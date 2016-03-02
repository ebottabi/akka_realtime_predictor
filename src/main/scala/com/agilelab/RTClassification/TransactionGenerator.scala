package com.agilelab.RTClassification

import java.io.{FileInputStream, BufferedInputStream}
import java.util.UUID
import java.util.zip.GZIPInputStream

import akka.actor.{ActorRef, ActorSystem}
import akka.cluster.Cluster
import akka.cluster.singleton.{ClusterSingletonProxySettings, ClusterSingletonProxy}
import com.agilelab.RTClassification.SparkModelBuilderActor.NewDataAvailable
import com.agilelab.RTClassification.ingestion.TransactionRaw
import com.agilelab.RTClassification.utils.RTClassificationConfig
import scala.concurrent.duration._

import scala.io.Source
import scala.util.Random


object TransactionGenerator extends RTClassificationConfig {

  def generate(clusterSystem: ActorSystem, ingestionActor: ActorRef) = {
    implicit val node = Cluster(clusterSystem)
    val proxy = clusterSystem.actorOf(ClusterSingletonProxy.props(s"user/${SparkModelBuilderActor.actorName}",
      settings = ClusterSingletonProxySettings(clusterSystem)), s"singletonProxy-${node.selfAddress.port.getOrElse(0)}")
    //Use the system's dispatcher as ExecutionContext
    import clusterSystem.dispatcher

    // Create a model
    clusterSystem.scheduler.schedule(0.second, 2.minutes) {
      proxy ! NewDataAvailable
    }

    val descriptions = Source.fromInputStream(gis(trainingDataFilePath)).getLines()
      .filter(_ != "").map(_.split(";")(1)).toSet.toVector

    val ndgs = Source.fromFile(ndgFilePath).getLines().filter(_.trim != "").toSet
    val rnd = new Random


    //Generate transactions and send them to the ingestion layer
    clusterSystem.scheduler.schedule(0.second, intervalTransactions) {
      val uuid = UUID.randomUUID()
      val sender: String = ndgs.toVector(rnd.nextInt(ndgs.size))
      val receiver = ndgs.-(sender).toVector(rnd.nextInt(ndgs.size - 1))
      val description = descriptions(rnd.nextInt(descriptions.size))
      ingestionActor ! TransactionRaw(uuid.toString, sender, receiver, description, rnd.nextInt(100000))
    }

  }

  private def gis(s: String) = new GZIPInputStream(new BufferedInputStream(new FileInputStream(s)))

}
