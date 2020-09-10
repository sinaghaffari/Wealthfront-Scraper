package com.sinaghaffari.wf_scraper

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.util.Base64

import akka.actor.ActorSystem
import com.j256.twofactorauth.TimeBasedOneTimePasswordUtil
import com.sinaghaffari.wf_scraper.models.alpha_vantage.AssetOverview
import com.sinaghaffari.wf_scraper.models.elasticsearch.Asset._
import com.sinaghaffari.wf_scraper.models.elasticsearch.{Asset, Dividend, Trade}
import com.sinaghaffari.wf_scraper.models.wealthfront.{Account, AssetAllocation, Stock, Transaction}
import com.sinaghaffari.wf_scraper.models.{LoginWSResponse, elasticsearch}
import com.sinaghaffari.wf_scraper.util.MonadicSimplifier.simplifyFutureEither
import com.typesafe.config.{Config, ConfigFactory}
import play.api.libs.json.{JsResultException, Json}
import play.api.libs.ws.DefaultBodyWritables._
import play.api.libs.ws.JsonBodyWritables._
import play.api.libs.ws.ahc._
import play.api.libs.ws.{DefaultWSCookie, WSCookie}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}


object Main {
  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val dispatcher: ExecutionContext = system.dispatcher
    implicit val ws: StandaloneAhcWSClient = StandaloneAhcWSClient()
    implicit val config: Config = ConfigFactory.load()
    implicit val assetOverviewManager: AssetOverview.AssetOverviewManager = AssetOverview.AssetOverviewManager()

