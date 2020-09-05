package com.sinaghaffari.wf_scraper

import com.sinaghaffari.wf_scraper.models.wealthfront.{Account, AssetAllocation}
import play.api.libs.json.{JsResultException, Json}
import play.api.libs.ws.WSCookie
import play.api.libs.ws.ahc.StandaloneAhcWSClient
import scalaz.std.either._
import scalaz.std.list._
import scalaz.syntax.traverse._

import scala.concurrent.{ExecutionContext, Future}

object AssetAllocations {
  def getAssetAllocations(accounts: Future[Seq[Account]], authCookies: Future[Seq[WSCookie]])(implicit
                                                                                              ws: StandaloneAhcWSClient,
                                                                                              dispatcher: ExecutionContext)
  : Future[Either[Exception, Map[Account, Seq[AssetAllocation]]]] = for {
    accounts <- accounts
    authCookies <- authCookies
    result <- Future.sequence(accounts.map(acc => getAssetAllocationsForAccount(acc, authCookies).map(_.map((acc, _)))))
      .map(_.toList.sequenceU.map(_.toMap))
  } yield result

  private def getAssetAllocationsForAccount(account: Account, authCookies: Seq[WSCookie])(implicit
                                                                                          ws: StandaloneAhcWSClient,
                                                                                          dispatcher: ExecutionContext): Future[Either[Exception, Seq[AssetAllocation]]] = {
    ws.url(s"https://www.wealthfront.com/api/wealthfront_accounts/${account.accountId}/asset-allocation")
      .withCookies(authCookies: _*)
      .get()
      .map {
        case res if res.status == 200 => Right(res.body)
        case res => Left(new Exception(
          s"""
             |Get Asset Allocation Request for ${account.accountId} returned an exception
             |  Response Code: ${res.status} ${res.statusText}
             |  Response Body:
             |${res.body.lines().map(l => s"    $l")}
             |""".stripMargin))
      }
      .map(_.map(Json.parse))
      .map(_.flatMap(j => (j \ "assetAllocations").validate[Seq[AssetAllocation]].asEither.left.map(JsResultException)))
  }
}
