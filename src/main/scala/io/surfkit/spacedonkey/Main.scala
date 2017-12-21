package io.surfkit.spacedonkey

import java.io.File

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import io.surfkit.spacedonkey.flows._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

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

      Thread.currentThread.join()
    }catch{
      case t:Throwable =>
        t.printStackTrace()
    }

  }

}
