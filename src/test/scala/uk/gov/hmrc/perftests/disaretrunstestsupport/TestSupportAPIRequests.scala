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
import uk.gov.hmrc.perftests.disaretrunstestsupport.constant.Headers.{headerOnlyWithBearerToken, headerWithJsonContentType}

object TestSupportAPIRequests extends ServicesConfiguration {

  val generateReconciliationReportPayload: String = s"""
         {
                                                       |    "oversubscribed": 1,
                                                       |    "traceAndMatch": 2,
                                                       |    "failedEligibility": 3
                                                       |}""".stripMargin

  val generateReconciliationReportScenario: HttpRequestBuilder =
    http("Generate Reconciliation Report")
      .post(s"$disaReturnsTestSupportBaseUrl/#{zRef}/2025-26/#{month}/$testSupportPath")
      .headers(headerWithJsonContentType)
      .body(StringBody(generateReconciliationReportPayload))
      .check(status.is(204))

  val getReportingResultsSummary: HttpRequestBuilder =
    http("Get Reporting Results Summary")
      .get(s"$disaReturnsHost$disaReturnsRoute#{zRef}/2025-26/#{month}$reportingResultsSummaryPath")
      .headers(headerOnlyWithBearerToken)
      .check(status.is(200))
}
