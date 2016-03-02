package com.agilelab.RTClassification

import com.agilelab.RTClassification.utils.{SparkContextBuilder, SparkContextHolder}
import org.apache.spark.{SparkConf, SparkContext}
import org.scalatest.BeforeAndAfterAll

trait SparkContextTestBuilder extends SparkContextBuilder {
  def createSparkContext(): SparkContext = {
    println("createSparkContext")
    val conf = new SparkConf().setAppName("test").setMaster("local")
      .set("spark.driver.allowMultipleContexts", "true")
    val sc = new SparkContext(conf)
    sc
  }
}

trait SparkTestContext extends SparkContextHolder with SparkContextTestBuilder {
  self: BeforeAndAfterAll =>

  override protected def beforeAll() = {
    initSparkContext()
    println("Create sparkContext")
  }

  override protected def afterAll() = {
    stopSparkContext()
    println("Stop sparkContext")
  }

}