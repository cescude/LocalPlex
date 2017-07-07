scalaVersion := "2.12.2"

libraryDependencies ++= Seq(
  "com.twitter" %% "twitter-server" % "1.30.0",
  "com.twitter" %% "finagle-stats" % "6.45.0"
)


enablePlugins(JavaAppPackaging)
