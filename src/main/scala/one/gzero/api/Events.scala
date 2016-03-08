package one.gzero.api

import spray.json._

abstract class PropertyHolder {
  import DefaultJsonProtocol._
  val properties : Option[JsObject]
  def getProperty(key : String) : String = {
    properties.get.fields.get(key).get.convertTo[String]
  }
  def getListProperty(key : String) : List[String] = {
    properties.get.fields.get(key).get.convertTo[List[String]]
  }
  def getDoubleProperty(key : String) : Double = {
    properties.get.fields.get(key).get.convertTo[Double]
  }
  def getIntProperty(key : String) : Int = {
    properties.get.fields.get(key).get.convertTo[Int]
  }
}


/*case classes used by spray for marshalling and unmarshalling of JSON*/
//case class VertexProperties(name:String, event_source: Option[String], timestamp: Option[String])
case class Vertex(label:String, properties : Option[JsObject]) extends PropertyHolder {
  def name : String = getProperty("name")
}
case class Edge(label: String, name:Option[String], event_source: Option[String], timestamp: Option[String], properties: Option[JsObject],
                head: Vertex, tail: Vertex) extends PropertyHolder
/*used for querying the graph*/
case class Query(gremlin: String, bindings:Option[JsObject], tags:Option[JsArray])

case class FeatureQuery(name: String, bindings:Option[JsObject])

case class GraphSONEdge(outV:Int, inV:Int, label:String, properties: Option[JsObject])
case class GraphSONVertex(label:String, id:Option[Int], properties: Option[JsObject])

/* TODO - consider dropping Protocols and put implicit JSON conversion directly in the Vertex,Edge, Query classes */
trait GzeroProtocols extends DefaultJsonProtocol {
  implicit val impVertex = jsonFormat2(Vertex.apply)
  implicit val impEdge = jsonFormat7(Edge.apply)
  implicit val impQuery = jsonFormat3(Query.apply)
  implicit val impFeatureQuery = jsonFormat2(FeatureQuery.apply)
}
