package com.agilelab.RTClassification.modules

import org.apache.spark.{SparkConf, SparkContext}


object SparkUtil {

  lazy val sparkContext = new SparkContext(new SparkConf()
    .setMaster("local[*]")
    .setAppName("RT Classification")
  )

}
