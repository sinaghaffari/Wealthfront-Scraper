name := "WFScraper"

version := "0.1"

scalaVersion := "2.13.3"

libraryDependencies += "com.typesafe.play" %% "play-ahc-ws-standalone" % "2.1.2"
libraryDependencies += "com.typesafe.play" %% "play-ws-standalone-json" % "2.1.2"
libraryDependencies += "com.typesafe.play" %% "play-json" % "2.9.0"

libraryDependencies += "org.julienrf" %% "play-json-derived-codecs" % "7.0.0"
libraryDependencies += "com.j256.two-factor-auth" % "two-factor-auth" % "1.0"

libraryDependencies += "org.scalaz" %% "scalaz-core" % "7.3.2"
libraryDependencies += "com.lihaoyi" %% "pprint" % "0.6.0"
