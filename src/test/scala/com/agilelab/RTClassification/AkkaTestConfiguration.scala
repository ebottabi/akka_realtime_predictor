package com.agilelab.RTClassification

import com.typesafe.config.{Config, ConfigFactory}


trait AkkaTestConfiguration {

  private var _config = ConfigFactory.parseString(
    """
      akka.actor.provider = "akka.cluster.ClusterActorRefProvider"
      akka.loglevel = INFO
      akka.actor.warn-about-java-serializer-usage = off
      akka.remote {
          log-remote-lifecycle-events = off
          netty.tcp {
            hostname = "127.0.0.1"
            port = 0
          }
        }
    """)

  def addConfig(config: Config) = {
    this._config = config.withFallback(this.testConfig)
  }

  def testConfig = _config
}
