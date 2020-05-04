package coinyser.data

case class BitmapWebsocketTransaction(amount: Double,
                                      buy_order_id: Long,
                                      sell_order_id: Long,
                                      amount_str: String,
                                      price_str: String,
                                      timestamp: String,
                                      price: Double,
                                      `type`: Int,
                                      id: Int)
