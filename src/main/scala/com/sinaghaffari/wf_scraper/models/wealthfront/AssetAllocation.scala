package com.sinaghaffari.wf_scraper.models.wealthfront

import com.sinaghaffari.wf_scraper.models.wealthfront.AssetAllocation.Etf
import play.api.libs.functional.syntax._
import play.api.libs.json.{Reads, __}

case class AssetAllocation(
                            name: String,
                            `type`: String,
                            etfs: Seq[Etf],
                            current: BigDecimal,
                            cash: BigDecimal,
                            target: BigDecimal,
                            marketValue: BigDecimal,
                            costBasis: BigDecimal,
                            `return`: Option[BigDecimal],
                            annualizedReturn: Option[BigDecimal],
                            ytdReturn: Option[BigDecimal],
                            accruedDividend: BigDecimal
                          )

object AssetAllocation {
  implicit val etfReads: Reads[Etf] = ((__ \ "name").read[String] and
    (__ \ "shares").read[BigDecimal] and
    (__ \ "marketValue").read[String].map(_.toSeq.filterNot("$,".contains(_)).mkString("")).map(BigDecimal.apply) and
    (__ \ "costBasis").read[String].map(_.toSeq.filterNot("$,".contains(_)).mkString("")).map(BigDecimal.apply) and
    (__ \ "displayName").read[String] and
    (__ \ "expenseRatio").read[BigDecimal]) (Etf.apply _)
  implicit val assetAllocationReads: Reads[AssetAllocation] = ((__ \ "name").read[String] and
    (__ \ "type").read[String] and
    (__ \ "etfs").read[Seq[Etf]] and
    (__ \ "current").read[BigDecimal] and
    (__ \ "cash").read[BigDecimal] and
    (__ \ "target").read[BigDecimal] and
    (__ \ "marketValue").read[String].map(_.toSeq.filterNot("$,".contains(_)).mkString("")).map(BigDecimal.apply) and
    (__ \ "costBasis").read[String].map(_.toSeq.filterNot("$,".contains(_)).mkString("")).map(BigDecimal.apply) and
    (__ \ "return").readNullable[BigDecimal] and
    (__ \ "annualizedReturn").readNullable[BigDecimal] and
    (__ \ "ytdReturn").readNullable[BigDecimal] and
    (__ \ "accruedDividend").read[BigDecimal]) (AssetAllocation.apply _)

  case class Etf(name: String, shares: BigDecimal, marketValue: BigDecimal, costBasis: BigDecimal,
                 displayName: String, expenseRatio: BigDecimal)

}
