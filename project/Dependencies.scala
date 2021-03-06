package im.ghasedak

import sbt._

object Dependencies {

  object V {
    val akka = "2.5.21"
    val slick = "3.2.1"
    val slickPg = "0.16.0"
    val postgres = "42.2.1"
    val flyway = "5.0.7"
    val config = "1.3.2"
    val pulsar4s = "2.2.0"
    val scalapb = "0.8.2"
  }

  object Compile {
    val actor = "com.typesafe.akka" %% "akka-actor" % V.akka
    val cluster = "com.typesafe.akka" %% "akka-cluster" % V.akka
    val sharding = "com.typesafe.akka" %% "akka-cluster-sharding" % V.akka

    //akka typed
    val actorTyped = "com.typesafe.akka" %% "akka-actor-typed" % V.akka
    val clusterTyped = "com.typesafe.akka" %% "akka-cluster-typed" % V.akka
    val shardingTyped = "com.typesafe.akka" %% "akka-cluster-sharding-typed" % V.akka

    val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % V.akka
    val stream = "com.typesafe.akka" %% "akka-stream" % V.akka

    val config = "com.typesafe" % "config" % V.config

    val postgres = "org.postgresql" % "postgresql" % V.postgres
    val slick = "com.typesafe.slick" %% "slick" % V.slick
    val slickPg = "com.github.tminglei" %% "slick-pg" % V.slickPg
    val hikariCp = "com.typesafe.slick" %% "slick-hikaricp" % V.slick
    val flyway = "org.flywaydb" % "flyway-core" % V.flyway

    val scalap = "com.thesamet.scalapb" %% "compilerplugin" % scalapb.compiler.Version.scalapbVersion
    val scalapbRuntime = "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf" exclude("io.grpc", "grpc-netty")

    val logback = "ch.qos.logback" % "logback-classic" % "1.2.3"

    val jwt = "com.auth0" % "java-jwt" % "3.4.1"

    val libPhoneNumber = "com.googlecode.libphonenumber" % "libphonenumber" % "7.0.+"
    val cats = "org.typelevel" %% "cats-core" % "1.5.0"

    val caffeine = "com.github.ben-manes.caffeine" % "caffeine" % "2.6.2"

    val pulsar4s = Seq(
      "com.sksamuel.pulsar4s" %% "pulsar4s-core" % V.pulsar4s,
      "com.sksamuel.pulsar4s" %% "pulsar4s-akka-streams" % V.pulsar4s
    )
  }

  object Test {
    val scalatic = "org.scalactic" %% "scalactic" % "3.0.5"
    val scalaTest = "org.scalatest" %% "scalatest" % "3.0.5" % "test"
    val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % V.akka % "test"
    val akkaStreamTestkit ="com.typesafe.akka" %% "akka-stream-testkit" %  V.akka % "test"
  }

  import Compile._
  import Test._

  val shared = Seq(
    scalapbRuntime,
    logback
  )

  val sdk: Seq[ModuleID] = shared

  val model: Seq[ModuleID] = shared ++ Seq(
    config
  )

  val core: Seq[ModuleID] = shared ++ Seq(
    actor,
    cluster,
    sharding,
    actorTyped,
    clusterTyped,
    shardingTyped,
    akkaSlf4j,
    caffeine,
    stream
//    ,scalap
  )++ pulsar4s

  val rpc: Seq[ModuleID] = shared ++ Seq(
    jwt,
    stream
  )

  val persist: Seq[ModuleID] = shared ++ Seq(
    actor,
    slick,
    postgres,
    hikariCp,
    flyway,
    slickPg
  )

  val commons: Seq[ModuleID] = shared ++ Seq(
    cats,
    slick,
    config,
    libPhoneNumber
  )

  val test: Seq[ModuleID] = shared ++ Seq(
    scalatic,
    scalaTest,
    akkaTestkit,
    akkaStreamTestkit,
    stream
  )

}