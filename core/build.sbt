name := "unsecurity-core"

scalacOptions := Seq(
  "-deprecation",
  "-language:higherKinds",
  "-Ywarn-value-discard"
)

scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
  case Some((2, v)) if v <= 12 => Seq("-Ypartial-unification")
  case _                       => Seq.empty
})

val http4sVersion     = "0.21.0-M5"
val circeVersion      = "0.12.3"
val directivesVersion = "0.14.0"
val shapelessVersion  = "2.3.3"
val scalaTestVersion  = "3.0.8"
val logbackVersion    = "1.2.3"

libraryDependencies := Seq(
  "io.circe"           %% "circe-parser"        % circeVersion,
  "org.http4s"         %% "http4s-blaze-client" % http4sVersion,
  "org.http4s"         %% "http4s-blaze-server" % http4sVersion,
  "org.http4s"         %% "http4s-circe"        % http4sVersion,
  "no.scalabin.http4s" %% "http4s-directives"   % directivesVersion,
  "com.chuusai"        %% "shapeless"           % shapelessVersion,
  "org.scalatest"      %% "scalatest"           % scalaTestVersion % Test,
  "ch.qos.logback"     % "logback-classic"      % logbackVersion % Test
)
