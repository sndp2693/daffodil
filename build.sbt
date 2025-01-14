/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import sbtcc._

import scala.collection.immutable.ListSet

// Silence an errant sbt linter warning about unused sbt settings. For some
// reason, the sbt linter thinks the below settings are set but not used, which
// leads to a bunch of noisy warnings. But they clearly are used. Seems to be a
// bug in the linter where it cannot detect that some keys are used. The
// following is the sbt recommended way to silence these linter warnings on a
// per setting basis rather thand disabling the linter completely.
Global / excludeLintKeys ++= Set(
  EclipseKeys.classpathTransformerFactories,
)

lazy val genManaged = taskKey[Unit]("Generate managed sources and resources")
lazy val genProps = taskKey[Seq[File]]("Generate properties scala source")
lazy val genSchemas = taskKey[Seq[File]]("Generated DFDL schemas")

lazy val daffodil         = project.in(file(".")).configs(IntegrationTest)
                              .enablePlugins(JavaUnidocPlugin, ScalaUnidocPlugin)
                              .aggregate(macroLib, propgen, lib, io, runtime1, runtime1Unparser, runtime2, core, japi, sapi, tdmlLib, tdmlProc, cli, udf, schematron, test, testIBM1, tutorials, testStdLayout)
                              .settings(commonSettings, nopublish, ratSettings, unidocSettings)

lazy val macroLib         = Project("daffodil-macro-lib", file("daffodil-macro-lib")).configs(IntegrationTest)
                              .settings(commonSettings, nopublish)
                              .settings(libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value)

lazy val propgen          = Project("daffodil-propgen", file("daffodil-propgen")).configs(IntegrationTest)
                              .settings(commonSettings, nopublish)

lazy val lib              = Project("daffodil-lib", file("daffodil-lib")).configs(IntegrationTest)
                              .dependsOn(macroLib % "compile-internal, test-internal")
                              .settings(commonSettings, libManagedSettings, usesMacros)

lazy val io               = Project("daffodil-io", file("daffodil-io")).configs(IntegrationTest)
                              .dependsOn(lib, macroLib % "compile-internal, test-internal")
                              .settings(commonSettings, usesMacros)

lazy val runtime1         = Project("daffodil-runtime1", file("daffodil-runtime1")).configs(IntegrationTest)
                              .dependsOn(io, lib % "test->test", udf, macroLib % "compile-internal, test-internal")
                              .settings(commonSettings, usesMacros)

lazy val runtime1Unparser = Project("daffodil-runtime1-unparser", file("daffodil-runtime1-unparser")).configs(IntegrationTest)
                              .dependsOn(runtime1, lib % "test->test", runtime1 % "test->test")
                              .settings(commonSettings)

val runtime2CFiles        = Library("libruntime2.a")
lazy val runtime2         = Project("daffodil-runtime2", file("daffodil-runtime2")).configs(IntegrationTest)
                              .enablePlugins(CcPlugin)
                              .dependsOn(core, core % "test->test")
                              .settings(commonSettings)
                              .settings(
                                Compile / cCompiler := sys.env.getOrElse("CC", "cc"),
                                Compile / ccArchiveCommand := sys.env.getOrElse("AR", "ar"),
                                Compile / ccTargets := ListSet(runtime2CFiles),
                                Compile / cSources  := Map(
                                  runtime2CFiles -> (
                                    ((Compile / resourceDirectory).value / "org" / "apache" / "daffodil" / "runtime2" ** GlobFilter("*.c")).get()
                                  )
                                ),
                                Compile / cIncludeDirectories := Map(
                                  runtime2CFiles -> Seq(
                                    (Compile / resourceDirectory).value / "org" / "apache" / "daffodil" / "runtime2" / "c" / "libcli",
                                    (Compile / resourceDirectory).value / "org" / "apache" / "daffodil" / "runtime2" / "c" / "libruntime",
                                    (Compile / resourceDirectory).value / "org" / "apache" / "daffodil" / "runtime2" / "examples"
                                  )
                                ),
                                Compile / cFlags := (Compile / cFlags).value.withDefaultValue(Seq("-Wall", "-Wextra", "-pedantic", "-std=gnu99"))
                              )

