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

import cats.effect._
import cats.implicits._
import fr.davit.taxonomy.fs2.Dns
import fr.davit.taxonomy.model._
import fr.davit.taxonomy.model.record._
import fr.davit.taxonomy.scodec.DnsCodec
import fs2._
import fs2.io.udp.{Socket, SocketGroup}
import scodec.Codec

import java.net.{InetAddress, InetSocketAddress, NetworkInterface, StandardProtocolFamily}
import scala.concurrent.duration._

object Zeroconf {

  final case class Service(
      application: String,
      transport: String,
      domain: String = "local"
  ) {
    override def toString: String = s"_$application._$transport.$domain"
  }

  final case class Instance(
      service: Service,
      name: String,
      port: Int,
      target: String,
      information: Map[String, String]
  ) {
    override def toString: String = s"$name.$service"
  }

  private val LocalMulticastAddress             = new InetSocketAddress(InetAddress.getByName("224.0.0.251"), 5353)
  private implicit val codec: Codec[DnsMessage] = DnsCodec.dnsMessage

  private def localMulticastSocket[F[_]: Concurrent: ContextShift]: Resource[F, Socket[F]] = {
    val networkInterface = NetworkInterface.getByName("wlp0s20f3")
    for {
      blocker     <- Blocker[F]
      socketGroup <- SocketGroup[F](blocker)
      socket <- socketGroup
        .open[F](
          new InetSocketAddress(LocalMulticastAddress.getPort),
          protocolFamily = Some(StandardProtocolFamily.INET),
          reuseAddress = true,
          allowBroadcast = true,
          multicastInterface = Some(networkInterface),
          multicastTTL = Some(255)
        )
        .evalTap(_.join(LocalMulticastAddress.getAddress, networkInterface).void)
    } yield socket
  }

  def scan[F[_]: Concurrent: ContextShift: Timer](service: Service): Stream[F, Instance] = {
    val question = DnsQuestion(service.toString, DnsRecordType.PTR, unicastResponse = false, DnsRecordClass.Internet)
    val message  = DnsMessage.query(id = 0, isRecursionDesired = false, questions = Seq(question))
    val packet   = DnsPacket(LocalMulticastAddress, message)

    def serviceInstance(dnsMessage: DnsMessage): Option[Instance] = {
      for {
        ptr <- dnsMessage.answers.collectFirst {
          case DnsResourceRecord(question.name, _, question.`class`, _, ptr: DnsPTRRecordData) => ptr
        }
        srv <- dnsMessage.additionals.collectFirst {
          case DnsResourceRecord(ptr.ptrdname, _, question.`class`, _, srv: DnsSRVRecordData) => srv
        }
        txt <- dnsMessage.additionals.collectFirst {
          case DnsResourceRecord(ptr.ptrdname, _, question.`class`, _, txt: DnsTXTRecordData) => txt
        }
        information = txt.txt
          .map(_.split('='))
          .collect {
            case Array(key)        => key -> ""
            case Array(key, value) => key -> value
          }
          .toMap
      } yield Instance(service, ptr.ptrdname, srv.port, srv.target, information)
    }

    for {
      socket <- Stream
        .resource(localMulticastSocket[F])
      exponentialDelay = Stream.emit(()) ++ Stream
        .iterate(1.second)(t => (t * 3).min(1.hour))
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
        .evalTap(m => Sync[F].delay(println(m)))
        .map(serviceInstance)
        .flattenOption
    } yield service
  }

  def register[F[_]: Concurrent: ContextShift: Timer](instance: Instance): Stream[F, Unit] = {
    val header = DnsHeader(
      id = 0,
      `type` = DnsType.Response,
      opCode = DnsOpCode.StandardQuery,
      isAuthoritativeAnswer = false,
      isTruncated = false,
      isRecursionDesired = false,
      isRecursionAvailable = false,
      responseCode = DnsResponseCode.Success,
      countQuestions = 0,
      countAnswerRecords = 1,
      countAuthorityRecords = 0,
      countAdditionalRecords = 2
    )
    val ptr = DnsResourceRecord(
      name = instance.service.toString,
      cacheFlush = false,
      `class` = DnsRecordClass.Internet,
      ttl = 1.minute,
      data = DnsPTRRecordData(instance.name)
    )
    val srv = DnsResourceRecord(
      name = instance.toString,
      cacheFlush = false,
      `class` = DnsRecordClass.Internet,
      ttl = 1.minute,
      data = DnsSRVRecordData(0, 0, instance.port, instance.target)
    )
    val txt = DnsResourceRecord(
      name = instance.toString,
      cacheFlush = false,
      `class` = DnsRecordClass.Internet,
      ttl = 1.minute,
      data = DnsTXTRecordData(instance.information.map { case (k, v) => if (v.isEmpty) k else s"$k=$v" }.toList)
    )
    val message = DnsMessage(header, List.empty, List(ptr), List.empty, List(srv, txt))
    val packet  = DnsPacket(LocalMulticastAddress, message)
    for {
      socket <- Stream
        .resource(localMulticastSocket[F])
      _ <- Stream
        .fixedDelay(30.seconds)
        .map(_ => packet)
        .through(Dns.stream(socket))
    } yield ()
  }
}
