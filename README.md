# scout

[![Continuous Integration](https://github.com/RustedBones/scout/workflows/Continuous%20Integration/badge.svg?branch=master)](https://github.com/RustedBones/scout/actions?query=branch%3Amaster+workflow%3A"Continuous+Integration")
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/fr.davit/scout_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/fr.davit/scout_2.13)
[![Software License](https://img.shields.io/badge/license-Apache%202-brightgreen.svg?style=flat)](LICENSE)

Zeroconf  for scala (multicast DNS service discovery)

## Versions

Work in progress...

## Getting scout

```sbt
libraryDependencies += "fr.davit" %% "scout" % "<version>"
```

## Zeroconf

Scanning for services

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

val services = Zeroconf
  .scan[IO](Zeroconf.Service("googlecast", "tcp"))
  .interruptAfter(50.seconds)
  .compile
  .toList
  .unsafeRunSync()
```