package com.agilelab.RTClassification.utils

import java.util.concurrent.atomic.AtomicBoolean

import org.apache.spark.SparkContext


trait SparkContextHolder {
  self: SparkContextBuilder =>

  @transient
  private var _sc: SparkContext = _
  val stopped: AtomicBoolean = new AtomicBoolean(true)

  def initSparkContext(): Unit = {
    stopSparkContext()
    this._sc = createSparkContext()
    stopped.set(false)
  }

  def setSparkContext(sc: SparkContext): Unit = {
    stopped.set(false)
    this._sc = sc
  }

  def sc(): SparkContext = _sc

  def stopSparkContext(): Unit = {
    if (!stopped.get() && _sc != null) {
      println("stop spark context")
      _sc.stop()
      stopped.set(true)
    }
  }

}