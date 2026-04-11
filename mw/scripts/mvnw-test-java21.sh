#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

./mvnw test

