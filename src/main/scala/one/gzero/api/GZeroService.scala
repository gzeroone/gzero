package one.gzero.api

import akka.actor.{ActorRef, Props, Actor, ActorSystem}
import one.gzero.db.GraphPublisher
import spray.http.MediaTypes._
import spray.routing._
import akka.event.{LoggingAdapter, Logging}
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{HttpResponse, HttpRequest}
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import java.io.IOException
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.math._
import spray.json.DefaultJsonProtocol
import spray.httpx.SprayJsonSupport.sprayJsonUnmarshaller


/* TODO - consider dropping Protocols and put implicit JSON conversion directly in the Vertex,Edge, Query classes */
trait Protocols extends DefaultJsonProtocol {
  implicit val impVertex = jsonFormat5(Vertex.apply)
  implicit val impEdge = jsonFormat7(Edge.apply)
  implicit val impQuery = jsonFormat1(Query.apply)
}

class GZeroServiceActor extends Actor with GZeroService {
  val graphPublisher = context.actorOf(Props[GraphPublisher])
  def actorRefFactory = context
  // TODO - consider creating stream processing route handling.
  def receive = runRoute(routes)
}

trait GZeroService extends HttpService with Protocols {
  val graphPublisher : ActorRef

  val routes = {
      path("edge") {
          (post & entity(as[Edge])) {
            edgeRequest =>
              complete {
                graphPublisher ! edgeRequest
                s"""{"edge_ack": $edgeRequest}"""
              }
          }
      } ~
        path("vertex") {
          (post & entity(as[Vertex])) {
            vertexRequest =>
              complete {
                s"""{"vertex_ack": $vertexRequest}"""
              }
          }
        } ~
        path("query") {
          (get & entity(as[Query])) {
            queryRequest => {
              complete {
                s"""requested query $queryRequest"""
              }
            }
          }
        }
  }
}