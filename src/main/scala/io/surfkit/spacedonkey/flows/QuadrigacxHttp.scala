package io.surfkit.spacedonkey.flows

import java.util.UUID
import akka.stream.{KillSwitches, Materializer}
import akka.NotUsed
import akka.actor.{ActorSystem, Cancellable}
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model._
import akka.stream.scaladsl.Source
import io.surfkit.spacedonkey.utils.EncoderUtil
import org.joda.time.DateTime
import play.api.libs.json.Json
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

class QuadrigacxPoller(path: String, interval: FiniteDuration, fuzz: Double, alignMinute: Boolean = true)(implicit system: ActorSystem, materializer: Materializer) {
  import scala.concurrent.duration._

  def request: akka.http.scaladsl.model.HttpRequest = {
    val url = s"https://api.quadrigacx.com/${path}"
    println(s"curl -XGET '${url}'")
    RequestBuilding.Get(Uri(url))
  }
  val initialDelay =
    if(alignMinute)(60.0-DateTime.now.getSecondOfMinute.toDouble) + (Math.random() * fuzz + 1.0)    // set to the end of the minute plus some fuzzy
    else 0
  val source: Source[() => HttpRequest, Cancellable] = Source.tick(initialDelay.seconds, interval, request _)
  val sharedKillSwitch = KillSwitches.shared(UUID.randomUUID().toString)

  val sourceWithDest: Source[Try[HttpResponse], Cancellable] =
    source.map(req ⇒ (req(), NotUsed)).via(Http().superPool[NotUsed]()).map(_._1)

  def apply(): Source[Try[HttpResponse], Cancellable] = sourceWithDest.via(sharedKillSwitch.flow)
  def shutdown = sharedKillSwitch.shutdown()
}

class QuadrigacxSignedRequester(clientId: String, apiKey: String, apiSecret: String)(implicit system: ActorSystem, materializer: Materializer){
  import system.dispatcher
  import Quadrigacx._

  val api = "https://api.quadrigacx.com/private/"

  def get(op: Quadrigacx.Private) = {
    val nonce = DateTime.now().getMillis
    val sig = EncoderUtil.encodeSHA512( s"${nonce}${apiKey}${clientId}", apiSecret)
    val (json, url) = op match{
      case x: Quadrigacx.Orders =>
        (Json.stringify(Quadrigacx.OrdersRequestWrites.writes(Quadrigacx.OrdersRequest(x.book.map(_.toString), apiKey, nonce, sig))),s"${api}/orders")
      case x: Quadrigacx.BuyOrder =>
        (Json.stringify(Quadrigacx.BuyOrderRequestWrites.writes(Quadrigacx.BuyOrderRequest(x.market.major, x.market.minor, x.amount, x.rate, apiKey, nonce, sig))),s"${api}/buy")
      case x: Quadrigacx.SellOrder =>
        (Json.stringify(Quadrigacx.SellOrderRequestWrites.writes(Quadrigacx.SellOrderRequest(x.market.major, x.market.minor, x.amount, x.rate, apiKey, nonce, sig))),s"${api}/sell")
      case x: Quadrigacx.CancelOrder =>
        (Json.stringify(Quadrigacx.CancelOrderRequestWrites.writes(Quadrigacx.CancelOrderRequest(x.id, apiKey, nonce, sig))),s"${api}/cancel")
    }
    val jsonEntity = HttpEntity(ContentTypes.`application/json`, json)
    println(s"curl -XPOST '${url}' -d '${json}'")
    Http().singleRequest(HttpRequest(method = HttpMethods.POST, uri = url, entity = jsonEntity))
  }
}
