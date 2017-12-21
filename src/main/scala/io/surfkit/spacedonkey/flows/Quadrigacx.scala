package io.surfkit.spacedonkey.flows

import akka.actor.{ActorSystem, Cancellable}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import play.api.libs.json._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import java.io.File
import com.typesafe.config._


object Quadrigacx {
  sealed trait CX

  case class Market(major: String, minor: String) extends CX{
    override def toString: String = s"${major.toLowerCase}_${minor.toLowerCase}"
  }
  object Markets{
    val btc_cad = Market("btc", "cad")
    val btc_usd =  Market("btc", "usd")
    val eth_btc = Market("eth", "btc")
    val eth_cad = Market("eth", "cad")
    val ltc_cad = Market("ltc", "cad")
    val bch_cad = Market("bch", "cad")
    val btg_cad = Market("btg", "cad")
  }

  // eg: https://api.quadrigacx.com/public/orders?book=btc_cad
  case class Order(amount: String, rate: String, value: String, id: Option[String], `type`: Option[String]) extends CX
  implicit val OrderWrites = Json.writes[Order]
  implicit val OrderReads = Json.reads[Order]

  case class OrderBook(sell: Seq[Order], buy: Seq[Order]) extends CX
  implicit val OrderBookWrites = Json.writes[OrderBook]
  implicit val OrderBookReads = Json.reads[OrderBook]

  // eg: https://api.quadrigacx.com/public/trades?book=btc_cad
  case class Trade(amount: String, datetime: String, date: String, rate: String, value: String) extends CX
  implicit val TradeWrites = Json.writes[Trade]
  implicit val TradeReads = Json.reads[Trade]

  trait Private extends CX

  case class MyOrders(
                       btc_cad: Option[Seq[Order]],
                       btc_usd: Option[Seq[Order]],
                       eth_btc: Option[Seq[Order]],
                       eth_cad: Option[Seq[Order]],
                       ltc_cad: Option[Seq[Order]],
                       bch_cad: Option[Seq[Order]],
                       btg_cad: Option[Seq[Order]]
                     ) extends Private
  implicit val MyOrdersWrites = Json.writes[MyOrders]
  implicit val MyOrdersReads = Json.reads[MyOrders]

  trait Creds extends Private{
    def key: String
    def nonce: Long
    def signature: String
  }
  case object Balances extends Private
  case class BalancesRequest(key: String, nonce: Long, signature: String) extends Creds
  implicit val BalancesRequestWrites = Json.writes[BalancesRequest]
  implicit val BalancesRequestReads = Json.reads[BalancesRequest]

  case class Orders(book: Option[Market]) extends Private
  case class OrdersRequest(book: Option[String], key: String, nonce: Long, signature: String) extends Creds
  implicit val OrdersRequestWrites = Json.writes[OrdersRequest]
  implicit val OrdersRequestReads = Json.reads[OrdersRequest]


  case class CancelOrder(id: String) extends Private
  case class CancelOrderRequest(id: String, key: String, nonce: Long, signature: String) extends Creds
  implicit val CancelOrderRequestWrites = Json.writes[CancelOrderRequest]
  implicit val CancelOrderRequestReads = Json.reads[CancelOrderRequest]

  case class BuyOrder(market: Market, amount: String, rate: String) extends Private
  case class BuyOrderRequest(major: String, minor: String, amount: String, rate: String, key: String, nonce: Long, signature: String) extends Creds
  implicit val BuyOrderRequestWrites = Json.writes[BuyOrderRequest]
  implicit val BuyOrderRequestReads = Json.reads[BuyOrderRequest]

  case class SellOrder(market: Market, amount: String, rate: String) extends Private
  case class SellOrderRequest(major: String, minor: String, amount: String, rate: String, key: String, nonce: Long, signature: String) extends Creds
  implicit val SellOrderRequestWrites = Json.writes[SellOrderRequest]
  implicit val SellOrderRequestReads = Json.reads[SellOrderRequest]
}

class QuadrigacxTicker[T <: Quadrigacx.CX](market: Quadrigacx.Market, interval: FiniteDuration, fuzz: Double = 6.66)(implicit system: ActorSystem, materializer: Materializer, um: Reads[T]) extends QuadrigacxPoller(
  path = s"public/trades?book=${market}", interval = interval, fuzz = fuzz) with PlayJsonSupport{
  def json(): Source[Future[Seq[T]], Cancellable] = super.apply().map{
    case scala.util.Success(response) => Unmarshal(response.entity).to[Seq[T]]
    case scala.util.Failure(ex) => Future.failed(ex)
  }
}

