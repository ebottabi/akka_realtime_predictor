import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

parallelExecution in Test := false

val baseSettings: Seq[Def.Setting[_]] =
  Seq(
    name := "real-time-data-classification-akka-spark",
    version := "0.1",
    scalaVersion := "2.11.7",
    organization := "AgileLab",
    ivyScala := ivyScala.value map {
      _.copy(overrideScalaVersion = true)
    },
    org.scalastyle.sbt.PluginKeys.config := file("project/scalastyle-config.xml"),
    scalacOptions in Compile ++= Seq("-encoding", "UTF-8", "-target:jvm-1.7", "-deprecation", "-unchecked", "-Ywarn-dead-code", "-Xfatal-warnings", "-feature", "-language:postfixOps"),
    scalacOptions in(Compile, doc) <++= (name in(Compile, doc), version in(Compile, doc)) map DefaultOptions.scaladoc,
    javacOptions in(Compile, compile) ++= Seq("-source", "1.7", "-target", "1.7", "-Xlint:unchecked", "-Xlint:deprecation", "-Xlint:-options"),
    javacOptions in doc := Seq(),
    javaOptions += "-Xmx2G",
    outputStrategy := Some(StdoutOutput),
    exportJars := true,
    fork := true,
    resolvers := ResolverSettings.resolvers,
    Keys.fork in run := true,
    // make sure that MultiJvm test are compiled by the default test compilation
    compile in MultiJvm <<= (compile in MultiJvm) triggeredBy (compile in Test),
    // disable parallel tests
    parallelExecution in Test := false,
    // make sure that MultiJvm tests are executed by the default test target,
    // and combine the results from ordinary test and multi-jvm tests
    executeTests in Test <<= (executeTests in Test, executeTests in MultiJvm) map {
      case (testResults, multiNodeResults) =>
        val overall =
          if (testResults.overall.id < multiNodeResults.overall.id)
            multiNodeResults.overall
          else
            testResults.overall
        Tests.Output(overall,
          testResults.events ++ multiNodeResults.events,
          testResults.summaries ++ multiNodeResults.summaries)
    }
  )


lazy val root = project.in(file("."))
  .settings(baseSettings ++ SbtMultiJvm.multiJvmSettings ++ Defaults.itSettings: _*)
  .settings(libraryDependencies ++= Dependencies.template)
  .configs(MultiJvm)

fork in run := true

fork in run := true