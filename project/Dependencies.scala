import sbt.*

object Dependencies:

  object Versions:
    val MUnitCE3 = "1.0.7"
    val Taxonomy = "1.2.1"

  val Taxonomy = "fr.davit" %% "taxonomy-fs2" % Versions.Taxonomy

  object Test:
    val MUnitCE3 = "org.typelevel" %% "munit-cats-effect-3" % Versions.MUnitCE3 % "it,test"
