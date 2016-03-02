package com.agilelab.RTClassification

import com.agilelab.RTClassification.io.DataModelReader
import com.agilelab.RTClassification.modules.{GeneralModel, SparkModelBuilder}
import org.apache.spark.SparkContext
import org.apache.spark.mllib.classification.SVMWithSGD
import org.apache.spark.mllib.feature.HashingTF
import org.apache.spark.mllib.linalg.{Vector, Vectors}
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.rdd.RDD


object ModelAndDataMock {
  // Fake data
  def dataReaderMock() = new DataModelReader[LabeledPoint] {
    override def readData(sc: SparkContext): RDD[LabeledPoint] = sc.parallelize(Seq(
      LabeledPoint(1.0, Vectors.dense(0.0, 1.1, 0.1)),
      LabeledPoint(0.0, Vectors.dense(2.0, 1.0, -1.0)),
      LabeledPoint(0.0, Vectors.dense(2.0, 1.3, 1.0)),
      LabeledPoint(1.0, Vectors.dense(0.0, 1.2, -0.5))
    ))
  }

  // Fake model builder
  def sparkModelBuilder() = new SparkModelBuilder[LabeledPoint] {
    override def build(trainingData: RDD[LabeledPoint]): GeneralModel = {
      val numIterations = 100
      val model = SVMWithSGD.train(trainingData.cache(), numIterations)

      new GeneralModel with Equals {
        val hashingTF = new HashingTF()

        override def getPrediction(data: Vector): String = {
          model.predict(data).toString
        }

        override def equals(that: Any): Boolean =
          that match {
            case generalModel: GeneralModel => this.hashCode == that.hashCode
            case _ => false
          }

        override def canEqual(that: Any): Boolean = equals(that)

        override def hashCode: Int = model.hashCode()
      }
    }
  }

  def buildTheMockModel(sc: SparkContext) = sparkModelBuilder().build(dataReaderMock().readData(sc))
}
