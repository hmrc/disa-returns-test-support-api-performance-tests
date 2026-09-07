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

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.http.request.builder.HttpRequestBuilder
import uk.gov.hmrc.performance.conf.ServicesConfiguration
import uk.gov.hmrc.perftests.disaretrunstestsupport.constant.AppConfig._
import uk.gov.hmrc.perftests.disaretrunstestsupport.constant.Headers.headers

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDate, ZoneOffset}
import java.util.Locale

object TestSupportAPIRequests extends ServicesConfiguration {

  val generateReconciliationReportPayload: String = s"""
         {
                                                       |    "oversubscribed": 1,
                                                       |    "traceAndMatch": 2,
                                                       |    "failedEligibility": 3
                                                       |}""".stripMargin

  val generateReconciliationReportScenario: HttpRequestBuilder =
    http("Generate Reconciliation Report")
      .post(s"$disaReturnsTestSupportBaseUrl/monthly/#{zRef}/reconciliation")
      .headers(headers)
      .body(StringBody(generateReconciliationReportPayload))
      .check(status.is(204))

  private val today          = LocalDate.now(ZoneOffset.UTC)
  private val taxYearStart   = if (today.getMonthValue >= 4) today.getYear else today.getYear - 1
  private val currentTaxYear = f"$taxYearStart-${(taxYearStart + 1) % 100}%02d"
  private val currentMonth   =
    today.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)).toUpperCase(Locale.ENGLISH)

  val verifyReconciliationReportScenario: HttpRequestBuilder =
    http("Verify Reconciliation Report")
      .get(s"$disaReturnsStubsBaseUrl/monthly/#{zRef}/$currentTaxYear/$currentMonth/results?limit=10")
      .headers(headers)
      .check(status.is(200))
      .check(jsonPath("$.returnResults[*]").count.is(6))

  val setReportingWindowOverrideScenario: HttpRequestBuilder =
    http("Set Reporting Window Override")
      .put(s"$disaReturnsTestSupportBaseUrl/monthly/#{zRef}/reporting-window-override")
      .headers(headers)
      .body(StringBody { _ =>
        val now = Instant.now()
        s"""{"startDate":"${now.minusSeconds(60)}","endDate":"${now.plusSeconds(3600)}"}"""
      })
      .check(status.is(204))

  val verifyReportingWindowOpenScenario: HttpRequestBuilder =
    http("Verify Reporting Window Is Open")
      .get(s"$disaReturnsStubsBaseUrl/disa-returns-submission/reporting-window/status/#{zRef}")
      .headers(headers)
      .check(status.is(200))
      .check(jsonPath("$.reportingWindowOpen").ofType[Boolean].is(true))
}
