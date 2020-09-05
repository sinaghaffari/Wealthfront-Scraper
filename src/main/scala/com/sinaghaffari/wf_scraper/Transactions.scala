package com.sinaghaffari.wf_scraper

import com.sinaghaffari.wf_scraper.models.wealthfront.{Account, Transaction}
import play.api.libs.json.{JsResultException, Json}
import play.api.libs.ws.WSCookie
import play.api.libs.ws.ahc.StandaloneAhcWSClient
import scalaz.std.either._
import scalaz.std.list._
import scalaz.syntax.traverse._

import scala.concurrent.{ExecutionContext, Future}

object Transactions {
  def getAllTransactions(accounts: Future[Seq[Account]], authCookies: Future[Seq[WSCookie]])
                        (implicit ws: StandaloneAhcWSClient, dispatcher: ExecutionContext)
  : Future[Either[Throwable, Map[Account, Seq[Transaction]]]] = for {
    accounts <- accounts
    authCookies <- authCookies
    result <- Future.sequence(accounts.map(acc =>
      getTransactions(acc, authCookies).map(_.map((acc, _)))))
      .map(_.filter(_.isRight))
      .map(_.toList
        .sequenceU
        .map(_.toMap))
  } yield result

  def getTransactions(account: Account, cookies: Seq[WSCookie])
                     (implicit ws: StandaloneAhcWSClient, dispatcher: ExecutionContext)
  : Future[Either[Throwable, Seq[Transaction]]] = {
    ws.url(s"https://www.wealthfront.com/api/transactions/${account.accountId}/transfers-for-account")
      .withCookies(cookies: _*)
      .get()
      .map {
        case res if res.status == 200 => Right(res.body)
        case res => Left(new Exception(
          s"""
             | Transaction request for ${account.accountId} returned an exception
             |   Response Code: ${res.status} ${res.statusText}
             |   Response Body:
             | ${res.body.lines().map(l => s"    $l")}
        """.stripMargin))
      }
      .map(_.map(Json.parse).flatMap(j => (j \ "transfers" \ "completed_transfers").validate[Seq[Transaction]]
        .asEither.left.map(JsResultException)))
  }
}
