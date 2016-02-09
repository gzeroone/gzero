package one.gzero.api

import spray.json._
import DefaultJsonProtocol._


/*case classes used by spray for marshalling and unmarshalling of JSON*/
//case class VertexProperties(name:String, event_source: Option[String], timestamp: Option[String])
case class Vertex(label:String, properties : Option[JsObject]) {
  def getProperty(key : String) : String = {
    properties.get.fields.get(key).get.convertTo[String]
  }
  def name : String = getProperty("name")
}
case class Edge(label: String, name:Option[String], event_source: Option[String], timestamp: Option[String], properties: Option[JsObject],
                head: Vertex, tail: Vertex) {
  def getProperty(key : String) : String = {
    properties.get.fields.get(key).get.convertTo[String]
  }
}
/*used for querying the graph*/
case class Query(gremlin: String, bindings:Option[JsObject], tags:Option[JsArray])

case class GraphSONEdge(outV:Int, inV:Int, label:String, properties: Option[JsObject])
case class GraphSONVertex(label:String, id:Option[Int], properties: Option[JsObject])

/* TODO - consider dropping Protocols and put implicit JSON conversion directly in the Vertex,Edge, Query classes */
trait GzeroProtocols extends DefaultJsonProtocol {
  implicit val impVertex = jsonFormat2(Vertex.apply)
  implicit val impEdge = jsonFormat7(Edge.apply)
  implicit val impQuery = jsonFormat3(Query.apply)
}
