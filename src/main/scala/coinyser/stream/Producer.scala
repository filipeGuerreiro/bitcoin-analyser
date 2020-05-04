package coinyser.stream

import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.TimeZone

import cats.effect.IO
import coinyser.data.{BitmapWebsocketTransaction, Transaction}
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.pusher.client.{Client, Pusher}
import com.pusher.client.channel.SubscriptionEventListener
import com.typesafe.scalalogging.StrictLogging

object Producer extends StrictLogging {

  implicit val client: Client = new Pusher("**SECRET**")

  def subscribe(onTradeReceived: String => Unit)(implicit pusher: Client): IO[Unit] =
    for {
      _ <- IO(pusher.connect())
      channel <- IO(pusher.subscribe("live_trades"))

      _ <- IO(channel.bind("trade", new SubscriptionEventListener() {
        override def onEvent(channel: String, event: String, data: String): Unit = {
          logger.info(s"Received event: $event with data: $data")
          onTradeReceived(data)
        }
      }))
    } yield ()


  val mapper: ObjectMapper = {
    val m = new ObjectMapper()
    m.registerModule(DefaultScalaModule)
    val sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    // Very important: the storage must be in UTC
    sdf.setTimeZone(TimeZone.getTimeZone("UTC"))
    m.setDateFormat(sdf)
  }

  def deserializeWebsocketTransaction(s: String): BitmapWebsocketTransaction =
    mapper.readValue(s, classOf[BitmapWebsocketTransaction])

  def convertWsTransaction(wsTx: BitmapWebsocketTransaction): Transaction =
    Transaction(
      timestamp = new Timestamp(wsTx.timestamp.toLong * 1000), tid = wsTx.id,
      price = wsTx.price, sell = wsTx.`type` == 1, amount = wsTx.amount)

  def serializeTransaction(tx: Transaction): String =
    mapper.writeValueAsString(tx)

}
