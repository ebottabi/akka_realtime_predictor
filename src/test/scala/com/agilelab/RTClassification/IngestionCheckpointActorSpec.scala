package com.agilelab.RTClassification

import akka.actor.ActorSystem
import akka.event.{LogSource, Logging}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.agilelab.RTClassification.ingestion.{IngestionCheckpointActor, TransactionRaw, TrxClassifiedFeedback, TrxEnvelope}
import com.typesafe.config.ConfigFactory
import org.scalatest.Matchers._
import org.scalatest.{WordSpecLike, _}

import scala.concurrent.duration._

object IngestionCheckpointActorSpec extends AkkaTestConfiguration {
  addConfig(ConfigFactory.parseString(
    """
      akka.persistence.at-least-once-delivery.redeliver-interval = 1s
      akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
      akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
      akka.persistence.snapshotSize = 100
    """))

  implicit val logSource: LogSource[AnyRef] = new LogSource[AnyRef] {
    def genString(o: AnyRef): String = o.getClass.getName

    override def getClazz(o: AnyRef): Class[_] = o.getClass
  }

}

class IngestionCheckpointActorSpec extends TestKit(ActorSystem("IngestionCheckpointActorSpec", IngestionCheckpointActorSpec.testConfig))
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll
  {

    import IngestionCheckpointActorSpec._
    val log = Logging(system, this)

    override protected def beforeAll() {
      log.debug("delete beforeEach")
  }

    override protected def afterAll() {
      log.debug("delete afterEach")
  }

    "Ingestion Checkpoint" must {

      "Persist incoming transactions and send them to the classifier" in {
        val testUtilityProbe = TestProbe()
        val testTrx = TransactionRaw("1", "123", "321", "online transaction", 2.0d)
        val ingestionCheckpoint = system.actorOf(IngestionCheckpointActor.props(testUtilityProbe.ref, 100, "ingestion-checkpoint-id-1"))

        ingestionCheckpoint ! testTrx

        val timeout = 5 seconds
        val trx1: TrxEnvelope = testUtilityProbe.expectMsgType[TrxEnvelope](timeout)
        trx1.trx shouldBe testTrx
        log.debug("Test 1: ingestionCheckpoint sent correctly the transaction")
        system.stop(ingestionCheckpoint)
      }

      "Persist the confirmations for transactions delivery not sending requests for these transactions anymore" in {
        val testUtilityProbe = TestProbe()
        val testTrx = TransactionRaw("1", "123", "321", "online transaction", 2.0d)
        val ingestionCheckpoint = system.actorOf(IngestionCheckpointActor.props(testUtilityProbe.ref, 100, "ingestion-checkpoint-id-2"))

        ingestionCheckpoint ! testTrx

        val timeout = 5 seconds
        val trx1: TrxEnvelope = testUtilityProbe.expectMsgType[TrxEnvelope](timeout)
        trx1.trx shouldBe testTrx

        ingestionCheckpoint ! TrxClassifiedFeedback(trx1.deliveryId)

        testUtilityProbe.expectNoMsg(timeout * 3)
        log.debug("Test 2: ingestionCheckpoint didn't resend the transaction due to the classification feedback")
        system.stop(ingestionCheckpoint)
      }

      "Resend transactions not confirmed in a timeout" in {
        val testUtilityProbe = TestProbe()
        val testTrx = TransactionRaw("1", "123", "321", "online transaction", 2.0d)
        val ingestionCheckpoint = system.actorOf(IngestionCheckpointActor.props(testUtilityProbe.ref, 100, "ingestion-checkpoint-id-3"))

        ingestionCheckpoint ! testTrx

        val timeout = 5.seconds
        val trx1: TrxEnvelope = testUtilityProbe.expectMsgType[TrxEnvelope](timeout)
        trx1.trx shouldBe testTrx
        log.debug("First request to the test probe sent")
        val trx2 = testUtilityProbe.expectMsgType[TrxEnvelope](timeout)
        trx2.trx shouldBe testTrx
        log.debug("Second request (first retry) to the test probe sent")
        val trx3 = testUtilityProbe.expectMsgType[TrxEnvelope](timeout)
        trx3.trx shouldBe testTrx
        log.debug("Third request (second retry) to the test probe sent")
        log.debug("Test 3: transaction resent after a timeout with no classification feedback")
        system.stop(ingestionCheckpoint)
      }

      "Resend a transaction to be classifed after a restart if no response received" in {
        val testUtilityProbe = TestProbe()
        val testTrx = TransactionRaw("1", "123", "321", "online transaction", 2.0d)
        val ingestionCheckpoint = system.actorOf(IngestionCheckpointActor.props(testUtilityProbe.ref, 100, "ingestion-checkpoint-id-4"))

        ingestionCheckpoint ! testTrx

        val timeout = 5.seconds
        val trx1: TrxEnvelope = testUtilityProbe.expectMsgType[TrxEnvelope](timeout)
        trx1.trx shouldBe testTrx
        system.stop(ingestionCheckpoint)

        system.actorOf(IngestionCheckpointActor.props(testUtilityProbe.ref, 100, "ingestion-checkpoint-id-4"))

        val trx2: TrxEnvelope = testUtilityProbe.expectMsgType[TrxEnvelope](timeout * 2)
        trx2.trx shouldBe testTrx
        trx1.deliveryId shouldBe trx2.deliveryId

        log.debug("Test 4: ingestionCheckpoint asks resend a unconfirmed transaction even if it was stopped and started again.")
        system.stop(ingestionCheckpoint)
      }
    }
}

