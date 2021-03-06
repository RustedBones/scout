/*
 * Copyright 2020 Michel Davit
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.davit.scout

import cats.Show
import cats.effect._
import cats.implicits._
import fr.davit.taxonomy.fs2.Dns
import fr.davit.taxonomy.model._
import fr.davit.taxonomy.model.record._
import fr.davit.taxonomy.scodec.DnsCodec
import fs2._
import fs2.io.udp.{Socket, SocketGroup}
import scodec.Codec

import java.net._
import scala.concurrent.duration._
import scala.collection.immutable
import scala.jdk.CollectionConverters._
import scala.collection.compat

object Zeroconf {

  /** Service to be discovered by DNS-SD
    * @param application Application type (eg tftp, printer)
    * @param transport Transport type (eq tcp, udp)
    * @param domain Domain where the service can be found. Defaults to 'local'
    */
  final case class Service(
      application: String,
      transport: String,
      domain: String = "local"
  )

  /** Instance of a service
    * @param service [[Service]] definition
    * @param name Instance name of the service, intended to be human readable
    * @param port Instance port for the service
    * @param target Instance host name
    * @param information Instance information
    * @param addresses Instance ip address
    */
  final case class Instance(
      service: Service,
      name: String,
      port: Int,
      target: String,
      information: Map[String, String] = Map.empty,
      addresses: immutable.Seq[InetAddress] = List.empty
  )

  private val LocalMulticastAddress = new InetSocketAddress(InetAddress.getByName("224.0.0.251"), 5353)

  private implicit val codec: Codec[DnsMessage]     = DnsCodec.dnsMessage
  private implicit val showService: Show[Service]   = Show(s => s"_${s.application}._${s.transport}.${s.domain}")
  private implicit val showInstance: Show[Instance] = Show(i => s"${i.name}.${i.service.show}")

  /** Finds the 'default' network interface on the machine
    * Will pick the 1st interface that supports broadcast
    * @return Default network interface
    */
  private[scout] def defaultNetworkInterface[F[_]: Sync](): Resource[F, NetworkInterface] = {
    val interface = Sync[F].delay {
      (for {
        itf  <- NetworkInterface.getNetworkInterfaces.asScala if itf.isUp && !itf.isLoopback
        addr <- itf.getInterfaceAddresses.asScala
        _    <- Option(addr.getBroadcast)
      } yield itf)
        .to(compat.immutable.LazyList)
        .headOption
        .getOrElse(throw new Exception("No network interface wit broadcast support was found"))
    }
    Resource.liftF(interface)
  }

  /** Creates the [[java.net.Socket]] resource bound on 224.0.0.251:5353
    * listening for multicast messages
    * @param interface Network interface. Will use the default NetworkInterface if not provided
    * @return Multicast socket
    */
  private[scout] def localMulticastSocket[F[_]: Concurrent: ContextShift](
      interface: Option[NetworkInterface] = None
  ): Resource[F, Socket[F]] = {
    for {
      itf <- interface match {
        case Some(i) => Resource.pure[F, NetworkInterface](i)
        case None    => defaultNetworkInterface[F]()
      }
      blocker     <- Blocker[F]
      socketGroup <- SocketGroup[F](blocker)
      socket <- socketGroup
        .open[F](
          new InetSocketAddress(LocalMulticastAddress.getPort),
          protocolFamily = Some(StandardProtocolFamily.INET),
          reuseAddress = true,
          allowBroadcast = true,
          multicastInterface = Some(itf),
          multicastTTL = Some(255)
        )
        .evalTap(_.join(LocalMulticastAddress.getAddress, itf).void)
    } yield socket
  }

  /** Periodically scans for [[Instance]] of the desired [[Service]].
    * @param service [[Service]] definition
    * @param interface Network interface. Will use the default NetworkInterface if not provided
    * @param nextDelay Applied to the previous delay to compute the next, e.g. to implement exponential backoff
    * @return Stream of [[Instance]]
    */
  def scan[F[_]: Concurrent: ContextShift: Timer](
      service: Service,
      interface: Option[NetworkInterface] = None,
      nextDelay: FiniteDuration => FiniteDuration = t => (t * 3).min(1.hour)
  ): Stream[F, Instance] = {
    val question = DnsQuestion(service.show, DnsRecordType.PTR, unicastResponse = false, DnsRecordClass.Internet)
    val message  = DnsMessage.query(id = 0, isRecursionDesired = false, questions = Seq(question))
    val packet   = DnsPacket(LocalMulticastAddress, message)

    def serviceInstance(dnsMessage: DnsMessage): Option[Instance] =
      for {
        ptr <- dnsMessage.answers.collectFirst {
          case DnsResourceRecord(question.name, _, question.`class`, _, DnsPTRRecordData(ptr)) => ptr
        }
        (port, target) <- dnsMessage.additionals.collectFirst {
          case DnsResourceRecord(`ptr`, _, question.`class`, _, DnsSRVRecordData(_, _, port, target)) => (port, target)
        }
        txt <- dnsMessage.additionals.collectFirst {
          case DnsResourceRecord(`ptr`, _, question.`class`, _, DnsTXTRecordData(txt)) => txt
        }
      } yield {
        val information = txt
          .map(_.split('='))
          .collect {
            case Array(key)        => key -> ""
            case Array(key, value) => key -> value
          }
          .toMap

        // address record is not required
        val addresses = dnsMessage.additionals.collect {
          case DnsResourceRecord(`target`, _, question.`class`, _, DnsARecordData(ipv4))    => ipv4
          case DnsResourceRecord(`target`, _, question.`class`, _, DnsAAAARecordData(ipv6)) => ipv6
        }
        Instance(service, ptr, port, target, information, addresses)
      }

    for {
      socket <- Stream
        .resource(localMulticastSocket[F](interface))
      exponentialDelay = Stream.emit(()) ++ Stream
        .iterate(1.second)(nextDelay)
        .flatMap(t => Stream.sleep_(t))
      requester = Stream
        .emit(packet)
        .repeat
        .zipLeft(exponentialDelay)
        .through(Dns.stream(socket))
      service <- Dns
        .listen(socket)
        .concurrently(requester)
        .map(_.message)
        .map(serviceInstance)
        .flattenOption
    } yield service
  }

  /** Register a [[Service]] [[Instance]] to be discovered with DNS-SD
    * @param instance [[Instance]] to be discovered
    * @param interface Network interface. Will use the default NetworkInterface if not provided
    * @param ttl Time to live of the DNS records
    */
  def register[F[_]: Concurrent: ContextShift](
      instance: Instance,
      interface: Option[NetworkInterface] = None,
      ttl: FiniteDuration = 2.minutes
  ): Stream[F, Unit] = {

    def isServiceRequest(message: DnsMessage): Boolean = {
      val isQuery = message.header.`type` == DnsType.Query
      val isServiceQuestion = message.questions.exists { q =>
        q.name == instance.service.show &&
        q.`type` == DnsRecordType.PTR &&
        q.`class` == DnsRecordClass.Internet
      }
      isQuery && isServiceQuestion
    }

    def knowsInstance(message: DnsMessage): Boolean = {
      message.answers.exists {
        case DnsResourceRecord(name, _, _, _, DnsPTRRecordData(ptr)) =>
          name == instance.service.show && ptr == instance.show
        case _ => false
      }
    }

    def serviceResponse(addresses: Seq[InetAddress]): DnsPacket = {
      val ptr = DnsResourceRecord(
        name = instance.service.show,
        cacheFlush = false,
        `class` = DnsRecordClass.Internet,
        ttl = ttl,
        data = DnsPTRRecordData(instance.show)
      )

      val srv = DnsResourceRecord(
        name = instance.show,
        cacheFlush = true,
        `class` = DnsRecordClass.Internet,
        ttl = ttl,
        data = DnsSRVRecordData(0, 0, instance.port, instance.target)
      )

      val txt = DnsResourceRecord(
        name = instance.show,
        cacheFlush = true,
        `class` = DnsRecordClass.Internet,
        ttl = ttl,
        data = DnsTXTRecordData(instance.information.map { case (k, v) => if (v.isEmpty) k else s"$k=$v" }.toList)
      )

      val as = addresses
        .map {
          case ipv4: Inet4Address => DnsARecordData(ipv4)
          case ipv6: Inet6Address => DnsAAAARecordData(ipv6)
        }
        .map { data =>
          DnsResourceRecord(
            name = instance.target,
            cacheFlush = true,
            `class` = DnsRecordClass.Internet,
            ttl = ttl,
            data = data
          )
        }

      val answers     = List(ptr)
      val additionals = List(srv, txt) ++ as
      val header = DnsHeader(
        id = 0,
        `type` = DnsType.Response,
        opCode = DnsOpCode.StandardQuery,
        isAuthoritativeAnswer = true,
        isTruncated = false,
        isRecursionDesired = false,
        isRecursionAvailable = false,
        responseCode = DnsResponseCode.Success
      )
      val message = DnsMessage(header, List.empty, answers, List.empty, additionals)
      DnsPacket(LocalMulticastAddress, message)
    }

    for {
      socket <- Stream
        .resource(localMulticastSocket[F](interface))
      addrs <-
        if (instance.addresses.isEmpty) {
          Stream.eval(socket.localAddress).map(a => List(a.getAddress))
        } else {
          Stream(instance.addresses)
        }
      response = serviceResponse(addrs)
      _ <- Dns
        .listen(socket)
        .map(_.message)
        .filter(isServiceRequest)
        .filterNot(knowsInstance)
        .map(_ => response)
        .through(Dns.stream(socket))
    } yield ()
  }
}
