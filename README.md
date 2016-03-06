# real-time-data-classification-akka-spark

Building a scalable predictor with akka technologies


## Overview

This application is a framewprk to build a real-time pipeline, targeted for user bound data.
In this specific case it allows to classify user credit card transactions, but it can be applied to twitter, click stream, rtb, etc.
The Classification of transactions is just an example. In the middle layer, every business logic can be applied.

This template can be also a base layer for anti-fraud applications. As soon as the transaction comes in, it is classified and then it can be used as an input for a rule engine or a neural network for pattern recognition, in order to detect a possible fraud.

Disclaimer: Random generated transactions contain real venues, they come from a Foursquare download.


## Background


### Persistence - At Least Once Delivery

At-least-once-delivery design pattern avoids the lost of any message at the cost of re-evaluate some message in case of failure. At-least-once-delivery is provided by Akka Persistence module. To leverage this pattern data should be handled in a idempotent way, otherwise side effects will occur, for example functionalities like cassandra counters. shouldn't be used.
Akka Persistence enables to persist each state change, but in the at-least-once-delivery context it allows to store messages as soon as they come from the mailbox. When messages are delivered to a remote counterpart, stored messages can be unpersisted. Proceeding like this, if a fail occurs between message persistence and remote delivery, lost messages can be recovered from the persistent store.


### Sharding 

Sharding is a horizontal partition of data. In Akka Cluster actors can be distributed horizontally using Cluster Sharding. Actors are partitioned by their logical identifier. Cluster Sharding allows to deliver messages to a specific actor without knowing its location, only its id is required.


### Distributed Data

Distributed Data is an elegant way to share objects among cluster actors. It provides a key-value store api. Data can be also updated by actors and eventual conflicts are resolved by CRDTs.



## Scope and Components

This application is a real time distributed classifier. It classifies financial transactions using a batch trained model. Transactions ( mocked up in the template ) of a single customer ( ndg ) are processed by a dedicated actor. It permits to have data segregation and enables to make customizations per ndg. The training of the model is a schedulable batch process that reads a bunch of transactions. Once the model is ready it will be distributed across the cluster and each actor will start to use the new model.
 

### Transaction Generator (TransactionGenerator.scala)

This module generates fake transactions and sends them to the system. In a real environment it will be replaced by Kafka or an other message queue system. This is not part of the architecture.

    clusterSystem.scheduler.schedule(0.second, intervalTransactions) {
      val uuid = UUID.randomUUID()
      val sender: String = ndgs.toVector(rnd.nextInt(ndgs.size))
      val receiver = ndgs.-(sender).toVector(rnd.nextInt(ndgs.size - 1))
      val description = descriptions(rnd.nextInt(descriptions.size))
      ingestionActor ! TransactionRaw(uuid.toString, sender, receiver, description, rnd.nextInt(100000))
    }

	
### Ingestion Layer ( IngestionCheckpointActor.scala )

Ingestion layer is the entry point of the system, it receives messages from the generator (it can be a receiver of Kafka).
The goal of this component is to do at-least-once-delivery, it stores messages as soon as it receives them from actor mailbox. 

  

    // First landing point of received messages: new transaction or delivery confirmations.
      override def receiveCommand: Receive = {
        case trx: TransactionRaw => persist(TrxSent(trx))(updateState)
        case TrxClassifiedFeedback(deliveryId) => persist(TrxConfirmed(deliveryId))     (updateState)
      }

Cassandra has been used as a backbone for akka-persistence.

Once the message has been persisted, the ingestion layer will forward the message adding it a deliveryID. DeliveryID will be used to acknowledge the ingestion layer once the message has been delivered to its final destination.
In case of node failure, once the ingestion actor resumes, it will replay all messages in not-confirmed state.

Once a message is delivered, ingestion layer receives a confirmation with a message TrxClassifiedFeedback(deliveryId).  

    def updateState(tps: TrxPersistedStatus): Unit = tps match {
        case TrxSent(trx) =>
          log.debug("updateState TrxSent: " + trx)
          deliver(destination.path)(deliveryId => TrxEnvelope(deliveryId, trx))
    
        case TrxConfirmed(deliveryId) =>
          log.debug("updateState TrxConfirmed " + deliveryId)
          confirmDelivery(deliveryId)
      }
     

### Spark Model Builder ( SparkModelBuilder.scala )

This actor handles a spark context in order to train a model with labeled transactions.

          println("Model is going to create")
          val newTrainingData = dataModelReader.readData(sc())
          val newModel = modelBuilder.build(newTrainingData)
          log.info("Model created")
    
          val key = SparkModelBuilderActor.modelDataKey
          val newModelRegister = LWWRegister(newModel)
          replicator ! Replicator.Update(key, newModelRegister, writeAll, Some(sender()))(x => newModelRegister)
          log.info("Updated the DistributedData on all nodes")
          
Once the model is ready, DistributedData is leveraged to broadcast the model across the cluster.

    replicator ! Replicator.Update(key, newModelRegister, writeAll, Some(sender()))
    

### Data Classifier ( TransactionClassifier.scala )

