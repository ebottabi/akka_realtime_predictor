package com.agilelab.RTClassification.io

import com.agilelab.RTClassification.classification.TransactionClassified

/**
  * Created by Paolo on 22/02/2016.
  */
class FakeWriter extends TransactionWriter{

  override def saveTransaction(transactionClassified: TransactionClassified) = {}

}
