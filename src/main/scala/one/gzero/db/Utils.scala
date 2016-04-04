package one.gzero.db

import com.thinkaurelius.titan.core.TitanGraph
import java.sql.Timestamp
import one.gzero.Config
import one.gzero.api.{Vertex => GVertex, GzeroProtocols, Query}
import gremlin.scala._
import spray.can.Http.ConnectionAttemptFailedException
import spray.json.JsObject
import scala.concurrent.{TimeoutException, Await}
import scala.concurrent.duration._
import akka.actor.ActorSystem
import spray.json._
import spray.httpx.SprayJsonSupport
import spray.client.pipelining._
import SprayJsonSupport._
import com.thinkaurelius.titan.core.TitanFactory

/* Inhererting this trait allows an app to simply create a titan graph object as graph = connect() */
trait CassandraElasticSearchConnect extends Config {
  def connect(): TitanGraph = {
    import org.apache.commons.configuration.BaseConfiguration
    val conf = new BaseConfiguration()

    // graph storage
    conf.setProperty("storage.backend", "cassandra")
    conf.setProperty("storage.hostname", cassandraHostName)

    // indexing
    conf.setProperty("index.search.backend", "elasticsearch")
    conf.setProperty("index.search.hostname", elasticsearchHostName)
    conf.setProperty("index.search.elasticsearch.client-only", "true")
    TitanFactory.open(conf)
  }
}

object TitanUtils {
  def convertToTitanKey (s: String): Key[Any] = {
    Key(s)
  }
}

object Protocols extends GzeroProtocols

trait VertexCache {
  import Protocols._

  val graph: TitanGraph
  val TimestampKey = Key[Timestamp]("timestamp")
  val EventSourceKey = Key[String]("event_source")
  /* the api allows for id, but we represent this as name inside of graph db, because the graph db has it's own id */
  val NameKey = Key[String]("name")
  val PrettyNameKey = Key[String]("prettyName")
  val RatingKey = Key[Double]("rating")
  val vertexIdCache = collection.mutable.Map[(String, String), Long]()
  val BindingsKey = Key[String]("bindings")
  val TagsKey = Key[String]("tags")

  def getOrCreateVertex(vertex: GVertex): Vertex = {
    val label = vertex.label
    val name: String = vertex.getProperty("name")
    val check = vertexIdCache.get(label, name)
    if (check.isDefined) {
      try {
        return graph.V(check.get).head()
      } catch {
        case e: Exception => {
          //log something
          vertexIdCache.remove(label, name)
        }
      }
    }

    /* go ask the graph for the vertex */
    val answer = {
      val matches = graph.V.has(label, NameKey, name).toList()
      if (matches.length > 0) {
        matches.head
      }
      else {
        /* create the vertex */
        graph +(label, NameKey -> name)
      }
    }
    if (vertex.properties.isDefined) {
      for ((k, v) <- vertex.properties.get.fields) {
        println("updating vertex:", k, v)
        //attempt to convert to int. if fail just convert to string
        //TODO this is probably really slow
        val x = try {
          v.convertTo[Int]
        } catch {
          case e: Exception => {
            try {
              v.convertTo[String]
            } catch {
              case e: Exception => {
                v.toString
              }
            }
          }
        }
        answer.setProperty(TitanUtils.convertToTitanKey(k), x)
      }
    }
    val vId = answer.id().asInstanceOf[Long]
    vertexIdCache += ((label, name) -> vId)
    graph.tx().commit()
    return answer
  }
}

case class GremlinResult(requestId: String, result: JsObject, status: JsObject)

//GraphSON is used for returning objects. The format is very similar to the syntax for adding edges/vertices.
//GZero API provides a simpler method for indicating "head" and "tail" vertices on edges. It is still possible to add
//vertices and edges using the standard gremlin api. g.addVertex...
//the difference is that in GraphSON the edges indicate their head and tail through the
//outV and inV vertices with internal graph ids.
//{"inVLabel":"vehicle","outV":8256,"label":"drove","outVLabel":"person","id":"2rs-6dc-4r9-6hs","type":"edge","inV":8416}
case class GraphSONEdge(outV:Int, inV:Int, label:String, properties: Option[JsObject] )
case class GraphSONVertex(label:String, id:Option[Int], properties: Option[JsObject])
case class NameProperty (name:String)

object GremlinResultJsonProtocol extends DefaultJsonProtocol with GzeroProtocols {
  implicit val gresult = jsonFormat3(GremlinResult)
  implicit val gedge = jsonFormat4(GraphSONEdge)
  implicit val gvertex = jsonFormat3(GraphSONVertex)
}

trait GremlinServerConnect extends Config {
  import SprayJsonSupport._
  import GremlinResultJsonProtocol._
  implicit val system = ActorSystem("gzero-api")
  import system.dispatcher
  val pipeline = sendReceive ~> unmarshal[GremlinResult]

  def query(query : Query): JsObject = {
    import GremlinResultJsonProtocol._
    val (gremlin, bindings) = (query.gremlin, query.bindings)
    val jsonQuery = query.toJson

    //curl -X POST -d "{\"gremlin\":\"100-x\", \"bindings\":{\"x\":1}}" "http://localhost:8182"
    val res =
    try {
      val responseFuture = pipeline {
        if (bindings.isDefined) {
          Post(gremlinServerAddress, JsObject("gremlin" -> JsString(gremlin), "bindings" -> bindings.get))
        } else {
          Post(gremlinServerAddress, JsObject("gremlin" -> JsString(gremlin)))
        }
      }

      Await.result(responseFuture, 24 hours)
      responseFuture.value.get.get.toJson.asJsObject
    }
    catch {
      case e: Exception => {
        e.printStackTrace()
        JsObject("error" -> true.toJson, "message" -> "unknown error".toJson, "gremlin" -> query.toJson)
      }
    }
    res
  }
}
