name := "precog-challenge"

scalaVersion := "2.9.2"

libraryDependencies ++= Seq(
  "org.specs2"     %% "specs2"      % "1.12.2" % "test",
  "org.scalacheck" %% "scalacheck"  % "1.10.0" % "test",
  "junit"          %  "junit"       % "4.10"   % "test"
)