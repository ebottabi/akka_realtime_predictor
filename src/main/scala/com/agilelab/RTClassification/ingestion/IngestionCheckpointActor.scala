package com.agilelab.RTClassification.ingestion

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.persistence.AtLeastOnceDelivery.AtLeastOnceDeliverySnapshot
import akka.persistence.{AtLeastOnceDelivery, PersistentActor}

case class TrxEnvelope(deliveryId: Long, trx: TransactionRaw)
case class TrxClassifiedFeedback (deliveryId: Long)

sealed trait TrxPersistedStatus
private case class TrxSent(trxSent: TransactionRaw) extends TrxPersistedStatus
private case class TrxConfirmed(deliveryId: Long) extends TrxPersistedStatus

object IngestionCheckpointActor {
  def props(destination: ActorRef, snapshotSize: Long, persistenceId: String = "ingestion-checkpoint-id"): Props = {
    Props(new IngestionCheckpointActor(destination,snapshotSize, persistenceId))
  }
}

/**
 * IngestionCheckpointActor serves as a checkpoint of received transactions to ensure the at-least-once delivery pattern.
 * The transactions are persisted and sent to the classifier; when the transaction will be delivered to the final datastore, this actor will receive an ack that will change the
 * persisted status of the transaction.
  *
  * @param destination path of the classifier
 */
class IngestionCheckpointActor(destination: ActorRef, snapshotSize: Long,
                               override val persistenceId: String = "ingestion-checkpoint-id")
  extends PersistentActor with AtLeastOnceDelivery with ActorLogging{

  // First landing point of received messages: new transaction or delivery confirmations.
  override def receiveCommand: Receive = {
    case trx: TransactionRaw => persist(TrxSent(trx))(updateState)
    case TrxClassifiedFeedback(deliveryId) => persist(TrxConfirmed(deliveryId))(updateState)
  }

  // Recovery handling of persisted status.
  override def receiveRecover: Receive = {
    case tps: TrxPersistedStatus =>
      log.info("receiveRecover: " + tps)
      updateState(tps)
  }

  // Transactions dispatcher.
  def updateState(tps: TrxPersistedStatus): Unit = tps match {
    case TrxSent(trx) =>
      log.info("updateState TrxSent: " + trx)
      deliver(destination.path)(deliveryId => TrxEnvelope(deliveryId, trx))

    case TrxConfirmed(deliveryId) =>
      log.info("updateState TrxConfirmed " + deliveryId)
      confirmDelivery(deliveryId)
      if(deliveryId%snapshotSize == 0) {
        log.info("saving a snapshot")
        saveSnapshot(getDeliverySnapshot)
      }
  }

}
