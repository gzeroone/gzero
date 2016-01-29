package one.gzero

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import one.gzero.api.GZeroServiceActor
import one.gzero.db.GraphPublisher
import spray.can.Http

import scala.concurrent.duration._

object Boot extends App {

  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("gzero-system")

  // create and start our actors
  val service = system.actorOf(Props[GZeroServiceActor], "gzero-api")
  val publisher = system.actorOf(Props[GraphPublisher], "gzero-graphpublisher")

  implicit val timeout = Timeout(5.seconds)
  // start a new HTTP server on port 8080 with our service actor as the handler
  IO(Http) ? Http.Bind(service, interface = "localhost", port = 8080)
}
