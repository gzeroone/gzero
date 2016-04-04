package one.gzero

import com.typesafe.config.ConfigFactory

trait Config {
  private val config = ConfigFactory.load()
  private val gzeroConfig = config.getConfig("gzero")
  private val titanConfig = config.getConfig("titan")
  private val gremlinServerConfig = titanConfig.getConfig("gremlin-server")
  private val cassandraConfig = titanConfig.getConfig("cassandra")
  private val elasticSearchConfig = titanConfig.getConfig("elasticsearch")

  val gzeroInterface = gzeroConfig.getString("interface")
  val gzeroPort = gzeroConfig.getInt("port")

  val cassandraHostName = cassandraConfig.getString("hostname")
  val elasticsearchHostName = elasticSearchConfig.getString("hostname")

  val gremlinServerAddress = gremlinServerConfig.getString("address") //fully qualified rest style
}

