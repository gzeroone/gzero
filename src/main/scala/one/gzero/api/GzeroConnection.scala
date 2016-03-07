package one.gzero.api

import akka.actor.ActorSystem
import spray.can.Http.ConnectionAttemptFailedException
import spray.client.pipelining._
import spray.httpx.SprayJsonSupport
import spray.json._

import scala.concurrent._
import scala.concurrent.duration._
import SprayJsonSupport._

/**
  * Created by james on 3/7/16.
  */

case class GzeroResult(requestId: String, result: JsObject, status: JsObject)

object Protocols extends GzeroProtocols {
  implicit val gzProt = jsonFormat3(GzeroResult)
}

class GzeroConnection(hostname: String, port: Int) {
  def send(data: String): GzeroResult = {
    import Protocols._
    val gzeroAddress = s"$hostname:$port"

    implicit val system = ActorSystem("gzero-api")
    import system.dispatcher

    val pipeline = sendReceive ~> unmarshal[GzeroResult]

    val res =
      try {
        val responseFuture = pipeline {
          Post(gzeroAddress, data)
        }
        Await.result(responseFuture, 24 hours)
        responseFuture.value.get.get
      }
      catch {
        case ca: ConnectionAttemptFailedException => {
          ca.printStackTrace()
          GzeroResult("", JsObject("message" -> "connection failed".toJson), JsObject("error" -> "true".toJson))
        }
        case te: TimeoutException => {
          te.printStackTrace()
          GzeroResult("", JsObject("message" -> s"timeout sending data $data".toJson), JsObject("error" -> "true".toJson))
        }
        case e: Exception => {
          e.printStackTrace()
          GzeroResult("", JsObject("message" -> s"an unknown error occurred $data".toJson), JsObject("error" -> "true".toJson))
        }
      }
    res
  }
}
