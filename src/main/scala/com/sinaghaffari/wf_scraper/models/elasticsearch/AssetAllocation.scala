package com.sinaghaffari.wf_scraper.models.elasticsearch

import java.time.LocalDateTime

import com.sinaghaffari.wf_scraper.models.elasticsearch.AssetAllocation.AssetAllocationType.AssetAllocationType
import com.sinaghaffari.wf_scraper.models.wealthfront
import com.sinaghaffari.wf_scraper.models.wealthfront.Account
import play.api.libs.json.{Json, OWrites}

case class AssetAllocation(name: String, `type`: AssetAllocationType, account_id: String, current: BigDecimal,
                           cash: BigDecimal, target: BigDecimal, market_value: BigDecimal, cost_basis: BigDecimal,
                           `return`: Option[BigDecimal], ytdReturn: Option[BigDecimal], accruedDividend: BigDecimal,
                           `@timestamp`: LocalDateTime)

object AssetAllocation {
  implicit val assetAllocationWrites: OWrites[AssetAllocation] = Json.writes[AssetAllocation]

  def fromWealthfrontAssetAllocationMap(wealthfrontAssetAllocationMap: Map[Account, Seq[wealthfront
  .AssetAllocation]], timeNow: LocalDateTime): Seq[AssetAllocation] = wealthfrontAssetAllocationMap
    .toSeq
    .flatMap(x => x._2.map((x._1, _)))
    .map(x => fromWealthfrontAssetAllocation(x._1.accountId, x._2, timeNow))

  def fromWealthfrontAssetAllocation(account_id: String, assetAllocation: wealthfront.AssetAllocation,
                                     timeNow: LocalDateTime): AssetAllocation = AssetAllocation(
    assetAllocation.name,
    AssetAllocationType.withName(assetAllocation.`type`),
    account_id,
    assetAllocation.current,
    assetAllocation.cash,
    assetAllocation.target,
    assetAllocation.marketValue,
    assetAllocation.costBasis,
    assetAllocation.`return`,
    assetAllocation.ytdReturn,
    assetAllocation.accruedDividend,
    timeNow
  )

  object AssetAllocationType extends Enumeration {
    type AssetAllocationType = Value
    val US_STOCKS, INTL_DEVELOPED, INTL_EMERGING, DIV_STOCKS, RISK_PARITY, MUNI_BONDS, CASH = Value
  }

}