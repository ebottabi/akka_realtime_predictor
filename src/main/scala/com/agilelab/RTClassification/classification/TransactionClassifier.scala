package com.agilelab.RTClassification.classification

import akka.actor._
import akka.cluster.ddata.DistributedData
import akka.cluster.ddata.Replicator._
import akka.cluster.sharding.ShardRegion
import com.agilelab.RTClassification.SparkModelBuilderActor
import com.agilelab.RTClassification.ingestion._
import com.agilelab.RTClassification.io.{FeaturesBuilder, TransactionWithSender}
import com.agilelab.RTClassification.modules.GeneralModel

import scala.concurrent.duration._

object TransactionClassifier {

  def props(writer: ActorRef, featuresBuilder: FeaturesBuilder): Props =
    Props(new TransactionClassifier(writer, featuresBuilder = featuresBuilder))

  val shardName: String = "TransactionClassifier"

  //in a production environment you have to size shardsNumber according with your workload and your number of nodes
  val shardsNumber = 100

  val idExtractor: ShardRegion.ExtractEntityId = {
    case trx: TrxEnvelope =>
      (trx.trx.senderNdg, trx)
  }

  val shardResolver: ShardRegion.ExtractShardId = {
    case trx: TrxEnvelope => (math.abs(trx.trx.senderNdg.hashCode) % shardsNumber).toString
  }
}

/**
  * The transaction classifier assigns a label to the received transaction and forwards it to the writer.
  */
class TransactionClassifier(writer: ActorRef, featuresBuilder: FeaturesBuilder) extends Actor with Stash with ActorLogging {

  val replicator = DistributedData(context.system).replicator
  val readMajority = ReadMajority(timeout = 5 seconds)

  val ModelKey = SparkModelBuilderActor.modelDataKey
  var model : Option[GeneralModel] = None

  override def preStart() = {
    super.preStart()
    init()
  }

  // Initialization function to request the classification model and subscribe to its changes.
  def init() = {
    replicator ! Get(ModelKey, readMajority)
    replicator ! Subscribe(ModelKey, self)
  }

  // Message handling in actor initialized state
  def initialized: Receive = {
    case trxEnvelope: TrxEnvelope =>
      val classifiedTrx = classify(trxEnvelope.trx)
      log.info("sending to the writer")
      writer ! TransactionWithSender(sender, trxEnvelope.deliveryId, classifiedTrx)
    case c @ Changed(ModelKey) =>
      log.info("The model changed, state: initialized")
      // no need to synchronize model, thanks to the actor pattern
      model = Some(c.get(ModelKey).value)
  }

  // Message handling in actor not initialized state
  def receive : Receive = {
    case trxEnvelope: TrxEnvelope =>
      log.info(s"Stashed this transaction: $trxEnvelope")
      stash()
    case g @ GetSuccess(ModelKey, req) =>
      model = Some(g.get(ModelKey).value)
      log.info("Received the new model in not initialized state")
      context.become(initialized)
      unstashAll()
    case GetFailure(ModelKey, req) =>
      log.error("The Get from DistributedData failed")
      //Retry to get model
      //an exit strategy must be considered according with your use case
      replicator ! Get(ModelKey, readMajority)
    case c @ Changed(ModelKey) =>
      log.info("The model changed, state: not initialized state")
      model = Some(c.get(ModelKey).value)
      context.become(initialized)
      //now we can re-play all buffered transactions
      unstashAll()
  }

  // Classify a transaction
  def classify(trx: TransactionRaw) : TransactionClassified = {
    val prediction =
      model.get.getPrediction(
        featuresBuilder.getVector(trx.description)
      )
    TransactionClassified(trx, prediction)
  }
}

case class TransactionClassified(trx: TransactionRaw, label: String)

