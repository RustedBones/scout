import sbt._

object Dependencies {

  object Versions {
    val Decline   = "1.3.0"
    val ScalaTest = "3.2.2"
    val Taxonomy  = "0.2.0"
  }

  val Decline  = "com.monovore" %% "decline-effect" % Versions.Decline
  val Taxonomy = "fr.davit"     %% "taxonomy-fs2"   % Versions.Taxonomy

  object Test {
    val ScalaTest = "org.scalatest" %% "scalatest" % Versions.ScalaTest % "test"
  }

}
