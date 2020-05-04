package coinyser.stream

import cats.effect.{ExitCode, IO, IOApp}
import coinyser.stream.Producer._
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}

import scala.collection.JavaConversions._

object ProducerApp extends IOApp {
  val topic = "transactions"

  val props = Map(
    "bootstrap.servers" -> "localhost:9092",
    "key.serializer" ->
      "org.apache.kafka.common.serialization.IntegerSerializer",
    "value.serializer" ->
      "org.apache.kafka.common.serialization.StringSerializer")

  def run(args: List[String]): IO[ExitCode] = {
    val kafkaProducer = new KafkaProducer[Int, String](props)

    subscribe { wsTx =>
      val tx = convertWsTransaction(deserializeWebsocketTransaction(wsTx))
      val jsonTx = serializeTransaction(tx)
      kafkaProducer.send(new ProducerRecord(topic, tx.tid, jsonTx))
    }.flatMap(_ => IO.never)
  }
}
