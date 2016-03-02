package com.agilelab.RTClassification.io

import akka.actor.{ActorRef, ActorLogging, Actor}
import com.agilelab.RTClassification.classification.TransactionClassified
import com.agilelab.RTClassification.ingestion.TrxClassifiedFeedback

case class TransactionWithSender(sender: ActorRef, deliveryId: Long, transaction: TransactionClassified)

/**
  * General purpose writer. This kind of actor have to persist transaction on a physical datastore.
  * After that it will confirm the delivery to the ingestion layer in order to accomplish with at least once delivery.
  */
trait TransactionWriter extends Actor with ActorLogging {

  def receive: Receive = {
    case msg: TransactionWithSender =>
      saveTransaction(msg.transaction)
      log.info(s"Saved Transaction: ${msg.transaction}")
      msg.sender ! TrxClassifiedFeedback(msg.deliveryId)
  }

  /**
    *Contains specific persistence semantic of each datastore
    * @param transactionClassified transaction that must be saved
    */
  def saveTransaction(transactionClassified: TransactionClassified): Unit
}
