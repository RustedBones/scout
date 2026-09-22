import sbt.*

object Dependencies:

  object Versions:
    val MUnitCE3 = "2.2.1"
    val Taxonomy = "1.2.1"

  val Taxonomy = "fr.davit" %% "taxonomy-fs2" % Versions.Taxonomy

  object Test:
    val MUnitCE3 = "org.typelevel" %% "munit-cats-effect" % Versions.MUnitCE3 % "it,test"
