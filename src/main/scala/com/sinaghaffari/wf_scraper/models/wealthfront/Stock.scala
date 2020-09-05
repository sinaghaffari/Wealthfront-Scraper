package com.sinaghaffari.wf_scraper.models.wealthfront

import play.api.libs.json.{Json, Reads}

case class Stock(name: String, shares: BigDecimal, marketValue: BigDecimal, costBasis: BigDecimal)

object Stock {
  implicit val stockReads: Reads[Stock] = Json.reads[Stock].map {
    case x@Stock("BRKB", _, _, _) => x.copy(name = "BRK-B")
    case x => x
  }
}