lazy val core             = Project("daffodil-core", file("daffodil-core")).configs(IntegrationTest)
                              .dependsOn(runtime1Unparser, udf, lib % "test->test", runtime1 % "test->test", io % "test->test")
                              .settings(commonSettings)

lazy val japi             = Project("daffodil-japi", file("daffodil-japi")).configs(IntegrationTest)
                              .dependsOn(core)
                              .settings(commonSettings)

lazy val sapi             = Project("daffodil-sapi", file("daffodil-sapi")).configs(IntegrationTest)
                              .dependsOn(core)
                              .settings(commonSettings)

lazy val tdmlLib             = Project("daffodil-tdml-lib", file("daffodil-tdml-lib")).configs(IntegrationTest)
                              .dependsOn(macroLib % "compile-internal", lib, io, io % "test->test")
                              .settings(commonSettings)

lazy val tdmlProc         = Project("daffodil-tdml-processor", file("daffodil-tdml-processor")).configs(IntegrationTest)
                              .dependsOn(tdmlLib, runtime2, core)
                              .settings(commonSettings)

lazy val cli              = Project("daffodil-cli", file("daffodil-cli")).configs(IntegrationTest)
                              .dependsOn(tdmlProc, runtime2, sapi, japi, schematron % Runtime, udf % "it->test") // causes runtime2/sapi/japi to be pulled into the helper zip/tar
                              .settings(commonSettings, nopublish)
                              .settings(libraryDependencies ++= Dependencies.cli)

lazy val udf              = Project("daffodil-udf", file("daffodil-udf")).configs(IntegrationTest)
                              .settings(commonSettings)

lazy val schematron       = Project("daffodil-schematron", file("daffodil-schematron"))
                              .dependsOn(lib, sapi % Test)
                              .settings(commonSettings)
                              .settings(libraryDependencies ++= Dependencies.schematron)
                              .configs(IntegrationTest)

lazy val test             = Project("daffodil-test", file("daffodil-test")).configs(IntegrationTest)
                              .dependsOn(tdmlProc, runtime2 % "test->test", udf % "test->test")
                              .settings(commonSettings, nopublish)
                              //
                              // Uncomment the following line to run these tests 
                              // against IBM DFDL using the Cross Tester
                              //
                              //.settings(IBMDFDLCrossTesterPlugin.settings)

lazy val testIBM1         = Project("daffodil-test-ibm1", file("daffodil-test-ibm1")).configs(IntegrationTest)
                              .dependsOn(tdmlProc)
                              .settings(commonSettings, nopublish)
                              //
                              // Uncomment the following line to run these tests 
                              // against IBM DFDL using the Cross Tester
                              //
                              //.settings(IBMDFDLCrossTesterPlugin.settings)

lazy val tutorials        = Project("daffodil-tutorials", file("tutorials")).configs(IntegrationTest)
                              .dependsOn(tdmlProc)
                              .settings(commonSettings, nopublish)

lazy val testStdLayout    = Project("daffodil-test-stdLayout", file("test-stdLayout")).configs(IntegrationTest)
                              .dependsOn(tdmlProc)
                              .settings(commonSettings, nopublish)


