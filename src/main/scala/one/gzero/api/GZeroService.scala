package one.gzero.api

import java.sql.Timestamp

import akka.actor.Actor
import one.gzero.db.{LocalGremlinQuery, CassandraElasticSearchConnect, VertexCache}
import spray.routing._
import spray.json.{JsString, JsObject}
import spray.httpx.SprayJsonSupport.sprayJsonUnmarshaller
import com.thinkaurelius.titan.core.TitanGraph
import one.gzero.api.{Edge => GEdge, Vertex => GVertex}
import gremlin.scala._


class GZeroServiceActor extends Actor with GZeroService {
  graphJava = getTitanConnection
  val graph = graphJava
  def actorRefFactory = context
  // TODO - consider creating stream processing route handling.
  def receive = runRoute(routes)
}

trait GZeroService extends HttpService with GzeroProtocols with CassandraElasticSearchConnect with VertexCache with LocalGremlinQuery {
  var graphJava: TitanGraph = null
  lazy val g = graphJava.asScala

  def getTitanConnection = {
    if (graphJava == null || !graphJava.isOpen()) {
      graphJava = connect()
    }
    graphJava
  }

  def handleEdge(edge:GEdge): String = {
    val head = getOrCreateVertex(edge.head)
    val tail = getOrCreateVertex(edge.tail)

    val e = head.addEdge(edge.label, tail)

    if (edge.timestamp.isDefined)
    {
      val timestamp = Timestamp.valueOf(edge.timestamp.get)
      e.setProperty(TimestampKey,timestamp)
    }
    if ( edge.event_source.isDefined ) {
      e.setProperty(EventSourceKey, edge.event_source.get)
    }

    if (edge.properties.isDefined) {
      //TODO - update the properties of the edge
    }
    g.tx().commit()

    s"""{"edge_ack": $edge}"""
  }

  def handleVertex(vertex:GVertex): String = {
    val v = getOrCreateVertex(vertex)
    s"""{"vertex_ack": $vertex}"""
  }

  def handleRegisterFeature(req:Query): String = {
    val BindingsKey = Key[String]("bindings")
    val TagsKey = Key[String]("tags")

    val (g,b,t) = (req.gremlin, req.bindings, req.tags)

    val gv = new GVertex("feature", Some(JsObject("name" -> JsString(g))))
    val v = getOrCreateVertex(gv)

    // The general technique will be to define the query logic with all optional parameters as
    // bindings. When "executing the feature" you can pass the bindings without the gremlin query
    // and the stored query will be executed with the bindings updated.
    if ( b.isDefined ) {
      v.setProperty(BindingsKey, b.get.toString)
    }
    if ( t.isDefined ) {
      v.setProperty(TagsKey, t.get.toString)
    }
    v.graph().tx().commit()
    s"""{"register_ack" : $v}"""
  }

  val routes = {
      path("edge") {
          (post & entity(as[GEdge])) {
            edge =>
              complete {
                handleEdge(edge)
              }
          }
      } ~
        path("vertex") {
          (post & entity(as[GVertex])) {
            vertex =>
              complete {
                handleVertex(vertex)
              }
          }
      } ~
        path("query") {
          (get & entity(as[Query])) {
            queryRequest => {
              complete {
                query(queryRequest)
              }
            }
          }
      } ~
        path("register_feature") {
          (post & entity(as[Query])) {
            req => {
              complete {
                handleRegisterFeature(req)
              }
            }
        }
      } ~
        path("feature") {
          (post & entity(as[Query])) {
            req => {
              complete {
                handleRegisterFeature(req)
              }
            }
          }
        }
  }
}