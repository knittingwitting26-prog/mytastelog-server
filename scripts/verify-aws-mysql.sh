#!/usr/bin/env bash
set -euo pipefail

# The Java launcher echoes JAVA_TOOL_OPTIONS, which can expose a truststore
# password. Forward it under a private name and let the verification test apply
# only the supported javax.net.ssl truststore properties without printing them.
export AWS_VERIFY_JAVA_OPTIONS="${JAVA_TOOL_OPTIONS-}"
unset JAVA_TOOL_OPTIONS

./mvnw -DawsVerification=false test
printf '\nRegression tests     PASS\n\n'

exec ./mvnw -DawsVerification=true -Dtest=AwsMySqlCrudVerificationTest test
