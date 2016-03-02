package com.agilelab.RTClassification

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.Cluster
import akka.cluster.ddata.Replicator.{UpdateResponse, WriteAll}
import akka.cluster.ddata._
import com.agilelab.RTClassification.SparkModelBuilderActor.{CompleteTraining, NewDataAvailable}
import com.agilelab.RTClassification.io.DataModelReader
import com.agilelab.RTClassification.modules.{SparkModelBuilder, GeneralModel}
import com.agilelab.RTClassification.utils.{SparkContextBuilder, SparkContextBuilderWithConfig, SparkContextHolder}
import org.apache.spark.ml.Transformer
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.rdd.RDD

import scala.concurrent.duration._


object SparkModelBuilderActor {
  def props[T](dataModelReader: DataModelReader[T],
               modelBuilder: SparkModelBuilder[T]): Props = {
    Props(new SparkModelBuilderActor(dataModelReader, modelBuilder) with SparkContextBuilderWithConfig)
  }

  case object NewDataAvailable

  case object End

  case object CompleteTraining

  case class TrainedModel(model: Transformer)

  val modelDataKey = LWWRegisterKey[GeneralModel]("model")

  val actorName = "modelBuilder"

}

/**
  * This is the builder of models. This actor receives only the NewDataAvailable message.
  *
  * That message run the build of a model from the training data and update the model in the DistributedData
  *
  * After the model updated in the DistributedData the actor response with CompleteTraining
  * at the original sender of the NewDataAvailable message
  *
  * @param dataModelReader the data used to create a model
  * @param modelBuilder    the model builder
  * @tparam T Type of the data used to create the model
  */
class SparkModelBuilderActor[T](dataModelReader: DataModelReader[T],
                                modelBuilder: SparkModelBuilder[T]) extends Actor
  with ActorLogging
  with SparkContextHolder {
  // https://github.com/akka/akka/issues/18983
  _self: SparkContextBuilder =>

  implicit val node = Cluster(context.system)
  val replicator = DistributedData(context.system).replicator
  val writeAll = WriteAll(timeout = 10.seconds)

  override def preStart(): Unit = {
    initSparkContext()
  }

  override def postStop(): Unit = {
    stopSparkContext()
  }

  override def receive: Receive = {
    case NewDataAvailable =>
      println("Model is going to create")
      val newTrainingData = dataModelReader.readData(sc())
      val newModel = modelBuilder.build(newTrainingData)
      log.info("Model created")

      val key = SparkModelBuilderActor.modelDataKey
      val newModelRegister = LWWRegister(newModel)
      replicator ! Replicator.Update(key, newModelRegister, writeAll, Some(sender()))(x => newModelRegister)
      log.info("Updated the DistributedData on all nodes")


    case u: UpdateResponse[_] =>
      println("Model updated on distributed data: " + u)
      u.request match {
        case Some(replyTo) => replyTo match {
          case replyToActor: ActorRef => replyToActor ! CompleteTraining
        }
        case None => log.info("Nobody to reply")
      }
      log.info("Model updated on distributed data: " + u)

    case a => log.info("Message not manged: " + a.toString)
  }

}
