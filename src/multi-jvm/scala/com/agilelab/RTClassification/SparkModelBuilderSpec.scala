package com.agilelab.RTClassification

import akka.actor.{PoisonPill, Props}
import akka.cluster.Cluster
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata.{DistributedData, LWWRegister, LWWRegisterKey}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.remote.testconductor.RoleName
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import akka.testkit.ImplicitSender
import com.agilelab.RTClassification.SparkModelBuilderActor.{CompleteTraining, NewDataAvailable}
import com.agilelab.RTClassification.modules.GeneralModel
import com.agilelab.RTClassification.utils.SparkContextHolder
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._

object SparkModelBuilderSpec extends MultiNodeConfig with AkkaTestConfiguration {
  val node1 = role("node1")
  val node2 = role("node2")

  commonConfig(testConfig)
}

class SparkModelBuilderSpecMultiJvmNode1 extends SparkModelBuilderSpec
class SparkModelBuilderSpecMultiJvmNode2 extends SparkModelBuilderSpec


class SparkModelBuilderSpec extends MultiNodeSpec(SparkModelBuilderSpec)
  with SparkContextHolder
  with SparkContextTestBuilder
  with STMultiNodeSpec
  with ImplicitSender {

  import SparkModelBuilderSpec._

  override def initialParticipants: Int = roles.size

  override protected def afterTermination() {
    runOn(node1, node2) {
      stopSparkContext()
    }
  }

  def startSingleton() = {
    val dataReaderMock = ModelAndDataMock.dataReaderMock()
    val sparkModelBuilder = ModelAndDataMock.sparkModelBuilder()

    system.actorOf(ClusterSingletonManager.props(
      singletonProps = Props(new SparkModelBuilderActor(dataReaderMock, sparkModelBuilder) with SparkContextTestBuilder),
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(system)),
      name = SparkModelBuilderActor.actorName)
  }

  def join(from: RoleName, to: RoleName): Unit = {
    runOn(from) {
      Cluster(system) join node(to).address
      startSingleton()
    }
    enterBarrier(from.name + "-joined")
  }


  "Spark model builder service" must {

    "join cluster" in within(15.seconds) {
      join(node1, node1)
      join(node2, node1)
      awaitAssert {
        DistributedData(system).replicator ! GetReplicaCount
        expectMsg(ReplicaCount(roles.size))
      }
      enterBarrier("joined")
    }

    "train and save a model into DistributedData" in within(40.seconds) {

      runOn(node2) {
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

      }
      enterBarrier("end")

    }
  }


}
