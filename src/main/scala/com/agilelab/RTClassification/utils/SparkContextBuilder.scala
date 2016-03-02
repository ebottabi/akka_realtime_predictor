package com.agilelab.RTClassification.utils

import org.apache.spark.{SparkConf, SparkContext}

/**
  * Created by mattiabertorello on 11/02/16.
  */
trait SparkContextBuilder {
  def createSparkContext(): SparkContext
}

trait SparkContextBuilderWithConfig extends SparkContextBuilder {
  def createSparkContext(): SparkContext = {
    val conf = new SparkConf().setAppName("RTClassification").setMaster("local")
      .set("spark.driver.allowMultipleContexts", "true")
    val sc = new SparkContext(conf)
    sc
  }
}
