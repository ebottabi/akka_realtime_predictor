package com.agilelab.RTClassification

import akka.actor._
import akka.cluster.Cluster
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata.{DistributedData, LWWRegister, Replicator}
import akka.testkit.{ImplicitSender, TestKit}
import com.agilelab.RTClassification.classification.{TransactionClassified, TransactionClassifier}
import com.agilelab.RTClassification.ingestion.{TransactionRaw, TrxEnvelope}
import com.agilelab.RTClassification.io.{FeaturesBuilder, TransactionWithSender}
import com.agilelab.RTClassification.modules.GeneralModel
import com.typesafe.config.ConfigFactory
import org.apache.spark.mllib.linalg.{Vectors, Vector}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.duration._

object TransactionClassifierSpec extends AkkaTestConfiguration {
  addConfig(ConfigFactory.parseString(
    """
    akka.loglevel = DEBUG
    """))
}

class TransactionClassifierSpec
  extends TestKit(ActorSystem("TransactionClassifierSpec", TransactionClassifierSpec.testConfig))
  with ImplicitSender
  with WordSpecLike
    with BeforeAndAfterAll
  with SparkTestContext {


  "Data classifier service" should {

    "change state when it gets a model" in {
      val writeAll = WriteAll(timeout = 10.seconds)
      implicit val node = Cluster(system)
      val replicator = DistributedData(system).replicator
      val transactionClassifier = system.actorOf(TransactionClassifier.props(self, new FeaturesBuilder {
        override def getVector(description: String): Vector = Vectors.dense(0.0, 1.1, 0.1)
      }), "transactionClassifier")
      val model: GeneralModel = ModelAndDataMock.buildTheMockModel(sc())
      val transactionRaw1 = TransactionRaw(id = "id1", senderNdg = "ndg1", receiverNdg = "ndg2", description = "paypal", amount = 10)
      val transactionRaw2 = TransactionRaw(id = "id2", senderNdg = "ndg2", receiverNdg = "ndg1", description = "amazon", amount = 5)
      transactionClassifier ! TrxEnvelope(1,
        transactionRaw1
      )
      expectNoMsg()

      replicator ! Replicator.Update(SparkModelBuilderActor.modelDataKey,
        LWWRegister(model), writeAll, request = Some(self))(x => LWWRegister(model))
      expectMsg(UpdateSuccess(SparkModelBuilderActor.modelDataKey, Some(self)))

      transactionClassifier ! TrxEnvelope(2,
        transactionRaw2
      )
      expectMsgType[TransactionWithSender].transaction == TransactionClassified(transactionRaw1, "0")
      expectMsgType[TransactionWithSender].transaction == TransactionClassified(transactionRaw2, "0")
    }

    "classify a transaction" in {
    }

    "change the internal model when the model change" in {

    }

  }
}
