package com.agilelab.RTClassification.modules

import org.apache.spark.mllib.classification.NaiveBayes
import org.apache.spark.mllib.linalg.Vector
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.rdd.RDD


trait SparkModelBuilder[T] {
  def build(data: RDD[T]): GeneralModel
}


class SimpleSparkModelBuilder extends SparkModelBuilder[LabeledPoint] {
  override def build(training: RDD[LabeledPoint]): GeneralModel = {
    val model = NaiveBayes.train(training, lambda = 1.0, modelType = "multinomial")

    new GeneralModel {
      override def getPrediction(feature: Vector): String = {
        val prediction = model.predict(feature)
        println(s"prediction: $prediction")
        if (prediction == 1.0) {
          "online"
        } else {
          "offline"
        }
      }
    }
  }
}