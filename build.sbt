name := "vaspark"

version := "0.1"

val sparkVersion = "2.4.0"

scalaVersion := "2.11.12"

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % sparkVersion % "provided",
  "org.apache.spark" %% "spark-sql" % sparkVersion % "provided",
  "org.apache.spark" %% "spark-mllib" % sparkVersion % "provided",
  "org.apache.spark" %% "spark-streaming" % sparkVersion % "provided",
  "com.github.scopt" %% "scopt" % "4.0.0"
//  "org.scala-lang.modules" %% "scala-xml" % "1.1.1", // Scala XML library
//  "org.json4s" %% "json4s-native" % "3.6.1", // Scala Lift JSON Library
//  "org.apache.commons" % "commons-csv" % "1.6", // Apache Commons CSV Java Library
//  "org.vegas-viz" %% "vegas" % "0.3.11", // Vegas Visualization Library
//  "org.scala-saddle" %% "saddle-core" % "1.3.4", // Saddle Dataframe like Library
//  "org.scalatest" %% "scalatest" % "3.0.5" % "test" // Scala test library
)

// sbt-assembly 2.x uses ThisBuild scoped assemblyMergeStrategy
ThisBuild / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}

assemblyJarName := "vaspark-0.1.jar"
