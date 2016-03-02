package com.agilelab.RTClassification.io

import org.apache.spark.SparkContext
import org.apache.spark.mllib.feature.HashingTF
import org.apache.spark.mllib.linalg.Vector
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.rdd.RDD


trait DataModelReader[T] {
  def readData(sc: SparkContext): RDD[T]
}

trait FeaturesBuilder {
  def getVector(description: String): Vector
}

class SimpleFeaturesBuilder(numFeatures: Int) extends FeaturesBuilder with Serializable {
  val hashingTF = new HashingTF(numFeatures)

  override def getVector(description: String): Vector = {
    val token: Array[String] = description.toLowerCase.split(" +")
    hashingTF.transform(token)
  }
}

class SimpleDataModelReader(trainingSetFilePath: String, featuresBuilder: FeaturesBuilder) extends DataModelReader[LabeledPoint] {
  override def readData(sc: SparkContext): RDD[LabeledPoint] = {

    val featuresBuilderLocal = featuresBuilder
    sc.textFile(trainingSetFilePath).sample(withReplacement = false, 0.7).map(_.split(";")).map(splitLine => {
      val rawFlagIsOnline = splitLine(0)
      val description = splitLine(1)
      val flagIsOnline = if (rawFlagIsOnline.toLowerCase.trim == "y") {
        1.0
      } else {
        0.0
      }

      LabeledPoint(flagIsOnline, featuresBuilderLocal.getVector(description))
    })
  }
}