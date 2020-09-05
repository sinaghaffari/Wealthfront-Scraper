package com.sinaghaffari.wf_scraper.models.elasticsearch

import java.time.LocalDateTime

import com.sinaghaffari.wf_scraper.models.alpha_vantage.AssetOverview
import com.sinaghaffari.wf_scraper.models.elasticsearch.AssetAllocation.AssetAllocationType
import com.sinaghaffari.wf_scraper.models.elasticsearch.AssetAllocation.AssetAllocationType.AssetAllocationType
import com.sinaghaffari.wf_scraper.models.wealthfront
import com.sinaghaffari.wf_scraper.models.wealthfront.{Account, AssetAllocation}
import play.api.libs.functional.syntax._
import play.api.libs.json.{Json, OWrites, __}
import play.api.libs.ws.ahc.StandaloneAhcWSClient

import scala.concurrent.{ExecutionContext, Future}

sealed trait Asset {
  val symbol: String
  val account_id: String
  val asset_allocation_type: AssetAllocationType
  val shares: Option[BigDecimal]
  val market_value: BigDecimal
  val cost_basis: Option[BigDecimal]
  val expense_ratio: Option[BigDecimal]
  val asset_type: String
  val `@timestamp`: LocalDateTime
}

object Asset {
  def fromWealthfrontAssetAllocationMap(assetAllocations: Map[Account, Seq[AssetAllocation]], timeNow: LocalDateTime)
  : (Seq[Etf], Seq[Cash]) = {
    val t = assetAllocations.toSeq
      .flatMap(x => x._2.map((x._1, _, timeNow)))
      .map(x => Asset.fromWealthfrontAssetAllocation(x._1.accountId, x._2, x._3))
      .unzip
    (t._1.flatten, t._2.flatten)
  }

  def fromWealthfrontAssetAllocation(account_id: String, assetAllocation: wealthfront.AssetAllocation,
                                     timestamp: LocalDateTime): (Seq[Etf], Option[Cash]) = (
    assetAllocation.etfs.map(etf => Etf(etf.name, account_id, AssetAllocationType.withName(assetAllocation.`type`),
      Some(etf.shares), etf.marketValue, Some(etf.costBasis), Some(etf.expenseRatio), timestamp)),
    assetAllocation.`type` match {
      case "CASH" => Some(Cash(account_id, assetAllocation.cash, timestamp))
      case _ => None
    }
  )

  def fromWealthfrontStockMap(
                               directInvestmentAssetAllocations: Map[Account, Seq[wealthfront.Stock]],
                               timeNow: LocalDateTime
                             )
                             (
                               implicit ws: StandaloneAhcWSClient,
                               assetOverviewManager: AssetOverview.AssetOverviewManager,
                               executionContext: ExecutionContext
                             ): Future[Right[Nothing, Seq[Stock]]] = Future.sequence(directInvestmentAssetAllocations
    .toSeq
    .flatMap(x => x._2.map((x._1, _, timeNow)))
    .map(x => Asset.fromWealthfrontStock(x._1.accountId, x._2, x._3))).map(Right.apply)

  def fromWealthfrontStock(account_id: String, stock: wealthfront.Stock, timestamp: LocalDateTime)
                          (
                            implicit ws: StandaloneAhcWSClient,
                            assetOverviewManager: AssetOverview.AssetOverviewManager,
                            executionContext: ExecutionContext
                          ): Future[Stock] = {
    assetOverviewManager.getAssetOverview(stock.name).map(
      Stock(stock.name, account_id, Some(stock.shares), stock.marketValue, Some(stock.costBasis), _, timestamp)
    )
  }

  sealed case class Stock(
                           symbol: String,
                           account_id: String,
                           shares: Some[BigDecimal],
                           market_value: BigDecimal,
                           cost_basis: Some[BigDecimal],
                           overview: AssetOverview,
                           `@timestamp`: LocalDateTime
                         ) extends Asset {
    override val asset_allocation_type: AssetAllocationType = AssetAllocationType.US_STOCKS
    override val expense_ratio: Option[BigDecimal] = None
    override val asset_type: String = "STOCK"
  }

  sealed case class Cash(
                          account_id: String,
                          market_value: BigDecimal,
                          `@timestamp`: LocalDateTime
                        ) extends Asset {
    override val symbol: String = "USD"
    override val asset_allocation_type: AssetAllocationType = AssetAllocationType.CASH
    override val expense_ratio: Option[BigDecimal] = None
    override val shares: Option[BigDecimal] = None
    override val asset_type: String = "CASH"
    override val cost_basis: Some[BigDecimal] = Some(market_value)
  }

  sealed case class Etf(
                         symbol: String,
                         account_id: String,
                         asset_allocation_type: AssetAllocationType,
                         shares: Some[BigDecimal],
                         market_value: BigDecimal,
                         cost_basis: Option[BigDecimal],
                         expense_ratio: Option[BigDecimal],
                         `@timestamp`: LocalDateTime
                       ) extends Asset {
    override val asset_type: String = "ETF"
  }

  implicit val etfWrites: OWrites[Etf] = ((__ \ "symbol").write[String] and
    (__ \ "account_id").write[String] and
    (__ \ "asset_allocation_type").write[AssetAllocationType] and
    (__ \ "shares").writeNullable[BigDecimal] and
    (__ \ "market_value").write[BigDecimal] and
    (__ \ "cost_basis").writeNullable[BigDecimal] and
    (__ \ "expense_ratio").writeNullable[BigDecimal] and
    (__ \ "@timestamp").write[LocalDateTime]) (unlift(Etf.unapply)).transform(_ ++ Json.obj("asset_type" -> "ETF"))
  implicit val stockWrites: OWrites[Stock] = (
    (__ \ "symbol").write[String] and
      (__ \ "account_id").write[String] and
      (__ \ "shares").writeNullable[BigDecimal] and
      (__ \ "market_value").write[BigDecimal] and
      (__ \ "cost_basis").writeNullable[BigDecimal] and
      (__ \ "overview").write[AssetOverview] and
      (__ \ "@timestamp").write[LocalDateTime]
    ) (unlift(Stock.unapply)).transform(_ ++ Json.obj(
    "asset_allocation_type" -> AssetAllocationType.US_STOCKS,
    "asset_type" -> "STOCK"
  ))
  implicit val cashWrites: OWrites[Cash] = (
    (__ \ "account_id").write[String] and
      (__ \ "market_value").write[BigDecimal] and
      (__ \ "@timestamp").write[LocalDateTime]
    ) (unlift(Cash.unapply)).transform(x => x ++ Json.obj(
    "symbol" -> "USD",
    "asset_allocation_type" -> AssetAllocationType.CASH,
    "asset_type" -> "CASH",
    "cost_basis" -> (x \ "market_value").get
  ))

}

