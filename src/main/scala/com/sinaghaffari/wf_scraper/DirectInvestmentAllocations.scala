package com.sinaghaffari.wf_scraper

import com.sinaghaffari.wf_scraper.models.wealthfront.{Account, Stock}
import play.api.libs.json.{JsResultException, Json}
import play.api.libs.ws.WSCookie
import play.api.libs.ws.ahc.StandaloneAhcWSClient
import scalaz.std.either._
import scalaz.std.list._
import scalaz.syntax.traverse._

import scala.concurrent.{ExecutionContext, Future}

object DirectInvestmentAllocations {
  def getDirectInvestmentAllocations(accounts: Future[Seq[Account]], authCookies: Future[Seq[WSCookie]])
                                    (implicit ws: StandaloneAhcWSClient, dispatcher: ExecutionContext)
  : Future[Either[Exception, Map[Account, Seq[Stock]]]] = for {
    accounts <- accounts
    authCookies <- authCookies
    result <- Future.sequence(
      accounts.map(acc => getDirectInvestmentAllocationsForAccount(acc, authCookies).map(_.map((acc, _))))
    )
      .map(_.filter(_.isRight))
      .map(_.toList
        .sequenceU
        .map(_.toMap)
      )
  } yield result

  def getDirectInvestmentAllocationsForAccount(account: Account, authCookies: Seq[WSCookie])
                                              (implicit ws: StandaloneAhcWSClient, dispatcher: ExecutionContext)
  : Future[Either[Exception, Seq[Stock]]] = {
    ws.url(s"https://www.wealthfront.com/api/wealthfront_accounts/${account.accountId}/di-allocations")
      .withCookies(authCookies: _*)
      .get()
      .map {
        case res if res.status == 200 => Right(res.body)
        case res => Left(new Exception(
          s"""
             | Get Direct Investment Allocation Request for ${account.accountId} returned an exception
             |   Response Code: ${res.status} ${res.statusText}
             |   Response Body:
             | ${res.body.lines().map(l => s"    $l")}
             |""".stripMargin))
      }
      .map(_.map(Json.parse))
      .map(_.flatMap(j => (j \ 0 \ "stocks").validate[Seq[Stock]].asEither.left.map(JsResultException)))
  }
}
