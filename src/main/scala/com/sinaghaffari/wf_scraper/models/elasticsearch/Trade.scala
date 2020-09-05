package com.sinaghaffari.wf_scraper.models.elasticsearch

import java.time.LocalDate

import com.sinaghaffari.wf_scraper.models.wealthfront.Transaction.WithTrade
import com.sinaghaffari.wf_scraper.models.wealthfront.{Account, Transaction}
import play.api.libs.json.{Json, OWrites}

case class Trade(symbol: String, account_id: String, transaction_id: String, share_price: Option[BigDecimal],
                 shares: Option[BigDecimal],
                 `type`: String, value: BigDecimal, `@timestamp`: LocalDate)

object Trade {
  implicit val trade: OWrites[Trade] = Json.writes[Trade]

  def fromWealthfrontTransactionMap(wealthfrontTransactionMap: Map[Account, Seq[Transaction]]): Seq[Trade] =
    wealthfrontTransactionMap
      .toSeq.flatMap(x => x._2.map((x._1, _)))
      .filter {
        case (_, _: WithTrade) => true
        case _ => false
      }
      .map(x => (x._1, Trade.fromWealthfrontTransaction(x._1.accountId, x._2.asInstanceOf[WithTrade])))
      .flatMap(_._2)

  def fromWealthfrontTransaction(account_id: String, transaction: WithTrade): Seq[Trade] =
    transaction.trades.map(wft =>
      Trade(wft.symbol, account_id, transaction.id, wft.share_price, wft.shares, wft.`type`, wft.value, wft.date)
    )
}