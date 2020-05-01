# bitcoin-analyser

A data pipeline to fetch, store, and analyze bitcoin transaction data.

Calls the bitmap currency exchange REST API to fetch transactions.
A cryptocurrency exchange allows customers to trade digital currencies, such as bitcoin, for fiat currencies, such as the US dollar. 
The transaction data allows to track the price and quantity exchanged at a certain point in time.

The application processes new data in batch, stored in the Parquet columnar data format. A common format for big data analytics.
The output is a history of bitcoin/USD transactions, which can be queried interactively with Apache Spark or Apache Zeppelin (the latter comes with data visualization capabilities).
