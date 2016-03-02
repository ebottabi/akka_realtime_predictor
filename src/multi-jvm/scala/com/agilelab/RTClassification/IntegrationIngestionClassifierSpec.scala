package com.agilelab.RTClassification

import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import akka.cluster.Cluster
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata.{DistributedData, LWWRegister, LWWRegisterKey}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.remote.testconductor.RoleName
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import akka.testkit.ImplicitSender
import com.agilelab.RTClassification.SparkModelBuilderActor.{CompleteTraining, NewDataAvailable}
import com.agilelab.RTClassification.classification.TransactionClassifier
import com.agilelab.RTClassification.ingestion.{IngestionCheckpointActor, TransactionRaw, TrxClassifiedFeedback}
import com.agilelab.RTClassification.io.{FeaturesBuilder, TransactionWithSender}
import com.agilelab.RTClassification.modules.GeneralModel
import com.agilelab.RTClassification.utils.SparkContextHolder
import com.typesafe.config.ConfigFactory
import org.apache.spark.mllib.linalg.{Vector, Vectors}

import scala.concurrent.duration._
import scala.util.Random


object IntegrationIngestionClassifierSpec extends MultiNodeConfig with AkkaTestConfiguration {
  val ingestion = role("ingestion")
  val worker1 = role("worker1")
  val worker2 = role("worker2")
  val worker3 = role("worker3")

  addConfig(ConfigFactory.parseString(
    """
      akka.loglevel = DEBUG
      akka.persistence.at-least-once-delivery.redeliver-interval = 1s
      akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
      akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
    """))
  commonConfig(testConfig)

}

class IntegrationIngestionClassifierSpecMultiJvmNode1 extends IntergrationIngestionClassifierSpec
class IntegrationIngestionClassifierSpecMultiJvmNode2 extends IntergrationIngestionClassifierSpec
class IntegrationIngestionClassifierSpecMultiJvmNode3 extends IntergrationIngestionClassifierSpec
class IntegrationIngestionClassifierSpecMultiJvmNode4 extends IntergrationIngestionClassifierSpec


class IntergrationIngestionClassifierSpec extends MultiNodeSpec(IntegrationIngestionClassifierSpec)
  with SparkContextHolder
  with SparkContextTestBuilder
  with STMultiNodeSpec
  with ImplicitSender {

  import IntegrationIngestionClassifierSpec._

  override def initialParticipants: Int = roles.size

  "A real time classifier" must {


    def startShardingAndSingleton() = {
      val dataReaderMock = ModelAndDataMock.dataReaderMock()
      val sparkModelBuilder = ModelAndDataMock.sparkModelBuilder()

      system.actorOf(ClusterSingletonManager.props(
        singletonProps = Props(new SparkModelBuilderActor(dataReaderMock, sparkModelBuilder) with SparkContextTestBuilder),
        terminationMessage = PoisonPill,
        settings = ClusterSingletonManagerSettings(system)),
        name = SparkModelBuilderActor.actorName)

      val writerActor = system.actorOf(Props(new Actor {
        override def receive: Receive = {
          case msg: TransactionWithSender =>
            log.debug("Transaction writed: " + msg)
            msg.sender.forward(TrxClassifiedFeedback(msg.deliveryId))
        }
      }), "actorWriter")
      ClusterSharding(system).start(
        typeName = TransactionClassifier.shardName,
        entityProps = TransactionClassifier.props(writerActor, new FeaturesBuilder {
          override def getVector(description: String): Vector = Vectors.dense(0.0, 1.1, 0.1)
        }),
        settings = ClusterShardingSettings(system),
        extractEntityId = TransactionClassifier.idExtractor,
        extractShardId = TransactionClassifier.shardResolver)

    }

    def join(from: RoleName, to: RoleName): Unit = {
      runOn(from) {
        Cluster(system) join node(to).address
        startShardingAndSingleton()
      }
      enterBarrier(from.name + "-joined")
    }

    "join cluster" in within(15.seconds) {
      join(ingestion, ingestion)
      join(worker1, ingestion)
      join(worker2, ingestion)
      join(worker3, ingestion)
      awaitAssert {
        DistributedData(system).replicator ! GetReplicaCount
        expectMsg(ReplicaCount(roles.size))
      }
      enterBarrier("joined")
    }

    "classify and save a transaction" in {


      runOn(ingestion) {
        implicit val node = Cluster(system)
        val replicator = DistributedData(system).replicator

        val proxy = system.actorOf(ClusterSingletonProxy.props(s"user/${SparkModelBuilderActor.actorName}",
          settings = ClusterSingletonProxySettings(system)), s"singletonProxy-${node.selfAddress.port.getOrElse(0)}")

        // Create a model
        proxy ! NewDataAvailable
        expectMsg(30.seconds, CompleteTraining)

        // Get model from distributed data
        val ModelFlagKey: LWWRegisterKey[GeneralModel] = SparkModelBuilderActor.modelDataKey
        replicator ! Get(ModelFlagKey, ReadLocal)
        expectMsgType[GetSuccess[LWWRegister[GeneralModel]]]

        val counterRegion: ActorRef = ClusterSharding(system).shardRegion(TransactionClassifier.shardName)

        val testTrx = TransactionRaw("1", "123", "321", "online transaction", 2.0d)

        val ingestionCheckpoint = system.actorOf(IngestionCheckpointActor.props(counterRegion, 100))
        ingestionCheckpoint ! testTrx
        expectNoMsg()

      }
      enterBarrier("end-test-1")

    }

    "classify a lot of transactions" in {


      runOn(ingestion) {
        implicit val node = Cluster(system)
        val replicator = DistributedData(system).replicator

        val proxy = system.actorSelection(s"user/singletonProxy-${node.selfAddress.port.getOrElse(0)}")

        // Create a model
        proxy ! NewDataAvailable
        expectMsg(30.seconds, CompleteTraining)

        // Get model from distributed data
        val ModelFlagKey: LWWRegisterKey[GeneralModel] = SparkModelBuilderActor.modelDataKey
        replicator ! Get(ModelFlagKey, ReadLocal)
        expectMsgType[GetSuccess[LWWRegister[GeneralModel]]]

        val counterRegion: ActorRef = ClusterSharding(system).shardRegion(TransactionClassifier.shardName)

        val writer = system.actorSelection(s"user/actorWriter")

        val ingestionCheckpoint = system.actorOf(IngestionCheckpointActor.props(counterRegion, 100))
        (1 to 1000).foreach(i => {
          val rand = new Random()
          val randomNdg = math.abs(rand.nextInt()) % 10
          ingestionCheckpoint ! TransactionRaw("1", randomNdg.toString, "321", "online transaction", 2.0d)
        })
        expectNoMsg(10.seconds)


      }
      enterBarrier("end-test-1")

    }
  }
}
