package com.agilelab.RTClassification

import akka.actor._
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import akka.event.Logging
import akka.routing.{FromConfig, RoundRobinPool}
import com.agilelab.RTClassification.classification.TransactionClassifier
import com.agilelab.RTClassification.ingestion.IngestionCheckpointActor
import com.agilelab.RTClassification.io._
import com.agilelab.RTClassification.modules.SimpleSparkModelBuilder
import com.agilelab.RTClassification.utils.{RTClassificationConfig, SparkContextBuilderWithConfig}

import scala.collection.JavaConverters._


object RTClassificationBoot extends RTClassificationConfig {

  def boot()(clusterSystem: ActorSystem, ingestionNode: Boolean): Option[ActorRef] = {
    val log = Logging(clusterSystem, this.getClass)

    val featuresBuilder: FeaturesBuilder = new SimpleFeaturesBuilder(numFeatures)
    val dataReaderMock = new SimpleDataModelReader(trainingDataFilePath, featuresBuilder)
    val sparkModelBuilder = new SimpleSparkModelBuilder()

    val clusterCassandra = com.datastax.driver.core.Cluster.builder()
      .addContactPointsWithPorts(cassandraContactPoint.asJavaCollection).build()

    TransactionWriterCassandra.initialize(clusterCassandra)
    val cassandraActor: ActorRef = clusterSystem.actorOf(
      FromConfig.props(Props(new TransactionWriterCassandra(clusterCassandra))),
      "router-transactionWriter")

    log.info("End start cassandra writer")


    /*fake writer*/
    //val fakeWriter = clusterSystem.actorOf(Props(new FakeWriter()))

    ClusterSharding(clusterSystem).start(
      typeName = TransactionClassifier.shardName,
      //injection of cassandra writer actor
      entityProps = TransactionClassifier.props(cassandraActor, featuresBuilder),
      settings = ClusterShardingSettings(clusterSystem),
      extractEntityId = TransactionClassifier.idExtractor,
      extractShardId = TransactionClassifier.shardResolver
    )
    log.info("End start ClusterSharding")

    clusterSystem.actorOf(ClusterSingletonManager.props(
      singletonProps = Props(new SparkModelBuilderActor(dataReaderMock, sparkModelBuilder) with SparkContextBuilderWithConfig),
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(clusterSystem)),
      name = SparkModelBuilderActor.actorName)

    if (ingestionNode) {
      val trxClassifier: ActorRef = ClusterSharding(clusterSystem).shardRegion(TransactionClassifier.shardName)

      Some(
        clusterSystem.actorOf(IngestionCheckpointActor.props(trxClassifier, snapshotSize), "ingestion-1")
      )
    } else {
      None
    }


  }
}