lazy val commonSettings = Seq(
  organization := "org.apache.daffodil",
  version := "3.1.0-SNAPSHOT",
  scalaVersion := "2.12.13",
  crossScalaVersions := Seq("2.12.13"),
  scalacOptions ++= Seq(
    "-feature",
    "-deprecation",
    "-language:experimental.macros",
    "-unchecked",
    "-Xfatal-warnings",
    "-Xxml:-coalescing",
    "-Xfuture"
  ),
  // add scalac options that are version specific
  scalacOptions ++= scalacCrossOptions(scalaVersion.value),
  // Workaround issue that some options are valid for javac, not javadoc.
  // These javacOptions are for code compilation only. (Issue sbt/sbt#355)
  Compile / compile / javacOptions  ++= Seq(
    "-Werror",
    "-Xlint:deprecation"
  ),
  logBuffered := true,
  transitiveClassifiers := Seq("sources", "javadoc"),
  retrieveManaged := true,
  useCoursier := false, // disabled because it breaks retrieveManaged (sbt issue #5078)
  exportJars := true,
  Test / exportJars := false,
  publishMavenStyle := true,
  Test / publishArtifact := false,
  ThisBuild / pomIncludeRepository := { _ => false },
  scmInfo := Some(
    ScmInfo(
      browseUrl = url("https://github.com/apache/daffodil"),
      connection = "scm:git:https://github.com/apache/daffodil"
    )
  ),
  licenses := Seq("Apache License, Version 2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0")),
  homepage := Some(url("https://daffodil.apache.org")),
  unmanagedBase := baseDirectory.value / "lib" / "jars",
  sourceManaged := baseDirectory.value / "src_managed",
  resourceManaged := baseDirectory.value / "resource_managed",
  libraryDependencies ++= Dependencies.common,
  IntegrationTest / parallelExecution := false,
  testOptions += Tests.Argument(TestFrameworks.JUnit, "-q", "-v"),
) ++ Defaults.itSettings

def scalacCrossOptions(scalaVersion: String) =
  CrossVersion.partialVersion(scalaVersion) match {
    case Some((2, 12)) => Seq(
      "-Ywarn-unused:imports"
    )
    case _ => Seq.empty
  }

lazy val nopublish = Seq(
  publish := {},
  publishLocal := {},
  publishM2 := {},
  publish / skip := true
)

// "usesMacros" is a list of settings that should be applied only to
// subprojects that use the Daffodil macroLib subproject. In addition to using
// these settings, projects that use macroLib should add it as
// "compile-internal" and "test-internal" dependency, so that the macroLib jar
// does not need to be published. For example:
//
//   lazy val subProject = Project(...)
//                           .dependsOn(..., macroLib % "compile-internal, test-internal")
//                           .settings(commonSettings, usesMacros)
//
lazy val usesMacros = Seq(
  // Because the macroLib is an internal dependency to projects that use this
  // setting, the macroLib is not published. But that means we need to copy the
  // macro src/bin into projects that use it, essentially inlining macros into
  // the projects that use them. This is standard practice according to:
  //
  //   https://www.scala-sbt.org/1.x/docs/Macro-Projects.html#Distribution
  //
  // Note that for packageBin, we only copy directories and class files--this
  // ignores files such a META-INFA/LICENSE and NOTICE that are duplicated and
  // would otherwise cause a conflict.
  Compile / packageBin / mappings ++= (macroLib / Compile / packageBin / mappings).value.filter { case (f, _) => f.isDirectory || f.getPath.endsWith(".class") },
  Compile / packageSrc / mappings ++= (macroLib / Compile / packageSrc / mappings).value,

  // The .classpath files that the sbt eclipse plugin creates need minor
  // modifications. Fortunately, the plugin allows us to provide "transformers"
  // to make such modifications. Note that because this is part of the
  // "usesMacro" setting, the following transformations are only applied to
  // .classpath files in projects that use macros and add this setting.
  EclipseKeys.classpathTransformerFactories ++= Seq(
    // The macroLib project needs to be a "compile-internal" dependency to
    // projects that add this "usesMacros" setting. But the sbt eclipse plugin
    // only looks at "compile" dependencies when building .classpath files.
    // This means that eclipse projects that use macros don't have a dependency
    // to macroLib and so fail to compile. This transformation looks for
    // "classpath" nodes, and appends a new "classpathentry" node as a child
    // referencing the macroLib project. This causes Eclipse to treat macroLib
    // just like any other dependency to allow compilation to work.
    transformNode("classpath", DefaultTransforms.Append(EclipseClasspathEntry.Project(macroLib.base.toString))),
  ),
)

