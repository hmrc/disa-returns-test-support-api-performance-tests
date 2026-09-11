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
import uk.gov.hmrc.perftests.disaretrunstestsupport.LoginRequest.createAuthenticatedReferences
import uk.gov.hmrc.perftests.disaretrunstestsupport.TestSupportAPIRequests._

class TestSupportAPISimulation extends PerformanceTestRunner {

  private val reconciliationJourneyId = "monthly-return-test-support-api-journey"
  private val overrideJourneyId       = "reporting-window-override-journey"
  private val reservedZReferences     = Set("Z1400", "Z1500", "Z1503")
  private val namespaceSize           = 100000000 - reservedZReferences.size

  private def userCount(journeyId: String): Int =
    definitions(labels)
      .find(_.id == journeyId)
      .map { journey =>
        if (runSingleUserJourney) 1
        else {
          val configuredRate = journey.load * loadFactor
          val rate           =
            if ((constantRateTime.toSeconds * configuredRate).toInt < 1)
              1d / (constantRateTime.toSeconds - 1)
            else configuredRate

          Math.toIntExact(
            ((0.0001d + (rate - 0.0001d) / 2) * rampUpTime.toSeconds).toLong +
              (constantRateTime.toSeconds * rate).round +
              ((rate + (0.0001d - rate) / 2) * rampDownTime.toSeconds).toLong
          )
        }
      }
      .getOrElse(0)

  private val reconciliationCount = userCount(reconciliationJourneyId)
  private val overrideCount       = userCount(overrideJourneyId)
  private val totalCount          = reconciliationCount.toLong + overrideCount
  require(
    totalCount <= namespaceSize,
    s"Required $totalCount Z-references exceed the $namespaceSize available references"
  )

  private val zReferences             = Iterator
    .from(0)
    .map(value => f"Z$value%04d")
    .filterNot(reservedZReferences)
    .take(totalCount.toInt)
    .toVector
  private val performanceDataCleanup  = new PerformanceDataCleanup
  private var authenticatedReferences = Vector.empty[Map[String, String]]

  before {
    performanceDataCleanup.cleanup(zReferences)
    authenticatedReferences = createAuthenticatedReferences(zReferences)
  }

  after {
    performanceDataCleanup.cleanup(zReferences)
  }

  private def references(offset: Int, count: Int): Iterator[Map[String, String]] =
    Iterator.range(offset, offset + count).map { index =>
      if (authenticatedReferences.isEmpty) throw new IllegalStateException("Authenticated Z-references are not ready")
      authenticatedReferences(index)
    }

  setup(
    reconciliationJourneyId,
    "Monthly Return Test Support Api Journey"
  ).withActions(
    feed(references(offset = 0, count = reconciliationCount)).actionBuilders*
  ).withRequests(
    (Seq(generateReconciliationReportScenario) ++
      Option.when(runSingleUserJourney)(verifyReconciliationReportScenario))*
  )

  setup(
    overrideJourneyId,
    "Reporting Window Override Journey"
  ).withActions(
    feed(references(offset = reconciliationCount, count = overrideCount)).actionBuilders*
  ).withRequests(
    (Seq(setReportingWindowOverrideScenario) ++
      Option.when(runSingleUserJourney)(verifyReportingWindowOpenScenario))*
  )

  runSimulation()
}
