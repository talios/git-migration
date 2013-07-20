name := "git-migration"

scalaVersion := "2.10.2"

libraryDependencies ++= Seq(
  "com.jcraft" % "jsch" % "0.1.49",
  "org.eclipse.jgit" % "org.eclipse.jgit" % "3.0.0.201306101825-r",
  "com.typesafe.slick" %% "slick" % "1.0.1",
  "org.rogach" %% "scallop" % "0.9.3",
  "org.postgresql" % "postgresql" % "9.2-1003-jdbc4",
  "org.slf4j" % "slf4j-simple" % "1.7.5",
  "org.scalatest" %% "scalatest" % "1.9.1" % "test")

seq(com.github.retronym.SbtOneJar.oneJarSettings: _*)

mainClass in oneJar := Some("com.theoryinpractise.gitmigration.GitMigration")