This component needs to receive transactions from the ingestion layer. After receiving them, it classifies transactions with the broadcasted model. Finally it forwards them to writer actors. 
Forwarding transactions to an other layer, before persisting them in a datastore, enables to change the datastore very easily.

    def initialized: Receive = {
    case trxEnvelope: TrxEnvelope =>
      val classifiedTrx = classify(trxEnvelope.trx)
      log.debug("sending to the writer")
      writer ! TransactionWithSender(sender, trxEnvelope.deliveryId, classifiedTrx)
    case c @ Changed(ModelKey) =>
      log.debug("The model changed, state: initialized")
      // no need to synchronize model, thanks to the actor pattern
      model = Some(c.get(ModelKey).value)
      }
  
Attention must be paid to the forwarding of the sender reference to the writer layer. The writer needs the ingestion ActorRef to acknowledge transactions. <br/>
This kind of actor will be notified from DistributedData directly on mailbox, without any dependency or integration with model builder:

    case c @ Changed(ModelKey) =>

Data classifiers are sharded across the cluster with the following principles:

    val idExtractor: ShardRegion.ExtractEntityId = {
	    case trx: TrxEnvelope =>
	      (trx.trx.senderNdg, trx)
    }
    
    val shardResolver: ShardRegion.ExtractShardId = {
	    case trx: TrxEnvelope => (math.abs(trx.trx.senderNdg.hashCode) % shardsNumber).toString
	  

### Data Sharding

Sharding is operating at classifier level and it's very easy to setup according to previously listed parameters.

    ClusterSharding(clusterSystem).start(
      typeName = TransactionClassifier.shardName,
      //injection of cassandra writer actor
      entityProps = TransactionClassifier.props(cassandraActor, featuresBuilder),
      settings = ClusterShardingSettings(clusterSystem),
      extractEntityId = TransactionClassifier.idExtractor,
      extractShardId = TransactionClassifier.shardResolver
    )

	
### Writer Layer ( TransactionWriter.scala )

This layer is responsible to store data in a database and to acknowlege the ingestion layer for at-least-once delivery.

    def receive: Receive = {
    case msg: TransactionWithSender =>
      saveTransaction(msg.transaction)
      log.debug(s"Saved Transaction: ${msg.transaction}")
      msg.sender ! TrxClassifiedFeedback(msg.deliveryId)
     }
  

saveTransaction has to be overridden with each database specific logic.
Attention must be paid to handling saveTransaction in a sync way. If not, the acknowlege will be triggered before the effective delivery to the datastore, exposing to possible data loss.


### DataStore

This application requires a distributed datastore. Akka persistence can plug-in different datastores. In this template Cassandra has been choosen as the distributed journal of akka persistence and as final datastore where to archive all classified transactions.
Cassandra in a peer to peer distributed key value store, with tunable consistency. Having no single point of failure makes it very suitable for the journal.
When Akka persistence is run in a distributed environment, a distributed journal is needed in order to deal with failure.


    cassandra-config {
        hosts = ["127.0.0.1:9042"]
    }
    
    cassandra-journal {
        # FQCN of the cassandra journal plugin
        class = "akka.persistence.cassandra.journal.CassandraJournal"
        # Comma-separated list of contact points in the cluster.
        # Host:Port pairs are also supported.In that case the port parameter will be ignored.
        contact-points = ${cassandra-config.hosts}
    }

In order to speed-up recovery the ingestion layer takes snapshots every 1000 messages. Also snapshots are stored in Cassandra.

    cassandra-snapshot-store {
        # FQCN of the cassandra snapshot store plugin
         class = "akka.persistence.cassandra.snapshot.CassandraSnapshotStore"
        # Comma-separated list of contact points in the cluster.
        # Host:Port pairs are also supported. In that case the port parameter will be ignored.
        contact-points = ${cassandra-config.hosts}
        # replication strategy to use. SimpleStrategy or NetworkTopologyStrategy
        replication-strategy = "SimpleStrategy"
        # Replication factor to use when creating a keyspace. Is only used when replication-strategy is SimpleStrategy.
        replication-factor = 2
        # Write consistency level
        write-consistency = "ONE"
        # Read consistency level
        read-consistency = "ONE"
    }

As the snapshot is only a recovery facility, the consistency level has been set to ONE to reduce the performance impact.



## RUN

Steps to make this application run are:

*   Start Cassandra
*   Boot cluster nodes

Requirements:

*   Docker ( not mandatory)
*   JDK 1.7+

Library versions:

*   Scala Version = 2.11.7
*   Akka Version = 2.4.1
*   Spark Version = 1.5.2


### Start Cassandra

Cassandra will be the backbone of akka persistence and the datastore itself.
In order to simplify Cassandra setup a single docker command is required:

    docker run --name rtclassification-cassandra -p 9160:9160 -p 9042:9042 -d cassandra:3

The ip of the docker-host must be put in the src/main/resources/application.conf cassandra-config.hosts

### Start master

Run the master part of the application that creates the transactions.

    sbt 'runMain com.agilelab.RTClassification.Main 127.0.0.1 2551 RTClassificationSystem master'

It will setup the seed node of the cluster and will start the generator of fake transactions.

### Start slave

Run the slave (it will run all components but the ingestion and the transaction generator).

    sbt 'runMain com.agilelab.RTClassification.Main 127.0.0.1 2552 RTClassificationSystem slave'

The slave is a second node of the cluster that will join the master one.



