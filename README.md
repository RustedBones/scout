# scout

[![Continuous Integration](https://github.com/RustedBones/scout/actions/workflows/ci.yml/badge.svg)](https://github.com/RustedBones/scout/actions/workflows/ci.yml)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/fr.davit/scout_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/fr.davit/scout_2.13)
[![Software License](https://img.shields.io/badge/license-Apache%202-brightgreen.svg?style=flat)](LICENSE)
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)

Zeroconf for scala (multicast DNS service discovery)

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
import cats.effect.{IO, IOApp}
import fr.davit.scout.Zeroconf
import fr.davit.taxonomy.model.DnsMessage
import fr.davit.taxonomy.scodec.DnsCodec
import scodec.Codec

import java.net.InetAddress
import scala.concurrent.duration._

object App extends IOApp.Simple {

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
}
```