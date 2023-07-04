import org.scalafmt.sbt.ScalafmtPlugin

import scala.annotation.nowarn

// General info
val username = "RustedBones"
val repo     = "scout"

// for sbt-github-actions
ThisBuild / scalaVersion := "3.2.1"
ThisBuild / githubWorkflowBuild := Seq(
  WorkflowStep.Sbt(name = Some("Check project"), commands = List("scalafmtCheckAll", "headerCheckAll")),
  WorkflowStep.Sbt(name = Some("Build project"), commands = List("test", "IntegrationTest/test"))
)
ThisBuild / githubWorkflowTargetBranches        := Seq("main")
ThisBuild / githubWorkflowPublishTargetBranches := Seq.empty

lazy val commonSettings = Defaults.itSettings ++
  headerSettings(Configurations.IntegrationTest) ++
  inConfig(IntegrationTest)(ScalafmtPlugin.scalafmtConfigSettings) ++
  Seq(
    organization     := "fr.davit",
    organizationName := "Michel Davit",
    scalaVersion     := (ThisBuild / scalaVersion).value,
    homepage         := Some(url(s"https://github.com/$username/$repo")),
    licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt")),
    startYear := Some(2020),
    scmInfo   := Some(ScmInfo(url(s"https://github.com/$username/$repo"), s"git@github.com:$username/$repo.git")),
    developers := List(
      Developer(
        id = s"$username",
        name = "Michel Davit",
        email = "michel@davit.fr",
        url = url(s"https://github.com/$username")
      )
    ),
    publishMavenStyle      := true,
    Test / publishArtifact := false,
    publishTo := {
      val resolver =
        if isSnapshot.value then Opts.resolver.sonatypeSnapshots: @nowarn("cat=deprecation")
        else Opts.resolver.sonatypeStaging
      Some(resolver)
    },
    releasePublishArtifactsAction := PgpKeys.publishSigned.value,
    credentials ++= (for
      username <- sys.env.get("SONATYPE_USERNAME")
      password <- sys.env.get("SONATYPE_PASSWORD")
    yield Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", username, password)).toSeq,
    testFrameworks += new TestFramework("munit.Framework")
  )

lazy val `scout` = (project in file("."))
  .configs(IntegrationTest)
  .settings(commonSettings*)
  .settings(
    libraryDependencies ++= Seq(
      Dependencies.Taxonomy,
      Dependencies.Test.MUnitCE3
    )
  )
