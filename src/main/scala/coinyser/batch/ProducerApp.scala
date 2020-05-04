package coinyser.batch

import java.net.{URI, URL}

import cats.effect.{ExitCode, IO, IOApp}
import coinyser.batch.Producer.{httpToDomainTransactions, jsonToHttpTransactions}
import coinyser.AppContext
import coinyser.data.Transaction
import com.typesafe.scalalogging.StrictLogging
import org.apache.spark.sql.{Dataset, SparkSession}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.io.Source

class BatchProducerApp extends IOApp with StrictLogging {

  implicit val executor: ExecutionContextExecutor = ExecutionContext.fromExecutor(
    new java.util.concurrent.ForkJoinPool(2))
  implicit val spark: SparkSession =
    SparkSession.builder.master("local[*]").getOrCreate()
  implicit val appContext: AppContext = new AppContext(new URI("./data/transactions"))

  def bitstampUrl(timeParam: String): URL =
    new URL(f"https://www.bitstamp.net/api/v2/transactions/btcusd?time=$timeParam%s")

  def transactionsIO(timeParam: String): IO[Dataset[Transaction]] = {
    val url = bitstampUrl(timeParam)
    val jsonIO = IO {
      logger.info(s"calling $url")
      Source.fromURL(url)
    }
    jsonIO.bracket {
      use => IO.pure { httpToDomainTransactions(jsonToHttpTransactions(use.mkString)) }
    } { release =>
      IO { release.close() }
    }
  }

  val initialJsonTxs: IO[Dataset[Transaction]] = transactionsIO("day")
  val nextJsonTxs: IO[Dataset[Transaction]] = transactionsIO("hour")

  def run(args: List[String]): IO[ExitCode] =
    Producer.processRepeatedly(initialJsonTxs, nextJsonTxs).map(_
    => ExitCode.Success)
}

object BatchProducerAppSpark extends BatchProducerApp
