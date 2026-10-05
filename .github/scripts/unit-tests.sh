#!/usr/bin/env bash
# Runs the unit tests of all modules that have any (src/test). They run on the JVM, partly with
# Robolectric, so they don't need a device or an emulator. Extra arguments are passed to Gradle.
# This file only exists in the fork, so it never conflicts with upstream changes.
set -euo pipefail
cd "$(dirname "$0")/../.."

mapfile -t tasks < <(
  find . -path '*/src/test' -type d -not -path '*/build/*' | sort \
    | sed -E 's#^\./##; s#/src/test$##; s#/#:#g; s#^(.*)$#:\1:testDebugUnitTest#'
)
./gradlew "${tasks[@]}" --continue "$@"
