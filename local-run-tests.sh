#!/usr/bin/env bash
set -euo pipefail

sbt scalafmtCheckAll scalafmtSbtCheck

sbt -DrunLocal=true "Gatling / test"
