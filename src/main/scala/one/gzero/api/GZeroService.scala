package one.gzero.api

import java.sql.Timestamp

import akka.actor.Actor
import spray.routing._
import spray.json.DefaultJsonProtocol
import spray.httpx.SprayJsonSupport.sprayJsonUnmarshaller
import com.thinkaurelius.titan.core.TitanGraph
import one.gzero.api.{Edge => GEdge, Vertex => GVertex}
import gremlin.scala
import gremlin.scala._

trait LocalCassandraConnect {
  def connect(): TitanGraph = {
    import org.apache.commons.configuration.BaseConfiguration
    val conf = new BaseConfiguration()
    /* graph storage */
    conf.setProperty("storage.backend", "cassandra")
    conf.setProperty("storage.hostname", "127.0.0.1")
    /* indexing */
    /*
    conf.setProperty("index.search.backend", "elasticsearch")
    conf.setProperty("index.search.hostname" , "127.0.0.1")
    conf.setProperty("index.search.elasticsearch.client-only" ,  "true")
    */
/*    conf.setProperty("index.search.elasticsearch.interface", "NODE") */
    import com.thinkaurelius.titan.core.TitanFactory
    TitanFactory.open(conf)
  }
}

/* TODO - consider dropping Protocols and put implicit JSON conversion directly in the Vertex,Edge, Query classes */
trait Protocols extends DefaultJsonProtocol {
  implicit val impVertex = jsonFormat5(Vertex.apply)
  implicit val impEdge = jsonFormat7(Edge.apply)
  implicit val impQuery = jsonFormat1(Query.apply)
}

class GZeroServiceActor extends Actor with GZeroService {
/*  val graphPublisher = context.actorOf(Props[GraphPublisher])
*/
  graphJava = getTitanConnection
  def actorRefFactory = context
  // TODO - consider creating stream processing route handling.
  def receive = runRoute(routes)
}



trait GZeroService extends HttpService with Protocols with LocalCassandraConnect {
  var graphJava: TitanGraph = null
  lazy val graph = graphJava.asScala
  lazy val vertexIdCache = collection.mutable.Map[(String, String), Long]()

  val TimestampKey = Key[Timestamp]("timestamp")
  val EventSourceKey = Key[String]("event_source")
  /* the api allows for id, but we represent this as name inside of graph db, because the graph db has it's own id */
  val NameKey = Key[String]("name")
  val PrettyNameKey = Key[String]("prettyName")
  val RatingKey = Key[Double]("rating")


  def getOrCreateVertex(vertex : GVertex): scala.Vertex = {
    val label = vertex.label
    val name = vertex.id
    val check = vertexIdCache.get(label, name)
    if (check.isDefined) {
      try {
        return graph.V(check.get).head()
      } catch {
        case e : Exception => {
          //log something
          vertexIdCache.remove(label,name)
        }
      }
    }

    /* go ask the graph for the vertex */
    val answer = {
      val matches = graph.V.has(label, NameKey, name).toList()
      if (matches.length > 0) {
        println("matches:")
        println(matches)
        matches.head
      }
      else {
        /* create the vertex */
        graph +(label, NameKey -> name)
      }
    }
    val vId = answer.id().asInstanceOf[Long]
    vertexIdCache += ((label, name) -> vId)
    return answer
  }

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
                graph.tx().commit()

                s"""{"edge_ack": $edge}"""
              }
          }
      } ~
        path("vertex") {
          (post & entity(as[GVertex])) {
            vertex =>
              complete {
                val v = getOrCreateVertex(vertex)

                if (vertex.timestamp.isDefined)
                {
                  val timestamp = Timestamp.valueOf(vertex.timestamp.get)
                  v.setProperty(TimestampKey,timestamp)
                }

                if (vertex.properties.isDefined) {
                  //TODO - update the properties of the vertex
                }
                graph.tx().commit()
                s"""{"vertex_ack": $vertex}"""
              }
          }
        } ~
        path("query") {
          (get & entity(as[Query])) {
            queryRequest => {
              complete {
                /* TODO  - connect to gremlin server or figure out passing gremlin string into graph */
                s"""requested query $queryRequest"""
              }
            }
          }
        }
  }
}