/*
 * Copyright 2026 HM Revenue & Customs
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

import uk.gov.hmrc.perftests.disaretrunstestsupport.constant.AppConfig.{disaReturnsBaseUrl, disaReturnsStubsBaseUrl}

import java.net.URI
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest}
import java.time.Duration
import scala.util.control.NonFatal

class PerformanceDataCleanup {

  private val zReferenceCleanupBatchSize = 5000

  private val httpClient = HttpClient
    .newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .build()

  def cleanup(zReferences: Seq[String]): Unit = {
    val failures = zReferences
      .grouped(zReferenceCleanupBatchSize)
      .flatMap { batch =>
        Seq(
          cleanupAt(
            s"$disaReturnsStubsBaseUrl/test-only/reconciliation-report-data/cleanup",
            batch,
            "disa-returns-stubs reconciliation report data"
          ),
          cleanupAt(
            s"$disaReturnsStubsBaseUrl/test-only/reporting-window-overrides/cleanup",
            batch,
            "disa-returns-stubs reporting-window overrides"
          ),
          cleanupAt(
            s"$disaReturnsBaseUrl/test-only/monthly",
            batch,
            "disa-returns"
          )
        ).flatten
      }
      .toSeq

    if (failures.nonEmpty)
      throw new IllegalStateException(s"Performance data cleanup failed:\n${failures.mkString("\n")}")
  }

  private def cleanupAt(url: String, zReferences: Seq[String], service: String): Option[String] =
    if (zReferences.isEmpty) None
    else
      try {
        val values   = zReferences.map(zReference => s"\"$zReference\"").mkString(",")
        val request  = HttpRequest
          .newBuilder(URI.create(url))
          .timeout(Duration.ofSeconds(30))
          .header("Content-Type", "application/json")
          .POST(BodyPublishers.ofString(s"{\"zReferences\":[$values]}"))
          .build()
        val response = httpClient.send(request, BodyHandlers.ofString())

        Option.when(response.statusCode() != 204)(
          s"- $service: status=${response.statusCode()}, body=${response.body()}"
        )
      } catch {
        case NonFatal(exception) => Some(s"- $service: ${exception.getClass.getSimpleName}: ${exception.getMessage}")
      }
}
