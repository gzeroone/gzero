import one.gzero.db.GremlinResultJsonProtocol._
import one.gzero.db.{GraphSONEdge, GremlinResultJsonProtocol, GraphSONVertex}
import org.specs2._
import spray.json._
import spray.httpx.SprayJsonSupport
import SprayJsonSupport._


class GremlinTests extends Specification {

  case class NameProperty(name:String)

  implicit val gresult = jsonFormat1(NameProperty)

  import GremlinResultJsonProtocol._

  def is =
  "GraphSON objects and strings should"                                                            ^
    p^
    "be convertable to GraphSONEdge"          ! e1^
    "be convertable to GraphSONVertex with correct name property"        ! e2^
    end

  def e1 = {
    val edgeJson = JsObject("inVLabel"->JsString("vehicle"),"outV"->JsNumber(8256),"label"->JsString("drove"),"outVLabel"->JsString("person"),"id"->JsString("2rs-6dc-4r9-6hs"),"type"->JsString("edge"),"inV"->JsNumber(8416))
    val edge = edgeJson.convertTo[GraphSONEdge]
    edge.inV mustEqual 8416
    edge.outV mustEqual 8256
    edge.label mustEqual "drove"
  }

  def e2 = {
    val vJ = JsObject("label"->JsString("vehicle"),"id"->JsNumber(2),"type"->JsString("vertex"), "properties" -> JsObject("name"->JsString("1932 Ford V-8 B-400 convertible sedan")))
    val v = vJ.convertTo[GraphSONVertex]
    v.label mustEqual "vehicle"
    v.id mustEqual 2
    val name = v.properties.get.convertTo[NameProperty]
    name.name mustEqual "1932 Ford V-8 B-400 convertible sedan"
  }

}

