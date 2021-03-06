import sbt._
import Keys._
import scala.util.Properties

object BuildSettings {
  val buildVersion = "1.0.0-SNAPSHOT"
  val buildScalaVersion = "2.10.2-SNAPSHOT"
  val buildScalaOrganization = "org.scala-lang.macro-paradise"

  val useLocalBuildOfParadise = false
  // path to a build of https://github.com/scalamacros/kepler/tree/paradise/macros219
  val localBuildOfParadise210 = Properties.envOrElse("MACRO_PARADISE210", "/Users/xeno_by/Projects/Paradise210/build/pack")

  val buildSettings = Defaults.defaultSettings ++ Seq(
    version := buildVersion,
    scalaVersion := buildScalaVersion,
    scalaOrganization := buildScalaOrganization
  ) ++ (if (useLocalBuildOfParadise) Seq(
    scalaHome := Some(file(localBuildOfParadise210)),
    unmanagedBase := file(localBuildOfParadise210 + "/lib")
  ) else Nil) ++ Seq(
    resolvers += Resolver.sonatypeRepo("snapshots"),
    resolvers += Resolver.sonatypeRepo("releases"),
    scalacOptions ++= Seq("-feature")
  )
}

object MyBuild extends Build {
  import BuildSettings._

  // http://www.scala-sbt.org/release/docs/Extending/Input-Tasks
  def benchTask(benchClass: String, config: Traversable[Int]) = inputTask((args: TaskKey[Seq[String]]) =>
    (dependencyClasspath in Runtime in benchmark) map { (wrappedProjectCP) => {
      val projectCP = wrappedProjectCP.map(_.data).mkString(java.io.File.pathSeparatorChar.toString)
      val toolCP = projectCP // TODO: segregate compiler jars from the rest of dependencies
      val libraryCP = projectCP

      for (len <- config) {
        import scala.sys.process._
        var shellCommand = Seq(
          "java", "-Dsize=" + len, "-cp", toolCP,
          "-Xms1536M", "-Xmx4096M", "-Xss2M", "-XX:MaxPermSize=512M", "-XX:+UseParallelGC",
          "scala.tools.nsc.MainGenericRunner", "-cp", libraryCP,
          benchClass, "10")
        // println(shellCommand)
        shellCommand.!
      }
    }
  })

  lazy val core: Project = Project(
    "scala-pickling",
    file("core"),
    settings = buildSettings ++ (if (useLocalBuildOfParadise) Nil else Seq(
      libraryDependencies <+= (scalaVersion)(buildScalaOrganization % "scala-reflect" % _)
    )) ++ Seq(
      scalacOptions ++= Seq("-optimise"),
      libraryDependencies += "org.scalatest" %% "scalatest" % "1.9.1" % "test",
      libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.10.1" % "test",
      conflictWarning in ThisBuild := ConflictWarning.disable,
      parallelExecution in Test := false, // hello, reflection sync!!
      run <<= run in Compile in sandbox, // http://www.scala-sbt.org/release/docs/Detailed-Topics/Tasks
      InputKey[Unit]("travInt") <<= InputKey[Unit]("travInt") in Compile in benchmark,
      InputKey[Unit]("travIntFreeMem") <<= InputKey[Unit]("travIntFreeMem") in Compile in benchmark,
      InputKey[Unit]("travIntSize") <<= InputKey[Unit]("travIntSize") in Compile in benchmark,
      InputKey[Unit]("geoTrellis") <<= InputKey[Unit]("geoTrellis") in Compile in benchmark,
      InputKey[Unit]("evactor1") <<= InputKey[Unit]("evactor1") in Compile in benchmark,
      InputKey[Unit]("evactor2") <<= InputKey[Unit]("evactor2") in Compile in benchmark
    )
  )

  lazy val sandbox: Project = Project(
    "sandbox",
    file("sandbox"),
    settings = buildSettings ++ Seq(
      sourceDirectory in Compile <<= baseDirectory(root => root),
      sourceDirectory in Test <<= baseDirectory(root => root),
      libraryDependencies += "org.scalatest" %% "scalatest" % "1.9.1",
      parallelExecution in Test := false,
      scalacOptions ++= Seq()
      // scalacOptions ++= Seq("-Xprint:typer")
    )
  ) dependsOn(core)

  lazy val runtime: Project = Project(
    "runtime",
    file("runtime"),
    settings = buildSettings ++ (if (useLocalBuildOfParadise) Nil else Seq(
      libraryDependencies <+= (scalaVersion)(buildScalaOrganization % "scala-reflect" % _),
      libraryDependencies <+= (scalaVersion)(buildScalaOrganization % "scala-compiler" % _)
    ))
  ) dependsOn(core)

  lazy val benchmark: Project = Project(
    "benchmark",
    file("benchmark"),
    settings = buildSettings ++ Seq(
      sourceDirectory in Compile <<= baseDirectory(root => root),
      sourceDirectory in Test <<= baseDirectory(root => root),
      scalacOptions ++= Seq("-optimise"),
      InputKey[Unit]("travInt") <<= benchTask("TraversableIntBench", 100000 to 1000000 by 100000),
      InputKey[Unit]("travIntFreeMem") <<= benchTask("TraversableIntBenchFreeMem", 100000 to 1000000 by 100000),
      InputKey[Unit]("travIntSize") <<= benchTask("TraversableIntBenchSize", 100000 to 1000000 by 100000),
      InputKey[Unit]("geoTrellis") <<= benchTask("GeoTrellisBench", 100000 to 1000000 by 100000),
      InputKey[Unit]("evactor1") <<= benchTask("EvactorBench", 1000 to 10000 by 1000),
      InputKey[Unit]("evactor2") <<= benchTask("EvactorBench", 20000 to 40000 by 2000)
    )
  ) dependsOn(core, runtime)
}
