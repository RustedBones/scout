import sbt._

object Dependencies {

  object Versions {
    val MUnitCE3              = "1.0.2"
    val ScalaCollectionCompat = "2.4.4"
    val Taxonomy              = "1.0.0"
  }

  val ScalaCollectionCompat = "org.scala-lang.modules" %% "scala-collection-compat" % Versions.ScalaCollectionCompat
  val Taxonomy              = "fr.davit"               %% "taxonomy-fs2"            % Versions.Taxonomy

  object Test {
    val MUnitCE3 = "org.typelevel" %% "munit-cats-effect-3" % Versions.MUnitCE3 % "it,test"
  }

}
