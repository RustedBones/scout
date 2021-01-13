// General info
val username = "RustedBones"
val repo     = "scout"

lazy val filterScalacOptions = { options: Seq[String] =>
  options.filterNot { o =>
    // get rid of value discard
    o == "-Ywarn-value-discard" || o == "-Wvalue-discard"
  }
}

// for sbt-github-actions
ThisBuild / crossScalaVersions := Seq("2.13.4", "2.12.12")
ThisBuild / githubWorkflowBuild := Seq(
  WorkflowStep.Sbt(name = Some("Check project"), commands = List("scalafmtCheckAll", "headerCheckAll")),
  WorkflowStep.Sbt(name = Some("Build project"), commands = List("test", "it:test"))
)
ThisBuild / githubWorkflowTargetBranches := Seq("master")
ThisBuild / githubWorkflowPublishTargetBranches := Seq.empty

lazy val commonSettings = Defaults.itSettings ++
  headerSettings(Configurations.IntegrationTest) ++
  Seq(
    organization := "fr.davit",
    organizationName := "Michel Davit",
    version := "0.1.0",
    crossScalaVersions := (ThisBuild / crossScalaVersions).value,
    scalaVersion := crossScalaVersions.value.head,
    scalacOptions ~= filterScalacOptions,
    homepage := Some(url(s"https://github.com/$username/$repo")),
    licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt")),
    startYear := Some(2020),
    scmInfo := Some(ScmInfo(url(s"https://github.com/$username/$repo"), s"git@github.com:$username/$repo.git")),
    developers := List(
      Developer(
        id = s"$username",
        name = "Michel Davit",
        email = "michel@davit.fr",
        url = url(s"https://github.com/$username")
      )
    ),
    publishMavenStyle := true,
    Test / publishArtifact := false,
    publishTo := Some(if (isSnapshot.value) Opts.resolver.sonatypeSnapshots else Opts.resolver.sonatypeStaging),
    credentials ++= (for {
      username <- sys.env.get("SONATYPE_USERNAME")
      password <- sys.env.get("SONATYPE_PASSWORD")
    } yield Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", username, password)).toSeq,
    testFrameworks += new TestFramework("munit.Framework")
  )

lazy val `scout` = (project in file("."))
  .configs(IntegrationTest)
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      Dependencies.ScalaCollectionCompat,
      Dependencies.Taxonomy,
      Dependencies.Test.MUnit
    )
  )
