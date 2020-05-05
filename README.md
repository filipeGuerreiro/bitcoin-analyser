# Bitcoin transaction history

A data pipeline to fetch, store, and analyze bitcoin transaction data.

The source of data comes from the cryptocurrency exchange platform, bitmap.

A cryptocurrency exchange allows customers to trade digital currencies, such as bitcoin, for fiat currencies, such as the US dollar. 

The transaction data allows to track the price and quantity exchanged at a certain point in time.

To gather data, we use the popular Lambda architecture, which combines real-time analytics with batch processing.

For batch processing, Spark runs periodically (1-hour increments) and fetches data from the exchange's REST API, storing the data in the Parquet columnar format.

For real-time processing, Kafka uses Bitmap's Websocket API to fetch the data, which is then unioned with the batch data for a complete view of the transaction history.  

The output is a history of bitcoin/USD transactions, which can be queried and visualized interactively with Apache Zeppelin.

## Setup

The environment used to build the application is the following:
```
- Java 8
- Scala 2.11
- Spark 2.4.5
- Kafka 2.4.1
- Zeppelin 0.9.0 (optional - for data visualization)
```

Optionally, a docker environment can be automatically set up with:
```
docker-compose up -d
```
When you are done with it, just do:
```
docker-compose down
```

To build the executable, just run:
```
sbt assembly
```

