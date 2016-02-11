import one.gzero.db.{GraphSONEdge, GremlinResultJsonProtocol, GraphSONVertex}
import org.scalatest._

import spray.json._
import spray.httpx.SprayJsonSupport
import SprayJsonSupport._


class GremlinTests extends FlatSpec with Matchers {

  case class NameProperty(name:String)
  import GremlinResultJsonProtocol._

  "A GraphSON object" should "be convertible to a GraphSONEdge with correct label" in {
    val edgeJson = JsObject("inVLabel"->JsString("vehicle"),"outV"->JsNumber(8256),"label"->JsString("drove"),"outVLabel"->JsString("person"),"id"->JsString("2rs-6dc-4r9-6hs"),"type"->JsString("edge"),"inV"->JsNumber(8416))
    val edge = edgeJson.convertTo[GraphSONEdge]
    edge.inV should be (8416)
    edge.outV should be (8256)
    edge.label should be ("drove")
  }

  it should "be convertible to a GraphSONVertex with corret name property " in {
    implicit val gresult = jsonFormat1(NameProperty)
    val vJ = JsObject("label"->JsString("vehicle"),"id"->JsNumber(2),"type"->JsString("vertex"), "properties" -> JsObject("name"->JsString("1932 Ford V-8 B-400 convertible sedan")))
    val v = vJ.convertTo[GraphSONVertex]
    v.label should be ("vehicle")
    v.id.get should be (2)
    val name = v.properties.get.convertTo[NameProperty]
    name.name should be ("1932 Ford V-8 B-400 convertible sedan")
  }
}

