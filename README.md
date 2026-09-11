# disa-returns-test-support-api-performance-tests

Performance test suite for the DISA Returns Test Support API, using [performance-test-runner](https://github.com/hmrc/performance-test-runner) under the hood.

## Pre-requisites

### Services

Start Mongo Docker container following instructions from the [MDTP Handbook](https://docs.tax.service.gov.uk/mdtp-handbook/documentation/developer-set-up/set-up-mongodb.html).

Start `DISA_RETURNS` services as follows:

```bash
sm2 --start PUSH_PULL_NOTIFICATIONS_API --appendArgs '{"PUSH_PULL_NOTIFICATIONS_API": ["-Dallowlisted.useragents.0=api-subscription-fields","-Dallowlisted.useragents.1=disa-returns","-DvalidateHttpsCallbackUrl=false"]}'

sm2 --start DISA_RETURNS_ALL --appendArgs '{
  "DISA_RETURNS_TEST_SUPPORT_API": [
    "-Dfeatures.enrolment-verification-enabled=false",
    "-Dfeatures.strict-z-reference-validation-enabled=false"
  ],
  "DISA_RETURNS": [
    "-Dfeatures.strict-z-reference-validation-enabled=false",
    "-Dapplication.router=testOnlyDoNotUseInAppConf.Routes"
  ],
  "DISA_RETURNS_STUBS": [
    "-Dapplication.router=testOnlyDoNotUseInAppConf.Routes"
  ]
}'
```

`DISA_RETURNS_TEST_SUPPORT_API` needs enrolment verification disabled because the suite shares one bearer token across
Z-references. It and `DISA_RETURNS` need strict Z-reference validation disabled for references containing five to eight
digits. The test-only routers on `DISA_RETURNS` and `DISA_RETURNS_STUBS` provide the cleanup endpoints. `AUTH_LOGIN_API`
is also required and is included in `DISA_RETURNS_ALL`.

Each injected user gets a unique Z-reference. The two journeys use separate finite ranges from the staging-only 4-to-8
digit namespace. One bearer token is created during setup and shared by all references.

Cleanup runs before and after the simulation using bulk `POST` requests in batches of 5,000:

- `/test-only/monthly` on `DISA_RETURNS`
- `/test-only/reconciliation-report-data/cleanup` on `DISA_RETURNS_STUBS`
- `/test-only/reporting-window-overrides/cleanup` on `DISA_RETURNS_STUBS`

Cleanup is scoped to allocated references and reports all failures together. Do not overlap suite executions.

### Logging

The default log level for all HTTP requests is set to `WARN`. Configure [logback.xml](src/test/resources/logback.xml) to update this if required.

### WARNING :warning:

Do **NOT** run a full performance test against staging from your local machine. Please [implement a new performance test job](https://docs.tax.service.gov.uk/mdtp-handbook/documentation/mdtp-test-approach/performance-testing/performance-test-a-microservice/index.html) and execute your job from the dashboard in [Performance Jenkins](https://performance.tools.staging.tax.service.gov.uk).

## Tests

### Routes Under Test

- `POST /monthly/:zReference/reconciliation`
- `PUT /monthly/:zReference/reporting-window-override`

The reconciliation route no longer includes tax-year or month URL segments. Reporting-window override payloads are
generated around the current instant. Smoke runs also verify downstream semantics directly against the current
`DISA_RETURNS_STUBS` contracts: the reconciliation results response contains the six generated results, and the overridden
reporting-window status has `reportingWindowOpen` equal to `true`. These verification reads are not included in full
load runs.

### Bash Scripts

- `./smoke-run-tests.sh` runs every journey locally with one user per journey.
- `./local-run-tests.sh` runs the full local performance test using the configured journey loads.

Both scripts stop immediately if Gatling fails.

Run either script from the repository root, for example:

```bash
./smoke-run-tests.sh
```

### Commands

Run formatting and compile the test suite before committing:

```bash
sbt precommit
```

Run smoke test (locally) as follows:

```bash
sbt -Dperftest.runSmokeTest=true -DrunLocal=true "Gatling / test"
```

Run full performance test (locally) as follows:

```bash
sbt -DrunLocal=true "Gatling / test"
```

Run smoke test (staging) as follows:

```bash
sbt -Dperftest.runSmokeTest=true -DrunLocal=false "Gatling / test"
```

## Scalafmt

Check all project files are formatted as expected as follows:

```bash
sbt scalafmtCheckAll scalafmtSbtCheck
```

Format `*.sbt` and `project/*.scala` files as follows:

```bash
sbt scalafmtSbt
```

Format all project files as follows:

```bash
sbt scalafmtAll
```

## License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
