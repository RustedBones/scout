# scout

[![Continuous Integration](https://github.com/RustedBones/scout/workflows/Continuous%20Integration/badge.svg?branch=master)](https://github.com/RustedBones/scout/actions?query=branch%3Amaster+workflow%3A"Continuous+Integration")
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/fr.davit/scout_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/fr.davit/scout_2.13)
[![Software License](https://img.shields.io/badge/license-Apache%202-brightgreen.svg?style=flat)](LICENSE)

Zeroconf  for scala (multicast DNS service discovery)

## Versions

| Version | Release date | cats version | Scala versions      |
| ------- | ------------ | -----------  | ------------------- |
| `0.1.0` | 2021-01-13   | `2.2.0`      | `2.13.4`, `2.12.12` |

## Getting scout

```sbt
libraryDependencies += "fr.davit" %% "scout" % "<version>"
```

## Zeroconf

```scala
import cats.effect.{ContextShift, IO, Timer}
import fr.davit.scout.Zeroconf
import fr.davit.taxonomy.model.DnsMessage
import fr.davit.taxonomy.scodec.DnsCodec
import scodec.Codec

import java.net.InetAddress
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
implicit val timer: Timer[IO]               = IO.timer(ExecutionContext.global)

// service definition
val service = Zeroconf.Service("ipp", "tcp")

// Scanning for service instances
val instances = Zeroconf
  .scan[IO](service)
  .interruptAfter(50.seconds)
  .compile
  .toList
  .unsafeRunSync()


// instance definition
val instance = Zeroconf.Instance(
  service = service,
  name = "Ed’s Party Mix",
  port = 1010,
  target = "eds-musicbox", 
  information = Map("codec" -> "ogg"),
  addresses = Seq(InetAddress.getByName("169.254.150.84")) // use local address when left empty
)

// Registering an instance
Zeroconf.register[IO](instance)
```