package one.gzero

import com.typesafe.config.ConfigFactory

trait Config {
  private val config = ConfigFactory.load()
  private val gzeroConfig = config.getConfig("gzero")

  val interface = gzeroConfig.getString("interface")
  val port = gzeroConfig.getInt("port")
}

