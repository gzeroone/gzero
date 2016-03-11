package one.gzero

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import one.gzero.api.GzeroServiceActor
import spray.can.Http

import scala.concurrent.duration._


object Boot extends App with Config {

  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("gzero-system")

  // create and start our actors
  val service = system.actorOf(Props[GzeroServiceActor], "gzero-api")
  //val publisher = system.actorOf(Props[GraphPublisher], "gzero-graphpublisher")

  //two minute timeout -- TODO - configure this in application.conf
  implicit val timeout = Timeout(120.seconds)
  // start a new HTTP server on port 8080 with our service actor as the handler
  IO(Http) ? Http.Bind(service, interface = gzeroInterface, port = gzeroPort)
}
