package coinyser.batch

import java.net.URI
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

import cats.Monad
import cats.effect.{IO, Timer}
import coinyser.data.{BitmapTransaction, Transaction}
import coinyser.AppContext
import org.apache.spark.sql.functions.{explode, from_json, lit}
import org.apache.spark.sql.types._
import org.apache.spark.sql.{Dataset, SaveMode, SparkSession}

object Producer {

  def httpToDomainTransactions(ds: Dataset[BitmapTransaction]): Dataset[Transaction] = {
    import ds.sparkSession.implicits._
    val x = ds.select(
      $"date".cast(LongType).cast(TimestampType).as("timestamp"),
      $"date".cast(LongType).cast(TimestampType).
        cast(DateType).as("date"),
      $"tid".cast(IntegerType),
      $"price".cast(DoubleType),
      $"type".cast(BooleanType).as("sell"),
      $"amount".cast(DoubleType))
      .as[Transaction]
    x
  }

  def jsonToHttpTransactions(json: String)(implicit spark: SparkSession): Dataset[BitmapTransaction] = {
    import spark.implicits._
    val ds: Dataset[String] = Seq(json).toDS()
    val txSchema: StructType = Seq.empty[BitmapTransaction].toDS().schema
    val schema = ArrayType(txSchema)
    val arrayColumn = from_json($"value", schema)
    ds.select(explode(arrayColumn).alias("v"))
      .select("v.*")
      .as[BitmapTransaction]
  }

  def unsafeSave(transactions: Dataset[Transaction], path: URI): Unit =
    transactions
      .write
      .mode(SaveMode.Append)
      .partitionBy("date")
      .parquet(path.toString)

  def save(transactions: Dataset[Transaction], path: URI): IO[Unit] =
    IO(unsafeSave(transactions, path))

  import scala.concurrent.duration._
  val WaitTime: FiniteDuration = 59.minutes
  val ApiLag: FiniteDuration = 5.seconds

  def processOneBatch(fetchNextTransactions: IO[Dataset[Transaction]],
                      transactions: Dataset[Transaction],
                      saveStart: Instant,
                      saveEnd: Instant)(implicit context: AppContext
  ) : IO[(Dataset[Transaction], Instant, Instant)] = {
    import context._
    val transactionsToSave = filterTxs(transactions, saveStart, saveEnd)
    for {
      _ <- Producer.save(transactionsToSave,
        context.transactionStorePath)
      _ <- IO.sleep(WaitTime)
      beforeRead <- currentInstant
      end = beforeRead.minusSeconds(ApiLag.toSeconds)
      nextTransactions <- fetchNextTransactions
    } yield (nextTransactions, saveEnd, end)
  }

  def filterTxs(transactions: Dataset[Transaction],
                fromInstant: Instant, untilInstant: Instant): Dataset[Transaction] = {
    import transactions.sparkSession.implicits._
    transactions.filter(
      ($"timestamp" >= lit(fromInstant.getEpochSecond).cast(TimestampType)) &&
        ($"timestamp" < lit(untilInstant.getEpochSecond).cast(TimestampType)))
  }

  def currentInstant(implicit timer: Timer[IO]): IO[Instant] =
    timer.clock.realTime(TimeUnit.SECONDS) map Instant.ofEpochSecond

  def processRepeatedly(lastDayTxs: IO[Dataset[Transaction]],
                        lastHourTxs: IO[Dataset[Transaction]])
                       (implicit appContext: AppContext): IO[Unit] = {
    import appContext._
    import cats.implicits._
    for {
      beforeRead <- currentInstant
      firstEnd = beforeRead.minusSeconds(ApiLag.toSeconds)
      firstTxs <- lastDayTxs
      firstStart = firstEnd.truncatedTo(ChronoUnit.DAYS)
      _ <- Monad[IO].tailRecM((firstTxs, firstStart, firstEnd)) {
        case (txs, start, instant) =>
          processOneBatch(lastHourTxs, txs, start, instant).map(_.asLeft)
      }
    } yield ()
  }
}
