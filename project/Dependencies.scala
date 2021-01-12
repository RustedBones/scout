import sbt._

object Dependencies {

  object Versions {
    val Decline = "1.3.0"
    val MUnit   = "0.7.20"

    val Taxonomy = "0.3.0"
  }

  val Decline  = "com.monovore" %% "decline-effect" % Versions.Decline
  val Taxonomy = "fr.davit"     %% "taxonomy-fs2"   % Versions.Taxonomy

  object Test {
    val MUnit = "org.scalameta" %% "munit" % Versions.MUnit % "it,test"
  }

}
