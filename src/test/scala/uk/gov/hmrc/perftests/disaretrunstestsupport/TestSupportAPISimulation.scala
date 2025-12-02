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

import io.gatling.core.Predef.feed
import uk.gov.hmrc.performance.simulation.PerformanceTestRunner
import uk.gov.hmrc.perftests.disaretrunstestsupport.LoginRequest.getBearerToken
import uk.gov.hmrc.perftests.disaretrunstestsupport.TestSupportAPIRequests._
import uk.gov.hmrc.perftests.disaretrunstestsupport.util.RandomDataGenerator.{generateRandomISAReference, getMonth, getTaxYear}

class TestSupportAPISimulation extends PerformanceTestRunner {

  def generateReportInformationForTheSubmission(): Iterator[Map[String, String]] =
    Iterator.continually(
      Map(
        "zRef"    -> generateRandomISAReference(1, 500),
        "taxYear" -> getTaxYear,
        "month"   -> getMonth
      )
    )

  setup(
    "monthly-return-test-support-api-journey",
    "Monthly Return Test Support Api Journey"
  ) withActions (feed(
    generateReportInformationForTheSubmission()
  ).actionBuilders: _*) withRequests (
    getBearerToken,
    generateReconciliationReportScenario
  )

  runSimulation()
}
