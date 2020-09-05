package com.sinaghaffari.wf_scraper.models.wealthfront

import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

case class Account(
                    accountDisplayName: String,
                    accountId: String,
                    accountType: String,
                    accountTypeDisplayName: String,
                    value: BigDecimal,
                  )

object Account {
  implicit val accountReads: Reads[Account] = (
    (JsPath \ "accountDisplayName").read[String] and
      (JsPath \ "accountId").read[Int].map(_.toString) and
      (JsPath \ "accountType").read[String] and
      (JsPath \ "accountTypeDisplayName").read[String] and
      (JsPath \ "accountValueSummary" \ "totalValue").read[BigDecimal]
    ) (Account.apply _)
}
