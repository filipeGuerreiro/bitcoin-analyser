package coinyser.stream

case class KafkaConfig(bootStrapServers: String,
                       transactionsTopic: String)
