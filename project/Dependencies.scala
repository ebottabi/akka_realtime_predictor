import sbt._

object Version {
  val akka = "2.4.1"
  val spark = "1.5.2"
  val scala = "2.11.7"
  val scalaTest = "2.2.1"
  val camelElastic = "2.16.2"
  val levelDb = "0.7"
  val levelDbJni = "1.8"
}

object Library {
  val akkaActor = "com.typesafe.akka" %% "akka-actor" % Version.akka
  val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % Version.akka
  val akkaPersistence = "com.typesafe.akka" %% "akka-persistence" % Version.akka
  val akkaCluster = "com.typesafe.akka" %% "akka-cluster" % Version.akka
  val akkaClusterSharding = "com.typesafe.akka" %% "akka-cluster-sharding" % Version.akka
  val akkaDistributedData = "com.typesafe.akka" %% "akka-distributed-data-experimental" % Version.akka
  val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % Version.akka
  val akkaMultiNodeTest = "com.typesafe.akka" %% "akka-multi-node-testkit" % Version.akka
  val akkaCamel = "com.typesafe.akka" %% "akka-camel" % Version.akka
  val sparkCore = "org.apache.spark" %% "spark-core" % Version.spark
  val sparkMllib = "org.apache.spark" %% "spark-mllib" % Version.spark
  val scalaTest = "org.scalatest" %% "scalatest" % Version.scalaTest
  val akkaPersistenceCassandra = "com.typesafe.akka" %% "akka-persistence-cassandra" % "0.9"
  val camelElastic = "org.apache.camel" % "camel-elasticsearch" % Version.camelElastic
  val camelMongoDB = "org.apache.camel" % "camel-mongodb" % Version.camelElastic
  val camelCassandra = "org.apache.camel" % "camel-cassandraql" % Version.camelElastic
  val levelDb = "org.iq80.leveldb" % "leveldb" % Version.levelDb
  val levelDbJni = "org.fusesource.leveldbjni" % "leveldbjni-all" % Version.levelDbJni

}

object Dependencies {

  import Library._

  val template = List(
    akkaActor,
    akkaPersistence,
    akkaCluster,
    akkaClusterSharding,
    akkaDistributedData,
    akkaSlf4j,
    akkaCamel,
    sparkCore,
    sparkMllib,
    akkaTestkit % "test",
    akkaMultiNodeTest % "test",
    scalaTest % "test",
    camelElastic,
    akkaPersistenceCassandra,
    camelCassandra,
    levelDb,
    levelDbJni
  )
}