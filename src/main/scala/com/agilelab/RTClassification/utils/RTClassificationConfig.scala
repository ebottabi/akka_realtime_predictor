package com.agilelab.RTClassification.utils

import java.net.InetSocketAddress

import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._

import scala.collection.JavaConversions._

trait RTClassificationConfig {

  private val conf = ConfigFactory.load()

  val cassandraContactPoint: Seq[InetSocketAddress] = conf
    .getStringList("cassandra-config.hosts")
    .map(_.split(":"))
    .map(hostWithPort => {
      val host = hostWithPort(0)
      val port = hostWithPort(1).toInt
      new InetSocketAddress(host, port)
    }).toSeq

  val numFeatures: Int = conf.getInt("spark-model.num-features")
  val trainingDataFilePath: String = conf.getString("spark-model.training-data-path")
  val ndgFilePath: String = conf.getString("spark-model.ndg-data-path")
  val intervalTransactions: FiniteDuration = Duration(
    conf.getLong("transactions-generator.interval"),
    conf.getString("transactions-generator.time-unit")
  )

  val snapshotSize = conf.getLong("akka.persistence.snapshot-size")
}
