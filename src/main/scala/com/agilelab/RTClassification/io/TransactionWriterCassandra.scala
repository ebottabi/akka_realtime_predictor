package com.agilelab.RTClassification.io

import akka.actor.{ActorLogging, ActorRef, Actor}
import com.agilelab.RTClassification.classification.TransactionClassified
import com.agilelab.RTClassification.ingestion.TrxClassifiedFeedback
import com.datastax.driver.core.Cluster


object TransactionWriterCassandra {
  def initialize(cluster: Cluster) = {
    cluster.connect().execute("CREATE KEYSPACE IF NOT EXISTS rtclassification  WITH replication = {'class':'SimpleStrategy', 'replication_factor':1};")
    cluster.connect().execute("CREATE TABLE IF NOT EXISTS rtclassification.transactions (id varchar, sender_ndg varchar, receiver_ndg varchar, description varchar, amount varchar, classification_label varchar, primary key (id) );")

  }
}

class TransactionWriterCassandra(cluster: Cluster) extends TransactionWriter {
  val session = cluster.connect("rtclassification")
  val preparedStatement = session.prepare(
    """
      |INSERT INTO transactions(id, sender_ndg, receiver_ndg, description, amount, classification_label)
      |VALUES (?, ?, ?, ?, ?, ?);"""
      .stripMargin
  )

  override def saveTransaction(transactionClassified: TransactionClassified): Unit = {
    val trx = transactionClassified.trx
    session.execute(preparedStatement.bind(
      trx.id,
      trx.senderNdg,
      trx.receiverNdg,
      trx.description,
      trx.amount.toString,
      transactionClassified.label))
  }

}
