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

import cats.effect.IO
import fr.davit.taxonomy.fs2.Dns
import fr.davit.taxonomy.model.*
import fr.davit.taxonomy.model.record.*
import fr.davit.taxonomy.scodec.DnsCodec
import fs2.*
import munit.CatsEffectSuite
import scodec.Codec

import java.net.{Inet4Address, Inet6Address, InetAddress, InetSocketAddress}
import scala.concurrent.duration.*

class ZeroconfItSpec extends CatsEffectSuite:

  given Codec[DnsMessage] = DnsCodec.dnsMessage

  val googleCastService = Zeroconf.Service("awesomeservice", "tcp")

  val ipv4 = InetAddress.getByName("1.2.3.4").asInstanceOf[Inet4Address]
  val ipv6 = InetAddress.getByName("[2001:db8::8a2e:370:7334]").asInstanceOf[Inet6Address]

  val googleCastInstance = Zeroconf.Instance(
    googleCastService,
    "Scala FTW",
    8009,
    "awesome-hostname",
    Map("key" -> "value"),
    List(ipv4, ipv6)
  )

  // pick a random interface for testing
  val testInterface = Zeroconf.networkInterfaces[IO]().head.compile.toList.unsafeRunSync().head

  test("discover services") {
    Stream
      .resource(Zeroconf.localMulticastSocket[IO](testInterface))
      .flatMap(Dns.listen[IO])
      .concurrently(Zeroconf.scan[IO](googleCastService))
      .delayBy(200.millis)
      .map(_.message)
      .head
      .compile
      .toList
      .map { result =>
        assert(result.nonEmpty)
        assert(result.head.header.`type` == DnsType.Query)
        assert(result.head.questions.head.name == "_awesomeservice._tcp.local")
        assert(result.head.questions.head.`type` == DnsRecordType.PTR)
      }
  }

  test("register a service instance") {
    val question = DnsQuestion("_awesomeservice._tcp.local", DnsRecordType.PTR, false, DnsRecordClass.Internet)
    val message  = DnsMessage.query(id = 0, isRecursionDesired = false, questions = Seq(question))
    val packet   = DnsPacket(new InetSocketAddress(InetAddress.getByName("224.0.0.251"), 5353), message)

    Stream
      .resource(Zeroconf.localMulticastSocket[IO](testInterface))
      // on multicast, resolve will receive its owns message
      .flatMap(s => Stream.eval(Dns.resolve(s, packet)).drain ++ Dns.listen(s))
      .delayBy(200.millis)
      .concurrently(Zeroconf.register[IO](googleCastInstance))
      .map(_.message)
      .head
      .compile
      .toList
      .map { result =>
        assert(result.nonEmpty)
        assert(result.head.header.`type` == DnsType.Response)
        assert(result.head.header.responseCode == DnsResponseCode.Success)
        assert(result.head.answers.head.name == "_awesomeservice._tcp.local")
        assert(result.head.answers.head.data == DnsPTRRecordData("Scala FTW._awesomeservice._tcp.local"))
        assert(result.head.additionals(0).name == "Scala FTW._awesomeservice._tcp.local")
        assert(result.head.additionals(0).data == DnsSRVRecordData(0, 0, 8009, "awesome-hostname"))
        assert(result.head.additionals(1).name == "Scala FTW._awesomeservice._tcp.local")
        assert(result.head.additionals(1).data == DnsTXTRecordData(List("key=value")))
        assert(result.head.additionals(2).name == "awesome-hostname")
        assert(result.head.additionals(2).data == DnsARecordData(ipv4))
        assert(result.head.additionals(3).name == "awesome-hostname")
        assert(result.head.additionals(3).data == DnsAAAARecordData(ipv6))
      }
  }
end ZeroconfItSpec
