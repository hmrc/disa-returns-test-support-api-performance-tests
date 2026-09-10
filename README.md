# disa-returns-test-support-api-performance-tests

Performance test suite for the DISA Returns Test Support API, using [performance-test-runner](https://github.com/hmrc/performance-test-runner) under the hood.

## Pre-requisites

### Services

Start Mongo Docker container following instructions from the [MDTP Handbook](https://docs.tax.service.gov.uk/mdtp-handbook/documentation/developer-set-up/set-up-mongodb.html).

Start `DISA_RETURNS` services as follows:

```bash
sm2 --start PUSH_PULL_NOTIFICATIONS_API --appendArgs '{"PUSH_PULL_NOTIFICATIONS_API": ["-Dallowlisted.useragents.0=api-subscription-fields","-Dallowlisted.useragents.1=disa-returns","-DvalidateHttpsCallbackUrl=false"]}'

sm2 --start DISA_RETURNS_ALL
```

The suite requires `DISA_RETURNS_TEST_SUPPORT_API` and `AUTH_LOGIN_API`, which are included in `DISA_RETURNS_ALL`. It
also uses scoped cleanup endpoints in `DISA_RETURNS` and `DISA_RETURNS_STUBS`. Append
`-Dapplication.router=testOnlyDoNotUseInAppConf.Routes` to both services when their environment does not enable
test-only routes. Runs must not overlap because the suite reserves and cleans a fixed, finite Z-reference pool before
and after execution.

Both journeys independently cycle through one authenticated pool beginning at `Z1000`. The reporting-window journey
is rotated by half the pool to stagger reuse between journeys. Configure the pool with
`-Dperftest.zReferencePoolSize=<size>`; it defaults to 500, must be between 1 and 4,000, and is forced to one for smoke
runs. Bearer tokens are created once during setup, outside measured Gatling requests, with login starts paced at five
per second.

At `loadPercentage = 1000`, each configured 10 JPS journey runs at 100 JPS and creates approximately 36,000 users over
the configured test duration. A default 500-reference circular pool is therefore reused approximately every five
seconds by each journey and takes approximately 100 seconds to authenticate during setup. The maximum pool size is
4,000.

Cleanup runs before and after the simulation using `POST` requests with `{"zReferences":[...]}`:

- shared pool: `/test-only/monthly` on `DISA_RETURNS`
- shared pool: `/test-only/reconciliation-report-data/cleanup` on `DISA_RETURNS_STUBS`
- shared pool: `/test-only/reporting-window-overrides/cleanup` on `DISA_RETURNS_STUBS`

The `DISA_RETURNS` cleanup clears any reconciliation-report-ready callback state associated with the reserved reconciliation
references so the shared pool starts clean. Cleanup is scoped to allocated references, attempts every applicable
endpoint, and reports all failures together. The fixed pool and cleanup mean runs must be isolated: do not
overlap suite executions.

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

The suite asserts request success and smoke semantics. It does not define a latency SLO or invent response-time
thresholds; latency is assessed from the Gatling report against the agreed service objective.

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
sbt -Dperftest.runSmokeTest=true -DrunLocal=true gatling:test
```

Run full performance test (locally) as follows:

```bash
sbt -DrunLocal=true gatling:test
```

Run smoke test (staging) as follows:

```bash
sbt -Dperftest.runSmokeTest=true -DrunLocal=false gatling:test
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
