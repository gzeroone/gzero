package one.gzero.api

import java.sql.Timestamp

import akka.actor.Actor
import one.gzero.db.{TitanUtils, GremlinServerConnect, CassandraElasticSearchConnect, VertexCache}
import spray.routing._
import com.thinkaurelius.titan.core.TitanGraph
import one.gzero.api.{Edge => GEdge, Vertex => GVertex}
import gremlin.scala._
import scala.collection.mutable
import spray.json._


class GzeroServiceActor extends Actor with GzeroService {
  graphJava = getTitanConnection
  val graph = graphJava

  implicit def actorRefFactory = context

  // TODO - consider creating stream processing route handling.
  def receive = runRoute(routes)
}

object Protocols extends GzeroProtocols

trait GzeroService extends HttpService with CassandraElasticSearchConnect with VertexCache
  with GremlinServerConnect {
  import spray.httpx.SprayJsonSupport._
  import Protocols._
  var graphJava: TitanGraph = null
  lazy val g = graphJava.asScala


  def getTitanConnection = {
    if (graphJava == null || !graphJava.isOpen()) {
      graphJava = connect()
    }
    graphJava
  }

  def handleEdge(edge: GEdge): GzeroResult = {
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
      for( (k,v) <- edge.properties.get.fields  ) {
        println("updating edge:", k,v)
        //attempt to convert to int. if fail just convert to string
        //TODO this is probably really slow
        val x = try {
          v.convertTo[Int]
        } catch {
          case e : Exception => {
            try {
              v.convertTo[String]
            } catch {
              case e : Exception => {
                v.toString
              }
            }
          }
        }
        e.setProperty(TitanUtils.convertToTitanKey(k), x)
      }
    }
    g.tx().commit()

    GzeroResult(JsObject("edge_ack" -> e.toString.toJson), JsObject("success" -> "true".toJson))
  }

  def handleVertex(vertex: GVertex): GzeroResult = {
    val sv = getOrCreateVertex(vertex)
    GzeroResult(JsObject("vertex_ack" -> sv.toString.toJson), JsObject("success" -> "true".toJson))
  }

  def handleRegisterFeature(req: Query): GzeroResult = {
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
    GzeroResult(JsObject("register_ack" -> v.toString.toJson), JsObject("success" -> "true".toJson))
  }

  /* {"name":"feature_name", "bindings" : {}} */
  def handleFeature(req: FeatureQuery): GzeroResult = {
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
      GzeroResult(JsObject("result" -> result), JsObject("success" -> "true".toJson))
    } else {
      GzeroResult(JsObject("feature_not_found" -> true.toJson), JsObject("success" -> "true".toJson))
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
              val r = query(queryRequest)
              GzeroResult(JsObject("result" -> r.toJson), JsObject("success" -> "true".toJson))
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