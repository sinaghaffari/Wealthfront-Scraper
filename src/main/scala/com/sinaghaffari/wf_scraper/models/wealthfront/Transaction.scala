package com.sinaghaffari.wf_scraper.models.wealthfront

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime, YearMonth}

import play.api.libs.functional.FunctionalBuilder
import play.api.libs.functional.syntax._
import play.api.libs.json._

sealed trait Transaction {
  val id: String
  val created_at: LocalDateTime
  val `type`: String
  val class_type: String
}

object Transaction {
  implicit val dividendReads: Reads[Dividend] = ((__ \ "symbol").read[String] and
    (__ \ "ex_date").read[String].map(LocalDate.parse(_, DateTimeFormatter.ofPattern("uuuuMMdd"))) and
    (__ \ "amount").read[String].map(_.toSeq.filterNot("$,".contains(_)).mkString("")).map(BigDecimal.apply) and
    (__ \ "amount_per_share").read[String].map(_.toSeq.filterNot("$,".contains(_)).mkString("")).map(BigDecimal
      .apply)) (Dividend.apply _)
  implicit val tradeReads: Reads[Trade] = ((__ \ "symbol").read[String] and
    (__ \ "date").read[String].map(LocalDate.parse(_, DateTimeFormatter.ofPattern("MM/dd/uuuu"))) and
    parseNullableMoney(__ \ "share_price") and
    (__ \ "shares").readNullable[BigDecimal] and
    (__ \ "type").read[String] and
    parseMoney(__ \ "value")) (Trade.apply _)