lazy val libManagedSettings = Seq(
  genManaged := {
    (Compile / genProps).value
    (Compile / genSchemas).value
    ()
  },
  Compile / genProps := {
    val cp = (propgen / Runtime / dependencyClasspath).value
    val inSrc = (propgen / Runtime/ sources).value
    val inRSrc = (propgen / Compile / resources).value
    val stream = (propgen / streams).value
    val outdir = (Compile / sourceManaged).value
    val filesToWatch = (inSrc ++ inRSrc).toSet
    val cachedFun = FileFunction.cached(stream.cacheDirectory / "propgen") { (in: Set[File]) =>
      val mainClass = "org.apache.daffodil.propGen.PropertyGenerator"
      val out = new java.io.ByteArrayOutputStream()
      val forkOpts = ForkOptions()
                       .withOutputStrategy(Some(CustomOutput(out)))
                       .withBootJars(cp.files.toVector)
      val ret = new Fork("java", Some(mainClass)).fork(forkOpts, Seq(outdir.toString)).exitValue()
      if (ret != 0) {
        sys.error("Failed to generate code")
      }
      val bis = new java.io.ByteArrayInputStream(out.toByteArray)
      val isr = new java.io.InputStreamReader(bis)
      val br = new java.io.BufferedReader(isr)
      val iterator = Iterator.continually(br.readLine()).takeWhile(_ != null)
      val files = iterator.map { f =>
        stream.log.info("generated %s".format(f))
        new File(f)
      }.toSet
      files
    }
    cachedFun(filesToWatch).toSeq
  },
  Compile / genSchemas := {
    val inRSrc = (propgen / Compile / resources).value
    val stream = (propgen / streams).value
    val outdir = (Compile / resourceManaged).value
    val filesToWatch = inRSrc.filter{_.isFile}.toSet
    val cachedFun = FileFunction.cached(stream.cacheDirectory / "schemasgen") { (schemas: Set[File]) =>
      schemas.map { schema =>
        val out = outdir / "org" / "apache" / "daffodil" / "xsd" / schema.getName
        IO.copyFile(schema, out)
        stream.log.info("generated %s".format(out))
        out
      }
    }
    cachedFun(filesToWatch).toSeq
  },
  Compile / sourceGenerators += (Compile / genProps).taskValue,
  Compile / resourceGenerators += (Compile / genSchemas).taskValue
)

lazy val ratSettings = Seq(
  ratLicenses := Seq(
    ("BSD2 ", Rat.BSD2_LICENSE_NAME, Rat.LICENSE_TEXT_PASSERA)
  ),
  ratLicenseFamilies := Seq(
    Rat.BSD2_LICENSE_NAME
  ),
  ratExcludes := Rat.excludes,
  ratFailBinaries := true,
)

lazy val unidocSettings = Seq(
  ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(sapi, udf),
  ScalaUnidoc / unidoc / scalacOptions := Seq(
    "-doc-title", "Apache Daffodil " + version.value + " Scala API",
    "-doc-root-content", (sapi / baseDirectory).value + "/root-doc.txt"
  ),

  JavaUnidoc / unidoc / unidocProjectFilter := inProjects(japi, udf),
  JavaUnidoc / unidoc / javacOptions:= Seq(
    "-windowtitle", "Apache Daffodil " + version.value + " Java API",
    "-doctitle", "<h1>Apache Daffodil " + version.value + " Java API</h1>",
    "-notimestamp",
    "-quiet",
  ),
  JavaUnidoc / unidoc / unidocAllSources := (JavaUnidoc / unidoc / unidocAllSources).value.map { sources =>
    sources.filterNot { source =>
      source.toString.contains("$") || source.toString.contains("packageprivate")
    }
  },
)