    system.scheduler.scheduleAtFixedRate(0.seconds, 1.hour)(() => scrape())
  }

  def scrape()(implicit ws: StandaloneAhcWSClient, config: Config, system: ActorSystem, dispatcher: ExecutionContext,
               assetOverviewManager: AssetOverview.AssetOverviewManager): Unit = {
    // Load config
    val username = config.getString("wealthfront.username")
    val password = config.getString("wealthfront.password")
    val secret = config.getString("two_fac.secret")
    val esHostname = config.getString("elasticsearch.hostname")

    // Get list of accounts and cookies used to authenticate future requests to wealthfront
    val (accounts, authCookies) = {
      val futureResults = (for {
        loginResponse: LoginWSResponse <- login(username, password).?|
        authCookies: Seq[WSCookie] <- twoFac(secret, loginResponse.cookies).?|
        allAccounts <- getAllAccounts(authCookies).?|
        result = (allAccounts, authCookies)
      } yield result).run.map {
        case Left(ex) => throw ex
        case Right((accounts, auth)) => (accounts, auth)
      }
      (futureResults.map(_._1), futureResults.map(_._2))
    }

    // Make requests to get DI Allocations, ETF Allocations, and Transactions
    val directInvestmentAllocationsFuture = DirectInvestmentAllocations.getDirectInvestmentAllocations(accounts,
      authCookies)
    val assetAllocationsFuture: Future[Either[Exception, Map[Account, Seq[AssetAllocation]]]] = AssetAllocations
      .getAssetAllocations(accounts, authCookies)
    val transactionsFuture = Transactions.getAllTransactions(accounts, authCookies)

    // Record the current time to use when logging to ElasticSearch
    val timeNow = LocalDateTime.now()

    // Take responses from above Futures and convert them to ElasticSearch models
    val esAssetAllocationsFuture = (for {
      assetAllocations: Map[Account, Seq[AssetAllocation]] <- assetAllocationsFuture.?|
    } yield elasticsearch.AssetAllocation.fromWealthfrontAssetAllocationMap(assetAllocations, timeNow)).run
    val esEtfCashAssetAllocationsFuture: Future[Either[Throwable, (Seq[Etf], Seq[Cash])]] = (for {
      assetAllocations: Map[Account, Seq[AssetAllocation]] <- assetAllocationsFuture.?|
    } yield Asset.fromWealthfrontAssetAllocationMap(assetAllocations, timeNow)).run
    val esDIAllocationsFuture: Future[Either[Throwable, Seq[Asset.Stock]]] = (for {
      directInvestmentAllocations: Map[Account, Seq[Stock]] <- directInvestmentAllocationsFuture.?|
      res <- Asset.fromWealthfrontStockMap(directInvestmentAllocations, timeNow).?|
    } yield res).run
    val esTradesFuture: Future[Either[Throwable, Seq[Trade]]] = (for {
      transactions: Map[Account, Seq[Transaction]] <- transactionsFuture.?|
    } yield Trade.fromWealthfrontTransactionMap(transactions)).run
    val esDividendsFuture: Future[Either[Throwable, Seq[Dividend]]] = (for {
      transactions: Map[Account, Seq[Transaction]] <- transactionsFuture.?|
    } yield Dividend.fromWealthfrontTransactionMap(transactions)).run

    // Wait for the above futures, create all the relevant JSON, and make the ElasticSearch request
    val result = (for {
      // Wait for the futures
      allAssetAllocation <- esAssetAllocationsFuture.?|
      (allEtf, allCash) <- esEtfCashAssetAllocationsFuture.?|
      allStock <- esDIAllocationsFuture.?|
      allTrade <- esTradesFuture.?|
      allDividend <- esDividendsFuture.?|

      // Convert models to JSON
      allAssetAllocationJson = allAssetAllocation.flatMap(x => Seq(
        Json.obj("index" -> Json.obj("_index" -> "wealthfront-asset-allocations")),
        Json.toJson(x)
      )).mkString("\n") + "\n"
      allEtfJson = allEtf.flatMap(x => Seq(
        Json.obj("index" -> Json.obj("_index" -> "wealthfront-assets-etf")),
        Json.toJson(x)(etfWrites)
      )).mkString("\n") + "\n"
      allCashJson: String = allCash.flatMap(x => Seq(
        Json.obj("index" -> Json.obj("_index" -> "wealthfront-assets-cash")),
        Json.toJson(x)(cashWrites)
      )).mkString("\n") + "\n"
      allStockJson: String = allStock.flatMap(x => Seq(
        Json.obj("index" -> Json.obj("_index" -> "wealthfront-assets-stock")),
        Json.toJson(x)(stockWrites)
      )).mkString("\n") + "\n"
      allTradeJson: String = allTrade.flatMap(x => Seq(
        Json.obj("index" -> Json.obj(
          "_index" -> "wealthfront-trades",
          "_id" -> s"${
            Base64.getUrlEncoder.encodeToString(s"${x.transaction_id}${x.`type`}${x.symbol}${x.shares}${
              x
                .share_price
            }".getBytes(StandardCharsets.UTF_8))
          }"
        )),
        Json.toJson(x)
      )).mkString("\n") + "\n"
      allDividendJson: String = allDividend.flatMap(x => Seq(
        Json.obj("index" -> Json.obj(
          "_index" -> "wealthfront-dividends",
          "_id" -> s"${Base64.getUrlEncoder.encodeToString(s"${x.transaction_id}${x.symbol}${x.amount}".getBytes
          (StandardCharsets.UTF_8))}"
        )),
        Json.toJson(x)(Dividend.dividendWrites)
      )).mkString("\n") + "\n"

      // Make ES bulk API request
      esResponse <- ws
        .url(s"$esHostname/_bulk")
        .withHttpHeaders("Content-Type" -> "application/x-ndjson")
        .post(allAssetAllocationJson + allEtfJson + allCashJson + allStockJson + allTradeJson + allDividendJson)
        .map(Right.apply)
        .map(_.map(_.body))
        .map(_.map(Json.parse))
        .map(_.map(Json.prettyPrint))
        .?|
    } yield ()).run
    result.onComplete {
      case Success(Right(_)) => println("Done!")
      case Success(Left(ex)) => throw ex
      case Failure(ex) => throw ex
    }
  }

  def login(username: String, password: String)(implicit ws: StandaloneAhcWSClient, dispatcher: ExecutionContext)
  : Future[Either[Throwable, LoginWSResponse]] = {
    ws.url("https://www.wealthfront.com/api/access/login")
      .withCookies(DefaultWSCookie("login_xsrf", "1234"))
      .withHttpHeaders(
        "Content-Type" -> "application/json"
      )
      .post(Json.obj(
        "loginXsrf" -> "1234",
        "username" -> username,
        "password" -> password,
        "recaptchaResponseToken" -> null,
        "grantType" -> "password"
      ))
      .map {
        case res if res.status == 200 => Right(res)
        case res => Left(new Exception(
          s"""
             | Login Request returned an exception
             |   Response Code: ${res.status} ${res.statusText}
             |   Response Body:
             | ${res.body.lines().map(l => s"    $l")}
        """.stripMargin))
      }
      .map(_.flatMap(LoginWSResponse.fromWSResponse))
  }

  def twoFac(secret: String, cookies: Seq[WSCookie])(implicit ws: StandaloneAhcWSClient,
                                                     dispatcher: ExecutionContext): Future[Either[Throwable,
    Seq[WSCookie]]] = {
    ws.url("https://www.wealthfront.com/api/access/mfa")
      .withCookies(cookies: _*)
      .withHttpHeaders(
        "Content-Type" -> "application/json"
      )
      .post(
        Json.obj(
          "xsrf" -> cookies
            .find(_.name == "xsrf")
            .map(_.value)
            .map(URLDecoder.decode(_, "UTF-8")),
          "challengeResponse" -> TimeBasedOneTimePasswordUtil.generateCurrentNumberString(secret),
          "rememberDevice" -> false
        )
      )
      .map {
        case res if res.status == 200 => Right(res.cookies.toSeq)
        case res => Left(new Exception(
          s"""
             | Two Factor Request returned an exception
             |   Response Code: ${res.status} ${res.statusText}
             |   Response Body:
             | ${res.body.lines().map(l => s"    $l")}
             |""".stripMargin))
      }
  }

  def getAllAccounts(cookies: Seq[WSCookie])(implicit ws: StandaloneAhcWSClient, dispatcher: ExecutionContext)
  : Future[Either[Exception, Seq[Account]]] = {
    ws.url("https://www.wealthfront.com/api/wealthfront_accounts/all-accounts")
      .withCookies(cookies: _*)
      .get()
      .map {
        case res if res.status == 200 => Right(res.body)
        case res => Left(new Exception(
          s"""
             | All Accounts Request returned an exception
             |   Response Code: ${res.status} ${res.statusText}
             |   Response Body:
             | ${res.body.lines().map(l => s"    $l")}
             |""".stripMargin))
      }
      .map(_.map(Json.parse).flatMap(_.validate[Seq[Account]].asEither.left.map(JsResultException)))
  }
}
