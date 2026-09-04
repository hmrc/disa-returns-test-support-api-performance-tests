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

import com.typesafe.config.ConfigFactory
import io.gatling.core.Predef.feed
import uk.gov.hmrc.performance.simulation.PerformanceTestRunner
import uk.gov.hmrc.perftests.disaretrunstestsupport.LoginRequest.createAuthenticatedPool
import uk.gov.hmrc.perftests.disaretrunstestsupport.TestSupportAPIRequests._

class TestSupportAPISimulation extends PerformanceTestRunner {

  private val reconciliationJourneyId = "monthly-return-test-support-api-journey"
  private val overrideJourneyId       = "reporting-window-override-journey"
  private val configuredPoolSize      = ConfigFactory.load().getInt("perftest.zReferencePoolSize")
  private val poolSize                = if (runSingleUserJourney) 1 else configuredPoolSize
  require(poolSize >= 1 && poolSize <= 4000, "perftest.zReferencePoolSize must be between 1 and 4000")

  private val zReferences            = (1000 until 1000 + poolSize).map(value => f"Z$value%04d")
  private val performanceDataCleanup = new PerformanceDataCleanup
  private var authenticatedPool      = Vector.empty[Map[String, String]]

  before {
    performanceDataCleanup.cleanup(zReferences)
    authenticatedPool = createAuthenticatedPool(zReferences)
  }

  after {
    performanceDataCleanup.cleanup(zReferences)
  }

  private def circularPool(rotation: Int): Iterator[Map[String, String]] = new Iterator[Map[String, String]] {
    private var index = rotation

    override def hasNext: Boolean = true

    override def next(): Map[String, String] = {
      if (authenticatedPool.isEmpty) throw new IllegalStateException("Authenticated Z-reference pool is not ready")
      val value = authenticatedPool(index % authenticatedPool.size)
      index = (index + 1) % authenticatedPool.size
      value
    }
  }

  setup(
    reconciliationJourneyId,
    "Monthly Return Test Support Api Journey"
  ) withActions (feed(
    circularPool(rotation = 0)
  ).actionBuilders: _*) withRequests ((
    Seq(generateReconciliationReportScenario) ++
      Option.when(runSingleUserJourney)(verifyReconciliationReportScenario)
  ): _*)

  setup(
    overrideJourneyId,
    "Reporting Window Override Journey"
  ) withActions (feed(
    circularPool(rotation = poolSize / 2)
  ).actionBuilders: _*) withRequests ((
    Seq(setReportingWindowOverrideScenario) ++
      Option.when(runSingleUserJourney)(verifyReportingWindowOpenScenario)
  ): _*)

  runSimulation()
}
