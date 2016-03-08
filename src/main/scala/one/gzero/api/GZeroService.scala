package one.gzero.api

import java.sql.Timestamp

import akka.actor.Actor
import one.gzero.db.{GremlinServerConnect, CassandraElasticSearchConnect, VertexCache}
import spray.routing._
import spray.json.{JsString, JsObject}
import spray.httpx.SprayJsonSupport.sprayJsonUnmarshaller
import com.thinkaurelius.titan.core.TitanGraph
import one.gzero.api.{Edge => GEdge, Vertex => GVertex}
import gremlin.scala._
import spray.json._
import scala.collection.mutable


class GzeroServiceActor extends Actor with GzeroService {
  graphJava = getTitanConnection
  val graph = graphJava

  def actorRefFactory = context

  // TODO - consider creating stream processing route handling.
  def receive = runRoute(routes)
}

trait GzeroService extends HttpService with GzeroProtocols with CassandraElasticSearchConnect with VertexCache
  with GremlinServerConnect {
  var graphJava: TitanGraph = null
  lazy val g = graphJava.asScala

  def getTitanConnection = {
    if (graphJava == null || !graphJava.isOpen()) {
      graphJava = connect()
    }
    graphJava
  }

  def handleEdge(edge: GEdge): String = {
    val head = getOrCreateVertex(edge.head)
    val tail = getOrCreateVertex(edge.tail)

    val e = head.addEdge(edge.label, tail)

    if (edge.timestamp.isDefined) {
      val timestamp = Timestamp.valueOf(edge.timestamp.get)
      e.setProperty(TimestampKey, timestamp)
    }
    if (edge.event_source.isDefined) {
      e.setProperty(EventSourceKey, edge.event_source.get)
    }

    if (edge.properties.isDefined) {
      //TODO - update the properties of the edge
    }
    g.tx().commit()

    s"""{"edge_ack": $edge}"""
  }

  def handleVertex(vertex: GVertex): String = {
    val v = getOrCreateVertex(vertex)
    s"""{"vertex_ack": $vertex}"""
  }

  def handleRegisterFeature(req: Query): String = {
    val (gremlin, b, t) = (req.gremlin, req.bindings, req.tags)

    val gv = new GVertex("feature", Some(JsObject("name" -> JsString(gremlin))))
    val v = getOrCreateVertex(gv)

    // The general technique will be to define the query logic with all optional parameters as
    // bindings. When "executing the feature" you can pass the bindings without the gremlin query
    // and the stored query will be executed with the bindings updated.
    if (b.isDefined) {
      v.setProperty(BindingsKey, b.get.toString)
    }
    if (t.isDefined) {
      v.setProperty(TagsKey, t.get.toString)
    }
    v.graph().tx().commit()
    s"""{"register_ack" : $v}"""
  }

  /* {"name":"feature_name", "bindings" : {}} */
  def handleFeature(req: FeatureQuery): String = {
    val vo = if (req.id.isDefined) {
      g.V(req.id.get).headOption()
    } else {
      g.V.hasLabel("feature").has(NameKey, req.name).headOption()
    }
    val res = if (vo.isDefined) {
      val v = vo.get
      val bindingsMap = new mutable.HashMap[String, JsValue]
      val bindingsProp = v.property(BindingsKey)
      if (bindingsProp.isPresent) {
        bindingsMap ++= bindingsProp.value().parseJson.asJsObject.fields
      }
      if (req.bindings.isDefined) {
        bindingsMap ++= req.bindings.get.fields
      }
      val gremlin = v.property(NameKey).value()
      val queryRequest = if (!bindingsMap.isEmpty) {
        Query(gremlin, Some(new JsObject(bindingsMap.toMap)), None)
      } else {
        Query(gremlin, None, None)
      }
      val result = query(queryRequest)
      result
    } else {
      "\"Feature Not Found\""
    }
    res
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
        (get & entity(as[FeatureQuery])) {
          req => {
            complete {
              handleFeature(req)
            }
          }
        }
      }
  }
}