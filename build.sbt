resolvers ++= Seq(
  "snapshots" at "http://oss.sonatype.org/content/repositories/snapshots",
  "releases"  at "http://oss.sonatype.org/content/repositories/releases"
)

val scalacheck = "org.scalacheck" %% "scalacheck" % "1.13.2"

lazy val commonSettings = Seq(
  version := "0.1.0",
  scalaVersion in Global := "2.12.0-M5"
)

lazy val scads = (project in file("."))
  .settings(commonSettings: _*)
  .settings(
    name := "scads",
    libraryDependencies += scalacheck % Test
  )