  val commonReads: FunctionalBuilder[Reads]#CanBuild2[String, LocalDateTime] =
    (__ \ "id").read[Long].map(_.toString) and
      (__ \ "created_at").read[String].map(LocalDateTime.parse(_, DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss" +
        ".SSS")))
  val amountReads: Reads[BigDecimal] = parseMoney(__ \ "amount")
  val optionalAmountReads: Reads[Option[BigDecimal]] = parseNullableMoney(__ \ "amount")
  val tradesReads: Reads[Seq[Trade]] = (__ \ "trades").read[Seq[Trade]]

  val dividendTransactionReads: Reads[Transaction] = (__ \ "class_type").read[String].filter(_ == DividendTransaction
    .class_type)
    .andKeep(
      (commonReads and
        amountReads and
        (__ \ "dividends").read[Seq[Dividend]] and
        parseMoney(__ \ "total_dividends")) (DividendTransaction.apply _)
    )
  val indexOptimizationTransactionReads: Reads[Transaction] = (__ \ "class_type").read[String].filter(_ ==
    IndexOptimizationTransaction.class_type)
    .andKeep(
      (commonReads and
        (__ \ "amount").readWithDefault[String]("0").map(_.filterNot("$,".contains(_))).map(BigDecimal.apply) and
        (__ \ "harvested_amount").readWithDefault[BigDecimal](BigDecimal(0)) and
        tradesReads) (IndexOptimizationTransaction.apply _)
    )
  val otherInvestmentTransactionReads: Reads[Transaction] = (__ \ "class_type").read[String].filter(_ ==
    OtherInvestmentTransaction.class_type)
    .andKeep(
      (commonReads and
        amountReads and
        tradesReads) (OtherInvestmentTransaction.apply _)
    )
  val depositInvestmentTransactionReads: Reads[Transaction] = (__ \ "class_type").read[String].filter(_ ==
    DepositInvestmentTransaction.class_type)
    .andKeep(
      (commonReads and
        amountReads and
        tradesReads and
        parseMoney(__ \ "remaining_cash_balance")) (DepositInvestmentTransaction.apply _)
    )
  val depositIndexInvestmentTransactionReads: Reads[Transaction] = (__ \ "class_type").read[String].filter(_ ==
    DepositIndexInvestmentTransaction.class_type)
    .andKeep(
      (commonReads and
        amountReads and
        tradesReads) (DepositIndexInvestmentTransaction.apply _)
    )
  val depositTransactionReads: Reads[Transaction] = (__ \ "class_type").read[String].filter(_ == DepositTransaction
    .class_type)
    .andKeep(
      (commonReads and
        amountReads) (DepositTransaction.apply _)
    )
  val dividendInvestmentTransactionReads: Reads[Transaction] = (__ \ "class_type").read[String].filter(_ ==
    DividendInvestmentTransaction.class_type)
    .andKeep(
      (commonReads and
        amountReads and
        tradesReads) (DividendInvestmentTransaction.apply _)
    )
  val feeTransactionReads: Reads[Transaction] = (__ \ "class_type").read[String].filter(_ == FeeTransaction.class_type)
    .andKeep(
      (commonReads and
        amountReads and
        (__ \ "period").read[String].map(YearMonth.parse(_, DateTimeFormatter.ofPattern("MM/uu"))) and
        parseMoney(__ \ "total_fee") and
        parseMoney(__ \ "amount_waived") and
        parseMoney(__ \ "your_fee") and
        parseMoney(__ \ "waiver_amount")) (FeeTransaction.apply _)
    )
  val harvestTransactionReads: Reads[Transaction] = (__ \ "class_type").read[String].filter(_ == HarvestTransaction
    .class_type)
    .andKeep(
      (commonReads and
        amountReads and
        tradesReads) (HarvestTransaction.apply _)
    )
  val liquidationTransactionReads: Reads[Transaction] = (__ \ "class_type").read[String].filter(_ ==
    LiquidationTransaction.class_type)
    .andKeep(
      (commonReads and
        amountReads and
        tradesReads) (LiquidationTransaction.apply _)
    )
  val driftRebalanceTransactionReads: Reads[Transaction] = (__ \ "class_type").read[String].filter(_ ==
    DriftRebalanceTransaction.class_type)
    .andKeep(
      (commonReads and
        optionalAmountReads and
        tradesReads) (DriftRebalanceTransaction.apply _)
    )
  val sltlhConversionTransactionReads: Reads[Transaction] = (__ \ "class_type").read[String].filter(_ ==
    SltlhConversionTransaction.class_type)
    .andKeep(
      (commonReads and
        tradesReads and
        (__ \ "conversion_type").read[String]) (SltlhConversionTransaction.apply _)
    )
  val initialDepositIndexInvestmentReads: Reads[Transaction] = (__ \ "class_type").read[String].filter(_ ==
    InitialDepositIndexInvestmentTransaction.class_type)
    .andKeep(
      (commonReads and
        amountReads and
        tradesReads) (InitialDepositIndexInvestmentTransaction.apply _)
    )
  val crossAccountTransferInTransactionReads: Reads[Transaction] = (__ \ "class_type").read[String].filter(_ ==
    CrossAccountTransferInTransaction.class_type)
    .andKeep(
      (commonReads and
        amountReads and
        (__ \ "account_id").read[Long] and
        (__ \ "source_account_id").read[Long] and
        (__ \ "state").read[String] and
        (__ \ "target_account_id").read[Long] and
        (__ \ "transaction_id").read[Long]) (CrossAccountTransferInTransaction.apply _)
    )
  val profileChangedRebalaceTransactionReads: Reads[Transaction] = (__ \ "class_type").read[String].filter(_ ==
    ProfileChangedRebalaceTransaction.class_type)
    .andKeep(
      (commonReads and
        (__ \ "risk_before").read[BigDecimal] and
        (__ \ "risk_after").read[BigDecimal] and
        tradesReads) (ProfileChangedRebalaceTransaction.apply _)
    )
  implicit val transactionReads: Reads[Transaction] = dividendTransactionReads or
    indexOptimizationTransactionReads or
    otherInvestmentTransactionReads or
    depositInvestmentTransactionReads or
    depositIndexInvestmentTransactionReads or
    depositTransactionReads or
    dividendInvestmentTransactionReads or
    feeTransactionReads or
    harvestTransactionReads or
    liquidationTransactionReads or
    driftRebalanceTransactionReads or
    sltlhConversionTransactionReads or
    initialDepositIndexInvestmentReads or
    crossAccountTransferInTransactionReads or
    profileChangedRebalaceTransactionReads


  private def parseNullableMoney(money: JsPath): Reads[Option[BigDecimal]] = {
    money.readNullable[String].map(_.map(_.filterNot("$,".contains(_))).map(BigDecimal.apply))
  }

  private def parseMoney(money: JsPath): Reads[BigDecimal] = {
    money.read[String].map(_.filterNot("$,".contains(_))).map(BigDecimal.apply)
  }

  trait WithTrade {
    val id: String
    val trades: Seq[Trade]
  }

  trait InvestmentTransaction extends Transaction with WithTrade {
    override val `type`: String = "Investment"
  }

  case class DividendTransaction(id: String, created_at: LocalDateTime, amount: BigDecimal, dividends: Seq[Dividend],
                                 total_dividends: BigDecimal)
    extends Transaction {
    override val `type`: String = "Dividend Received"
    override val class_type: String = DividendTransaction.class_type
  }

  case class IndexOptimizationTransaction(id: String, created_at: LocalDateTime, amount: BigDecimal,
                                          harvested_amount: BigDecimal, trades: Seq[Trade])
    extends Transaction with WithTrade {
    override val `type`: String = "Stock-level Tax-Loss Harvesting"
    override val class_type: String = IndexOptimizationTransaction.class_type
  }

  case class OtherInvestmentTransaction(id: String, created_at: LocalDateTime, amount: BigDecimal, trades: Seq[Trade])
    extends InvestmentTransaction with WithTrade {
    override val class_type: String = OtherInvestmentTransaction.class_type
  }

  case class DepositInvestmentTransaction(id: String, created_at: LocalDateTime, amount: BigDecimal,
                                          trades: Seq[Trade], remaining_cash_balance: BigDecimal)
    extends InvestmentTransaction with WithTrade {
    override val class_type: String = DepositInvestmentTransaction.class_type
  }

  case class DepositIndexInvestmentTransaction(id: String, created_at: LocalDateTime, amount: BigDecimal,
                                               trades: Seq[Trade])
    extends Transaction with WithTrade {
    override val `type`: String = "Investment: Stock-level Tax-Loss Harvesting"
    override val class_type: String = "deposit-index-investment"
  }

  case class DepositTransaction(id: String, created_at: LocalDateTime, amount: BigDecimal) extends Transaction {
    override val `type`: String = "Deposit"
    override val class_type: String = "deposit"
  }

  case class DividendInvestmentTransaction(id: String, created_at: LocalDateTime, amount: BigDecimal,
                                           trades: Seq[Trade])
    extends Transaction with WithTrade {
    override val `type`: String = "Dividend Reinvesting"
    override val class_type: String = "dividend-investment"
  }

  case class FeeTransaction(id: String, created_at: LocalDateTime, amount: BigDecimal, period: YearMonth,
                            total_fee: BigDecimal, amount_waived: BigDecimal, your_fee: BigDecimal,
                            waiver_amount: BigDecimal)
    extends Transaction {
    override val `type`: String = "Advisory Fee"
    override val class_type: String = "fee"
  }

  case class HarvestTransaction(id: String, created_at: LocalDateTime, amount: BigDecimal, trades: Seq[Trade])
    extends Transaction with WithTrade {
    override val `type`: String = "Rebalancing"
    override val class_type: String = "harvest"
  }

  case class LiquidationTransaction(id: String, created_at: LocalDateTime, amount: BigDecimal, trades: Seq[Trade])
    extends Transaction with WithTrade {
    override val `type`: String = "Liquidation"
    override val class_type: String = "liquidation"
  }

  case class DriftRebalanceTransaction(id: String, created_at: LocalDateTime, amount: Option[BigDecimal],
                                       trades: Seq[Trade])
    extends Transaction with WithTrade {
    override val `type`: String = "Rebalance (Threshold Based)"
    override val class_type: String = DriftRebalanceTransaction.class_type
  }

  case class SltlhConversionTransaction(id: String, created_at: LocalDateTime, trades: Seq[Trade],
                                        conversion_type: String)
    extends Transaction with WithTrade {
    override val `type`: String = "Conversion: Risk Parity"
    override val class_type: String = SltlhConversionTransaction.class_type
  }

  case class InitialDepositIndexInvestmentTransaction(id: String, created_at: LocalDateTime, amount: BigDecimal,
                                                      trades: Seq[Trade])
    extends Transaction with WithTrade {
    override val `type`: String = "Investment: Stock-level Tax-Loss Harvesting"
    override val class_type: String = InitialDepositIndexInvestmentTransaction.class_type
  }

  case class CrossAccountTransferInTransaction(id: String, created_at: LocalDateTime, amount: BigDecimal,
                                               account_id: Long, source_account_id: Long, state: String,
                                               target_account_id: Long, transaction_id: Long)
    extends Transaction {
    override val `type`: String = "Transfer"
    override val class_type: String = CrossAccountTransferInTransaction.class_type
  }

  case class ProfileChangedRebalaceTransaction(id: String, created_at: LocalDateTime, risk_before: BigDecimal,
                                               risk_after: BigDecimal, trades: Seq[Trade])
    extends Transaction with WithTrade {
    override val `type`: String = "Rebalance (Profile Changed)"
    override val class_type: String = ProfileChangedRebalaceTransaction.class_type
  }

  case class Trade(symbol: String, date: LocalDate, share_price: Option[BigDecimal], shares: Option[BigDecimal],
                   `type`: String, value: BigDecimal)

  case class Dividend(symbol: String, ex_date: LocalDate, amount: BigDecimal, amount_per_share: BigDecimal)

  object DividendTransaction {
    val class_type: String = "dividend"
  }

  object IndexOptimizationTransaction {
    val class_type: String = "index-optimization"
  }

  object OtherInvestmentTransaction {
    val class_type: String = "other-investment"
  }

  object DepositInvestmentTransaction {
    val class_type: String = "deposit-investment"
  }

  object DepositIndexInvestmentTransaction {
    val class_type: String = "deposit-index-investment"
  }

  object DepositTransaction {
    val class_type: String = "deposit"
  }

  object DividendInvestmentTransaction {
    val class_type: String = "dividend-investment"
  }

  object FeeTransaction {
    val class_type: String = "fee"
  }

  object HarvestTransaction {
    val class_type: String = "harvest"
  }

  object LiquidationTransaction {
    val class_type: String = "liquidation"
  }

  object DriftRebalanceTransaction {
    val class_type: String = "drift-rebalance"
  }

  object SltlhConversionTransaction {
    val class_type: String = "sltlh-conversion"
  }

  object InitialDepositIndexInvestmentTransaction {
    val class_type: String = "initial-deposit-index-investment"
  }

  object CrossAccountTransferInTransaction {
    val class_type: String = "cross-account-transfer-in"
  }

  object ProfileChangedRebalaceTransaction {
    val class_type: String = "profile-changed-rebalance"
  }

}
