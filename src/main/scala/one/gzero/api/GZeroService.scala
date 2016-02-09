package one.gzero.api

import java.sql.Timestamp

import akka.actor.Actor
import one.gzero.db.{LocalGremlinQuery, LocalCassandraConnect, VertexCache}
import org.apache.tinkerpop.gremlin.driver.{Client, Cluster}
import spray.routing._
import spray.json.{JsString, JsObject, DefaultJsonProtocol}
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

trait GZeroService extends HttpService with GzeroProtocols with LocalCassandraConnect with VertexCache with LocalGremlinQuery {
  var graphJava: TitanGraph = null
  lazy val g = graphJava.asScala

  def getTitanConnection = {
    if (graphJava == null || !graphJava.isOpen()) {
      graphJava = connect()
    }
    graphJava
  }

  val routes = {
      path("edge") {
          (post & entity(as[GEdge])) {
            edge =>
              complete {
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
          }
      } ~
        path("vertex") {
          (post & entity(as[GVertex])) {
            vertex =>
              complete {
                val v = getOrCreateVertex(vertex)
                s"""{"vertex_ack": $vertex}"""
              }
          }
      } ~
        path("query") {
          (get & entity(as[Query])) {
            queryRequest => {
              complete {
                query(queryRequest.gremlin)
              }
            }
          }
      } ~
        path("register_feature") {
          (post & entity(as[Query])) {
            req => {
              complete {
                val g = req.gremlin
                val b = req.bindings
                val t = req.tags

                val gv = new GVertex("feature", Some(JsObject("name" -> JsString(g))))
                val v = getOrCreateVertex(gv)

                // The general technique will be to define the query logic with all optional parameters as
                // bindings. When "executing the feature" you can pass the bindings without the gremlin query
                // and the stored query will be executed with the bindings updated.
                if ( b.isDefined ) {
                  val BindingsKey = Key[String]("bindings")
                  v.setProperty(BindingsKey, b)
                }
                if ( t.isDefined ) {
                  val TagsKey = Key[String]("tags")
                  v.setProperty(TagsKey, b)
                }

                s"""{"register_ack" : $v}"""

                //query(queryRequest.gremlin)
              }
            }
        }
      }
  }
}