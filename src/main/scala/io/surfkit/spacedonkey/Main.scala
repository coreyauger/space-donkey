package io.surfkit.spacedonkey

import java.io.File

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import io.surfkit.spacedonkey.flows._
import org.joda.time.DateTime

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json.Json

import scala.concurrent.Await

object Main extends App{

  override def main(args: Array[String]) {

    val decider: Supervision.Decider = {
      case _ => Supervision.Resume
    }
    implicit val system: ActorSystem = ActorSystem()
    implicit val materializer = ActorMaterializer(ActorMaterializerSettings(system).withSupervisionStrategy(decider))

    try {
      val api = new QuadrigacxApi(new File("/home/suroot/.quadrigacx.conf"))
      import Quadrigacx._

      // public tests ...
      val sx = api.recentTrades(Markets.btc_cad)
      val s =  Await.result(sx, 10 seconds)
      println(s"trades: ${s}")

      val ox = api.openOrders(Markets.btc_cad)
      val o =  Await.result(ox, 10 seconds)
      println(s"order book: ${o}")


      // private ...
      val pox = api.orders(None)
      val po =  Await.result(pox, 10 seconds)
      println(s"orders: ${po}")

      val ticker = QuadrigacxOneMinuteTicker(Markets.btc_cad)
      ticker.json.runForeach(i => i.foreach(x => println(s"btc_cad: ${x}")) )(materializer)
/*

      val json =
        """
          |{"quotes":[{"symbol":"BABA","symbolId":7422546,"tier":"","bidPrice":null,"bidSize":0,"askPrice":null,"askSize":0,"lastTradePriceTrHrs":null,"lastTradePrice":null,"lastTradeSize":0,"lastTradeTick":null,"lastTradeTime":null,"volume":0,"openPrice":null,"highPrice":null,"lowPrice":null,"delay":0,"isHalted":false,"high52w":null,"low52w":null,"VWAP":null}]}
        """.stripMargin

      val test = Json.parse(json).as[Questrade.Quotes]
      println(s"test: ${test}")

      val fx = api.accounts()
      val f= Await.result(fx, 10 seconds)
      println(s"fx: ${f}")

      val account = f.accounts.head



      val end = DateTime.now.secondOfMinute().setCopy(0)
      val nowMinus1 = end.plusMinutes(-2)
      val cx = api.candles(s.symbols.head.symbolId, nowMinus1, end, Questrade.Interval.OneMinute)
      val c =  Await.result(cx, 10 seconds)
      println(s"cx: ${c}")

      val ticker = QuestradeOneMinuteTicker(api.getCreds _, s.symbols.head.symbolId)
      ticker.json.runForeach(i => i.foreach(x => println(s"meep: ${x}")) )(materializer)
*/



      Thread.currentThread.join()
    }catch{
      case t:Throwable =>
        t.printStackTrace()
    }

  }

}
