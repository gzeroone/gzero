package one.gzero.db

import java.sql.Timestamp
import akka.actor._
import com.thinkaurelius.titan.core.TitanGraph
import one.gzero.api.{Edge => GEdge, Vertex => GVertex}
import gremlin.scala._

class GraphPublisher() extends Actor with ActorLogging with LocalCassandraConnect with VertexCache {
  val graphJava = connect()
  val graph = graphJava
  val g = graphJava.asScala

  override def receive = {
    /* TODO - consider adding in some implicit conversions for converting between GVertex and Vertex */
    case vertex: GVertex => {
      val v = getOrCreateVertex(vertex)
      val z = v.id().asInstanceOf[Long].toString
      s"""$z -> $vertex"""
    }
    case edge: GEdge => {
      /* TODO - consider adding in some implicit conversions for converting between GEdge and Edge */
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
      s"""$edge"""
    }
  }

}
