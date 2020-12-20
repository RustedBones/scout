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

import cats.effect.{ContextShift, IO, Timer}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.net.InetAddress
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class ZeroconfSpec extends AnyFlatSpec with Matchers {

  implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit val timer: Timer[IO]               = IO.timer(ExecutionContext.global)

  val googleCastService = Zeroconf.Service("googlecast", "tcp")

  val googleCastInstance = Zeroconf.Instance(
    googleCastService,
    "scala-cast-42",
    8009,
    InetAddress.getLocalHost.toString,
    Map.empty
  )

  "Zeroconf" should "get list of registered services" in {
    val services = Zeroconf
      .scan[IO](googleCastService)
      .interruptAfter(10.seconds)
      .compile
      .toList
      .unsafeRunSync()
    println(services)
  }

  it should "registered a new service" in {
    Zeroconf
      .register[IO](googleCastInstance)
      .interruptAfter(50.seconds)
      .compile
      .drain
      .unsafeRunSync()
  }
}
