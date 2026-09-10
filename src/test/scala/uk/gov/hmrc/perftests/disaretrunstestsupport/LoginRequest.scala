/*
 * Copyright 2025 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.perftests.disaretrunstestsupport

import uk.gov.hmrc.perftests.disaretrunstestsupport.constant.AppConfig.ggSignInUrl

import java.net.URI
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest}
import java.time.Duration
import java.util.UUID
import scala.util.control.NonFatal

object LoginRequest {

  private val BearerTokenPattern = "(?i)^Bearer\\s+\\S+$".r

  private val httpClient = HttpClient
    .newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .build()

  def createAuthenticatedPool(zReferences: Seq[String]): Vector[Map[String, String]] = {
    require(zReferences.nonEmpty, "Cannot create authenticated Z-reference pool without Z-references")

    val loginZReference = zReferences.head
    val request         = HttpRequest
      .newBuilder(URI.create(ggSignInUrl))
      .timeout(Duration.ofSeconds(30))
      .header("Content-Type", "application/json")
      .POST(BodyPublishers.ofString(authRequestPayload(loginZReference)))
      .build()
    val response        =
      try httpClient.send(request, BodyHandlers.ofString())
      catch {
        case exception: InterruptedException =>
          Thread.currentThread().interrupt()
          throw new IllegalStateException("Shared bearer-token login setup was interrupted", exception)
        case NonFatal(exception)             =>
          throw new IllegalStateException(s"Shared bearer-token login setup failed: ${exception.getMessage}", exception)
      }

    if (response.statusCode() != 201)
      throw new IllegalStateException(
        s"Shared bearer-token login setup failed: status=${response.statusCode()}, body=${response.body()}"
      )

    val bearerToken = response.headers().firstValue("Authorization").orElse("")
    if (!BearerTokenPattern.matches(bearerToken))
      throw new IllegalStateException(
        "Shared bearer-token login setup failed: response did not contain a valid Authorization Bearer header"
      )

    zReferences.map(zReference => Map("zRef" -> zReference, "bearerToken" -> bearerToken)).toVector
  }

  private def authRequestPayload(zReference: String): String =
    s"""{
  "affinityGroup": "Organisation",
  "credId": "${UUID.randomUUID()}",
  "credentialStrength": "strong",
  "excludeGnapToken": true,
  "enrolments": [
    {
      "key": "HMRC-DISA-ORG",
      "identifiers": [{
        "key": "ZREF",
        "value": "$zReference"
      }],
      "state": "Activated"
    }
  ]
}"""
}
