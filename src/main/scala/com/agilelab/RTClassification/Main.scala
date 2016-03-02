package com.agilelab.RTClassification

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory


/**
  * Start an akka cluster node
  * Usage:  sbt 'runMain com.agilelab.RTClassification.Main 127.0.0.1 2551 RTClassificationSystem master'
  * or sbt 'runMain com.agilelab.RTClassification.Main 127.0.0.1 2551 RTClassificationSystem slave'
  */
object Main extends App {
  val nettyConf: String =
    """akka.remote.netty.tcp.hostname="%hostname%"
      |akka.remote.netty.tcp.port=%port%
    """.stripMargin
  val originalConfig = ConfigFactory.load()

  val config = if (args.length >= 2) {
    val hostname = args(0)
    val port = args(1).toInt
    ConfigFactory.parseString(nettyConf.replaceAll("%hostname%", hostname)
      .replaceAll("%port%", port.toString)).withFallback(ConfigFactory.load())
  } else {
    originalConfig
  }

  val clusterSystemName =
    if (args.length >= 3) {
      args(2)
    } else {
      "RTClassificationSystem"
    }
  val isMaster =
    if (args.length >= 4) {
      if (args(3) == "master") {
        true
      } else {
        false
      }
    } else {
      false
    }


  // Create an Akka system
  implicit val clusterSystem = ActorSystem(clusterSystemName, config)
  val ingestionActorOpt = RTClassificationBoot.boot()(clusterSystem, isMaster)
  ingestionActorOpt.foreach(TransactionGenerator.generate(clusterSystem, _))
}
