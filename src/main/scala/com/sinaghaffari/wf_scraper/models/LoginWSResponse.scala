package com.sinaghaffari.wf_scraper.models

import com.sinaghaffari.wf_scraper.models.wealthfront.LoginResponse
import play.api.libs.json.{JsResultException, Json}
import play.api.libs.ws.{StandaloneWSResponse, WSCookie}

case class LoginWSResponse(status: Int, cookies: Seq[WSCookie], response: LoginResponse)

object LoginWSResponse {
  def fromWSResponse(wsResponse: StandaloneWSResponse): Either[Throwable, LoginWSResponse] = {
    Json.parse(wsResponse.body)
      .validate[LoginResponse]
      .asEither
      .left
      .map(JsResultException)
      .map(LoginWSResponse(
        wsResponse.status,
        wsResponse.cookies.toSeq,
        _,
      ))
  }
}
