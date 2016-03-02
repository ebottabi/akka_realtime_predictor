package com.agilelab.RTClassification.modules

import com.agilelab.RTClassification.ingestion.TransactionRaw
import org.apache.spark.mllib.linalg.Vector


trait GeneralModel extends Serializable {
  def getPrediction(feature: Vector): String
}