case class QuadrigacxOneMinuteTicker(market: Quadrigacx.Market, fuzz: Double = 6.66)(implicit system: ActorSystem, materializer: Materializer, um: Reads[Quadrigacx.Trade])
  extends QuadrigacxTicker[Quadrigacx.Trade](market, 1 minute, fuzz)

case class QuadrigacxFiveMinuteTicker(market: Quadrigacx.Market, fuzz: Double = 6.66)(implicit system: ActorSystem, materializer: Materializer, um: Reads[Quadrigacx.Trade])
  extends QuadrigacxTicker[Quadrigacx.Trade](market, 5 minutes, fuzz)

case class QuadrigacxFifteenMinuteTicker(market: Quadrigacx.Market, fuzz: Double = 6.66)(implicit system: ActorSystem, materializer: Materializer, um: Reads[Quadrigacx.Trade])
  extends QuadrigacxTicker[Quadrigacx.Trade](market, 15 minutes, fuzz)

case class QuadrigacxThirtyMinuteTicker(market: Quadrigacx.Market, fuzz: Double = 6.66)(implicit system: ActorSystem, materializer: Materializer, um: Reads[Quadrigacx.Trade])
  extends QuadrigacxTicker[Quadrigacx.Trade](market, 30 minutes, fuzz)

case class QuadrigacxOneHourTicker(market: Quadrigacx.Market, fuzz: Double = 6.66)(implicit system: ActorSystem, materializer: Materializer, um: Reads[Quadrigacx.Trade])
  extends QuadrigacxTicker[Quadrigacx.Trade](market, 60 minutes, fuzz)


class QuadrigacxApi(creds: File)(implicit system: ActorSystem, materializer: Materializer, ex: ExecutionContext) extends PlayJsonSupport {

  val config = ConfigFactory.parseFile(creds)

  val apiKey = config.getString("Quadrigacx.apiKey")
  val apiSecret = config.getString("Quadrigacx.apiSecret")
  val clientId = config.getString("Quadrigacx.clientId")

  object httpApi extends QuadrigacxSignedRequester(clientId, apiKey, apiSecret)

  val apiUrl = "https://api.quadrigacx.com/"

  def unmarshal[T <: Quadrigacx.CX](response: HttpResponse)(implicit um: Reads[T]):Future[T] = Unmarshal(response.entity).to[T]
  def unmarshalSeq[T <: Quadrigacx.CX](response: HttpResponse)(implicit um: Reads[T]):Future[Seq[T]] = Unmarshal(response.entity).to[Seq[T]]

  // public api
  def recentTrades(market: Quadrigacx.Market)(implicit um: Reads[Quadrigacx.Trade]) =
    Http().singleRequest(HttpRequest(uri = s"${apiUrl}/public/trades?book=${market}")).flatMap(x => unmarshalSeq(x))

  def openOrders(market: Quadrigacx.Market)(implicit um: Reads[Quadrigacx.OrderBook]) =
    Http().singleRequest(HttpRequest(uri = s"${apiUrl}/public/orders?book=${market}")).flatMap(x => unmarshal(x))


  // private api
  def orders(book: Option[Quadrigacx.Market])(implicit um: Reads[Quadrigacx.MyOrders]) =
    httpApi.get(Quadrigacx.Orders(book)).flatMap(x => unmarshal(x) )

  def cancel(id: String)(implicit um: Reads[Quadrigacx.CX]) =
    httpApi.get(Quadrigacx.CancelOrder(id)).flatMap(x => unmarshal(x) )

  def buy(market: Quadrigacx.Market, amount: Double, rate: Double)(implicit um: Reads[Quadrigacx.CX]) =
    httpApi.get(Quadrigacx.BuyOrder(market, amount.toString, rate.toString)).flatMap(x => unmarshal(x) )

  def sell(market: Quadrigacx.Market, amount: Double, rate: Double)(implicit um: Reads[Quadrigacx.CX]) =
    httpApi.get(Quadrigacx.SellOrder(market, amount.toString, rate.toString)).flatMap(x => unmarshal(x) )
}