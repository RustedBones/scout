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
