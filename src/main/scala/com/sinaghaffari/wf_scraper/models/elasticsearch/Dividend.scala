package com.sinaghaffari.wf_scraper.models.elasticsearch

import java.time.LocalDate

import com.sinaghaffari.wf_scraper.models.wealthfront.Transaction.DividendTransaction
import com.sinaghaffari.wf_scraper.models.wealthfront.{Account, Transaction}
import play.api.libs.json.{Json, OWrites}

case class Dividend(symbol: String, account_id: String, transaction_id: String, amount: BigDecimal,
                    amount_per_share: BigDecimal, `@timestamp`: LocalDate)

object Dividend {
  val dividendWrites: OWrites[Dividend] = Json.writes[Dividend]

  def fromWealthfrontTransactionMap(wealthfrontTransactionMap: Map[Account, Seq[Transaction]]): Seq[Dividend] =
    wealthfrontTransactionMap
      .toSeq.flatMap(x => x._2.map((x._1, _)))
      .filter {
        case (_, _: DividendTransaction) => true
        case _ => false
      }
      .map(x => (x._1, Dividend.fromWealthfrontDividendTransaction(x._1.accountId, x
        ._2.asInstanceOf[DividendTransaction])))
      .flatMap(_._2)

  def fromWealthfrontDividendTransaction(account_id: String, dividendTransaction: DividendTransaction): Seq[Dividend]
  = dividendTransaction
    .dividends
    .map(x => Dividend(x.symbol, account_id, dividendTransaction.id, x.amount, x.amount_per_share, x.ex_date))
}
