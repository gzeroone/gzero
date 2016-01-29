package one.gzero.api

import spray.json.JsObject

/*case classes used by spray for marshalling and unmarshalling of JSON*/
case class Vertex(label:String, id:String, event_source: Option[String], timestamp: Option[String], properties: Option[JsObject])
case class Edge(label: String, id:Option[String], event_source: Option[String], timestamp: Option[String], properties: Option[JsObject],
                head: Vertex, tail: Vertex)
/*used for querying the graph*/
case class Query(gremlin: String)
