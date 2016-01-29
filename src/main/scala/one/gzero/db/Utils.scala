package one.gzero.db

import com.thinkaurelius.titan.core.TitanGraph
import java.sql.Timestamp
import one.gzero.api.{Edge => GEdge, Vertex => GVertex}
import gremlin.scala._

/* Inhererting this trait allows an app to simply create a titan graph object as graph = connect() */
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
    conf.setProperty("index.search.elasticsearch.interface", "NODE")
    */
    import com.thinkaurelius.titan.core.TitanFactory
    TitanFactory.open(conf)
  }
}

trait VertexCache {
    val graph : TitanGraph
    val TimestampKey = Key[Timestamp]("timestamp")
    val EventSourceKey = Key[String]("event_source")
    /* the api allows for id, but we represent this as name inside of graph db, because the graph db has it's own id */
    val NameKey = Key[String]("name")
    val PrettyNameKey = Key[String]("prettyName")
    val RatingKey = Key[Double]("rating")
    val vertexIdCache = collection.mutable.Map[(String, String), Long]()

    def getOrCreateVertex(vertex: GVertex): Vertex = {
      val label = vertex.label
      val name = vertex.id
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
      graph.tx().commit()
      return answer
    }
  }
