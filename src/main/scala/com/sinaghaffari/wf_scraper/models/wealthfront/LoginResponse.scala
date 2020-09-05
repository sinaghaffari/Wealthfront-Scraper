package com.sinaghaffari.wf_scraper.models.wealthfront

import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json.{JsPath, Reads}


trait LoginResponse {
  val success: Boolean
}

object LoginResponse {
  val unsuccessfulLoginResponseReads: Reads[LoginResponse] = (
    (JsPath \ "redirectUrl").read[String] and
      (JsPath \ "success").read[Boolean](verifying[Boolean](x => !x))
    ) ((redirectUrl, _) => Unsuccessful(redirectUrl))
  val noMfaLoginResponseReads: Reads[LoginResponse] = (
    (JsPath \ "username").read[String] and
      (JsPath \ "mfaRequired").read[Boolean](verifying[Boolean](x => !x)) and
      (JsPath \ "success").read[Boolean](verifying[Boolean](x => x))
    ) ((username, _, _) => NoMfa(username))
  val mfaLoginResponseReads: Reads[LoginResponse] = (
    (JsPath \ "username").read[String] and
      (JsPath \ "mfaDevice").read[String] and
      (JsPath \ "partialPhoneNumber").readNullable[String] and
      (JsPath \ "mfaRequired").read[Boolean](verifying[Boolean](x => x)) and
      (JsPath \ "success").read[Boolean](verifying[Boolean](x => x))
    ) ((username, mfaDevice, partialPhoneNumber, _, _) => Mfa(username, mfaDevice, partialPhoneNumber))

  trait Successful extends LoginResponse {
    override val success: Boolean = true
    val username: String
    val mfaRequired: Boolean
  }

  case class Unsuccessful(redirectUrl: String) extends LoginResponse {
    override val success: Boolean = false
  }

  case class NoMfa(username: String) extends Successful {
    override val mfaRequired: Boolean = false
  }

  case class Mfa(username: String, mfaDevice: String, partialPhoneNumber: Option[String]) extends Successful {
    override val mfaRequired: Boolean = true
  }

  implicit val loginResponseReads: Reads[LoginResponse] = unsuccessfulLoginResponseReads or
    noMfaLoginResponseReads or
    mfaLoginResponseReads
}
