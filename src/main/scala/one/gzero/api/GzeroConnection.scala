package one.gzero.api

import akka.actor.ActorSystem
import spray.can.Http.ConnectionAttemptFailedException
import spray.client.pipelining._
import spray.httpx.SprayJsonSupport
import spray.json._

import scala.concurrent._
import scala.concurrent.duration._

/**
  * Created by james on 3/7/16.
  */


class GzeroConnection(hostname: String, port: Int) {
  import SprayJsonSupport._
  import Protocols._
  implicit val system = ActorSystem("gzero-api")
  import system.dispatcher
  val pipeline = sendReceive ~> unmarshal[GzeroResult]

  def sendEdge(edge: Edge) = {
    send(edge.toJson.asJsObject, "edge")
  }

  def send(data: JsObject, path:String): GzeroResult = {
    val gzeroAddress = s"http://$hostname:$port/$path"

    val res =
      try {
        val responseFuture = pipeline {
          Post(gzeroAddress, data)
        }
        Await.result(responseFuture, 24 hours)
        GzeroResult(JsObject("message" -> responseFuture.value.get.get.toJson), JsObject("success" -> "true".toJson))
      }
      catch {
        case ca: ConnectionAttemptFailedException => {
          ca.printStackTrace()
          GzeroResult(JsObject("message" -> "connection failed".toJson), JsObject("error" -> "true".toJson))
        }
        case te: TimeoutException => {
          te.printStackTrace()
          GzeroResult(JsObject("message" -> s"timeout sending data $data".toJson), JsObject("error" -> "true".toJson))
        }
        case e: Exception => {
          e.printStackTrace()
          GzeroResult(JsObject("message" -> s"an unknown error occurred $data".toJson), JsObject("error" -> "true".toJson))
        }
      }
    res
  }
}
