package com.sinaghaffari.wf_scraper
import java.net.URLDecoder

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.j256.twofactorauth.TimeBasedOneTimePasswordUtil
import com.typesafe.config.{Config, ConfigFactory}
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json.{JsPath, Json, Reads}
import play.api.libs.ws.JsonBodyWritables._
import play.api.libs.ws.ahc._
import play.api.libs.ws.{DefaultWSCookie, StandaloneWSResponse, WSCookie}

import scala.concurrent.{ExecutionContext, Future}

object Main {
  trait LoginResponse {
    val success: Boolean
  }
  object LoginResponse {
    val unsuccessfulLoginResponseReads: Reads[LoginResponse] = (
      (JsPath \ "redirectUrl").read[String] and
        (JsPath \ "success").read[Boolean](verifying[Boolean](x => !x))
    )((redirectUrl, _) => UnsuccessfulLoginResponse(redirectUrl))
    val noMfaLoginResponseReads: Reads[LoginResponse] = (
      (JsPath \ "username").read[String] and
        (JsPath \ "mfaRequired").read[Boolean](verifying[Boolean](x => !x)) and
        (JsPath \ "success").read[Boolean](verifying[Boolean](x => x))
    )((username, _, _) => NoMfaLoginResponse(username))
    val mfaLoginResponseReads: Reads[LoginResponse] = (
      (JsPath \ "username").read[String] and
        (JsPath \ "mfaDevice").read[String] and
        (JsPath \ "partialPhoneNumber").readNullable[String] and
        (JsPath \ "mfaRequired").read[Boolean](verifying[Boolean](x => x)) and
        (JsPath \ "success").read[Boolean](verifying[Boolean](x => x))
      )((username, mfaDevice, partialPhoneNumber, _, _) => MfaLoginResponse(username, mfaDevice, partialPhoneNumber))
    implicit val loginResponseReads: Reads[LoginResponse] = unsuccessfulLoginResponseReads or
      noMfaLoginResponseReads or
      mfaLoginResponseReads
  }
  trait SuccessfulLoginResponse extends LoginResponse {
    val username: String
    val mfaRequired: Boolean
    override val success: Boolean = true
  }
  case class UnsuccessfulLoginResponse(redirectUrl: String) extends LoginResponse {
    override val success: Boolean = false
  }
  case class NoMfaLoginResponse(username: String) extends SuccessfulLoginResponse {
    override val mfaRequired: Boolean = false
  }
  case class MfaLoginResponse(username: String, mfaDevice: String, partialPhoneNumber: Option[String]) extends SuccessfulLoginResponse {
    override val mfaRequired: Boolean = true
  }
  case class LoginWSResponse(status: Int, cookies: scala.collection.Seq[WSCookie], response: Option[LoginResponse])
  object LoginWSResponse {
    def fromWSResponse(wsResponse: StandaloneWSResponse): LoginWSResponse = {
      val responseJson = Json.parse(wsResponse.body).validate[LoginResponse]
      LoginWSResponse(
        wsResponse.status,
        wsResponse.cookies,
        responseJson.asOpt,
      )
    }
  }

  def login(username: String, password: String)(implicit ws: StandaloneAhcWSClient, dispatcher: ExecutionContext): Future[LoginWSResponse] = {
    ws.url("https://www.wealthfront.com/api/access/login")
      .withCookies(DefaultWSCookie("login_xsrf", "1234"))
      .withHttpHeaders(
        "Content-Type" -> "application/json",
      )
      .post(Json.obj(
        "loginXsrf" -> "1234",
        "username" -> username,
        "password" -> password,
        "recaptchaResponseToken" -> null,
        "grantType" -> "password",
      ))
      .map(LoginWSResponse.fromWSResponse)
  }

  def twoFac(secret: String, cookies: Seq[WSCookie])(implicit ws: StandaloneAhcWSClient, dispatcher: ExecutionContext): Future[Seq[WSCookie]] = {
    ws.url("https://www.wealthfront.com/api/access/mfa")
      .withCookies(cookies:_*)
      .withHttpHeaders(
        "Content-Type" -> "application/json",
      )
      .post(
        Json.obj(
          "xsrf" -> cookies
            .find(_.name == "xsrf")
            .map(_.value)
            .map(URLDecoder.decode(_, "UTF-8")),
          "challengeResponse" -> TimeBasedOneTimePasswordUtil.generateCurrentNumberString(secret),
          "rememberDevice" -> false,
        )
      )
      .map(_.cookies.toSeq)
  }

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val dispatcher: ExecutionContext = system.dispatcher
    implicit val ws: StandaloneAhcWSClient = StandaloneAhcWSClient()
    val config: Config = ConfigFactory.load()

    val username = config.getString("wealthfront.username")
    val password = config.getString("wealthfront.password")
    val secret = config.getString("two_fac.secret")
    val t = for {
      loginResponse <- login(username, password)
      requestCookies <- twoFac(secret, loginResponse.cookies.toSeq)
    } yield requestCookies
  }
}
