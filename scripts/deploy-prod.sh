#!/usr/bin/env bash
set -Eeuo pipefail

EC2_HOST="${EC2_HOST:-43.203.11.71}"
EC2_USER="${EC2_USER:-ec2-user}"
SSH_KEY_CONFIG="${SSH_KEY:-C:\\Users\\kjo57\\Desktop\\knittingwitting\\knittingwitting_v2_key.pem}"
SERVICE_NAME="${SERVICE_NAME:-mytastelog.service}"
REMOTE_JAR="${REMOTE_JAR:-/opt/mytastelog/mytastelog-server.jar}"
REMOTE_ENV="${REMOTE_ENV:-/etc/mytastelog/mytastelog.env}"
LOCAL_JAR="${LOCAL_JAR:-target/mytastelog-server-0.0.1-SNAPSHOT.jar}"
SMOKE_URL="${SMOKE_URL:-https://archive-api.knittingwitting.com/api/v1/public/records?limit=1}"
SMOKE_RETRY_INTERVAL_SECONDS="${SMOKE_RETRY_INTERVAL_SECONDS:-3}"
SMOKE_MAX_ATTEMPTS="${SMOKE_MAX_ATTEMPTS:-10}"

CURRENT_STEP="initialization"

on_error() {
	local exit_code=$?
	trap - ERR
	printf '\nDEPLOY FAILED\n'
	printf 'failed step: %s\n' "$CURRENT_STEP"
	printf 'exit code: %s\n' "$exit_code"
	exit "$exit_code"
}

fail() {
	printf '\nDEPLOY FAILED\n' >&2
	printf 'failed step: %s\n' "$CURRENT_STEP" >&2
	printf '%s\n' "$1" >&2
	exit 1
}

require_command() {
	command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

request_smoke_http_code() {
	curl --fail --silent --location --max-time 30 \
		-o /dev/null -w '%{http_code}' "$SMOKE_URL"
}

public_api_smoke_check() {
	local attempt
	local http_code=""
	for ((attempt = 1; attempt <= SMOKE_MAX_ATTEMPTS; attempt++)); do
		http_code=""
		if http_code="$(request_smoke_http_code "$attempt")" \
			&& [[ "$http_code" =~ ^2[0-9][0-9]$ ]]; then
			SMOKE_HTTP_CODE="$http_code"
			printf 'Public API smoke check: PASS (HTTP %s)\n' "$SMOKE_HTTP_CODE"
			return 0
		fi

		if ((attempt < SMOKE_MAX_ATTEMPTS)); then
			printf 'Public API not ready (%d/%d), retrying in %ss...\n' \
				"$attempt" "$SMOKE_MAX_ATTEMPTS" "$SMOKE_RETRY_INTERVAL_SECONDS"
			sleep "$SMOKE_RETRY_INTERVAL_SECONDS"
		else
			printf 'Public API not ready (%d/%d), attempts exhausted\n' \
				"$attempt" "$SMOKE_MAX_ATTEMPTS"
		fi
	done

	SMOKE_HTTP_CODE="${http_code:-unavailable}"
	return 1
}

normalize_path() {
	local path=$1
	if [[ "$path" =~ ^[A-Za-z]:[\\/].* ]]; then
		require_command cygpath
		cygpath -u "$path"
	else
		printf '%s\n' "$path"
	fi
}

trap on_error ERR

CURRENT_STEP="local preflight"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd -P)"
cd "$REPO_ROOT"

require_command git
require_command ssh
require_command scp
require_command curl

[[ "$SMOKE_RETRY_INTERVAL_SECONDS" =~ ^[1-9][0-9]*$ ]] \
	|| fail "SMOKE_RETRY_INTERVAL_SECONDS must be a positive integer"
[[ "$SMOKE_MAX_ATTEMPTS" =~ ^[1-9][0-9]*$ ]] \
	|| fail "SMOKE_MAX_ATTEMPTS must be a positive integer"

[[ -f pom.xml ]] || fail "pom.xml was not found at $REPO_ROOT"
grep -Fq '<artifactId>mytastelog-server</artifactId>' pom.xml \
	|| fail "This directory is not the expected mytastelog-server repository"

GIT_ROOT="$(git rev-parse --show-toplevel)"
[[ "$(cd -- "$GIT_ROOT" && pwd -P)" == "$REPO_ROOT" ]] \
	|| fail "Script must run from the mytastelog-server repository"

if [[ -x ./mvnw ]]; then
	MAVEN_WRAPPER=(./mvnw)
elif [[ -f ./mvnw.cmd ]] && command -v cmd.exe >/dev/null 2>&1; then
	MAVEN_WRAPPER=(cmd.exe /c mvnw.cmd)
else
	fail "Maven Wrapper was not found or is not executable"
fi

SSH_KEY_PATH="$(normalize_path "$SSH_KEY_CONFIG")"
[[ -f "$SSH_KEY_PATH" && -r "$SSH_KEY_PATH" ]] || fail "SSH key is missing or unreadable: $SSH_KEY_PATH"

BRANCH="$(git branch --show-current)"
HEAD_FULL="$(git rev-parse HEAD)"
HEAD_SHORT="$(git rev-parse --short HEAD)"
GIT_STATUS="$(git status --short)"
if [[ -n "$GIT_STATUS" ]]; then
	WORKING_TREE_STATE="dirty"
else
	WORKING_TREE_STATE="clean"
fi

printf '\nProduction Deployment\n'
printf 'Branch:       %s\n' "${BRANCH:-DETACHED}"
printf 'Commit:       %s (%s)\n' "$HEAD_FULL" "$HEAD_SHORT"
printf 'Target:       %s@%s\n' "$EC2_USER" "$EC2_HOST"
printf 'Service:      %s\n' "$SERVICE_NAME"
printf 'Working tree: %s\n' "$WORKING_TREE_STATE"
if [[ -n "$GIT_STATUS" ]]; then
	printf '\nGit status --short:\n%s\n' "$GIT_STATUS"
fi

printf '\nDeploy? [y/N] '
read -r CONFIRMATION || CONFIRMATION=""
if [[ "$CONFIRMATION" != "y" && "$CONFIRMATION" != "Y" ]]; then
	printf 'DEPLOY CANCELLED\n'
	exit 0
fi

DEPLOY_TIMESTAMP="$(date -u +%Y%m%d-%H%M%S)"
REMOTE_UPLOAD="/home/${EC2_USER}/mytastelog-server-new-${HEAD_SHORT}-${DEPLOY_TIMESTAMP}.jar"
SSH_TARGET="${EC2_USER}@${EC2_HOST}"
SSH_OPTIONS=(-i "$SSH_KEY_PATH" -o BatchMode=yes -o IdentitiesOnly=yes -o StrictHostKeyChecking=yes -o ConnectTimeout=15)

CURRENT_STEP="Maven clean package"
printf '\n[%s] Building and testing Backend\n' "$CURRENT_STEP"
"${MAVEN_WRAPPER[@]}" clean package
[[ -f "$LOCAL_JAR" && -s "$LOCAL_JAR" ]] || fail "Build artifact was not created: $LOCAL_JAR"

CURRENT_STEP="EC2 upload"
printf '\n[%s] Uploading to temporary path %s\n' "$CURRENT_STEP" "$REMOTE_UPLOAD"
scp "${SSH_OPTIONS[@]}" -- "$LOCAL_JAR" "${SSH_TARGET}:${REMOTE_UPLOAD}"

CURRENT_STEP="remote precondition"
printf '\n[%s] Checking service, environment, and JAR paths\n' "$CURRENT_STEP"
printf -v REMOTE_PREFLIGHT_COMMAND 'bash -s -- %q %q %q %q' \
	"$SERVICE_NAME" "$REMOTE_JAR" "$REMOTE_UPLOAD" "$REMOTE_ENV"
ssh "${SSH_OPTIONS[@]}" "$SSH_TARGET" "$REMOTE_PREFLIGHT_COMMAND" <<'REMOTE_PREFLIGHT'
set -Eeuo pipefail
service_name=$1
remote_jar=$2
uploaded_jar=$3
remote_env=$4

sudo -n systemctl cat "$service_name" >/dev/null
sudo -n test -f "$remote_jar"
test -s "$uploaded_jar"
sudo -n test -f "$remote_env"
printf 'REMOTE PRECONDITION PASS\n'
REMOTE_PREFLIGHT

CURRENT_STEP="remote backup, replacement, and service restart"
printf '\n[%s] Backing up and replacing Production JAR\n' "$CURRENT_STEP"
DEPLOY_RESULT_FILE="$(mktemp)"
trap 'rm -f -- "$DEPLOY_RESULT_FILE"' EXIT
printf -v REMOTE_DEPLOY_COMMAND 'bash -s -- %q %q %q %q' \
	"$SERVICE_NAME" "$REMOTE_JAR" "$REMOTE_UPLOAD" "$REMOTE_ENV"
ssh "${SSH_OPTIONS[@]}" "$SSH_TARGET" "$REMOTE_DEPLOY_COMMAND" <<'REMOTE_DEPLOY' | tee "$DEPLOY_RESULT_FILE"
set -Eeuo pipefail
service_name=$1
remote_jar=$2
uploaded_jar=$3
remote_env=$4

remote_step="remote precondition"
on_remote_error() {
	local exit_code=$?
	trap - ERR
	printf 'REMOTE_FAILED_STEP=%s\n' "$remote_step" >&2
	exit "$exit_code"
}
trap on_remote_error ERR

sudo -n systemctl cat "$service_name" >/dev/null
sudo -n test -f "$remote_jar"
test -s "$uploaded_jar"
sudo -n test -f "$remote_env"

remote_step="Production JAR backup"
timestamp=$(date -u +%Y%m%d-%H%M%S)
backup_path="${remote_jar}.backup-${timestamp}"
jar_owner=$(sudo -n stat -c '%u' "$remote_jar")
jar_group=$(sudo -n stat -c '%g' "$remote_jar")
jar_mode=$(sudo -n stat -c '%a' "$remote_jar")

sudo -n test ! -e "$backup_path"
sudo -n cp -a -- "$remote_jar" "$backup_path"
sudo -n test -s "$backup_path"
printf 'BACKUP_PATH=%s\n' "$backup_path"

remote_step="Production JAR replacement"
staged_jar="$(dirname -- "$remote_jar")/.mytastelog-server.jar.new-${timestamp}-$$"
cleanup_staged() {
	if [[ -n "${staged_jar:-}" ]]; then
		sudo -n rm -f -- "$staged_jar"
	fi
}
trap cleanup_staged EXIT

sudo -n install -o "$jar_owner" -g "$jar_group" -m "$jar_mode" -- "$uploaded_jar" "$staged_jar"
sudo -n test -s "$staged_jar"
sudo -n mv -fT -- "$staged_jar" "$remote_jar"
staged_jar=""
sudo -n test -s "$remote_jar"
[[ "$(sudo -n stat -c '%u' "$remote_jar")" == "$jar_owner" ]]
[[ "$(sudo -n stat -c '%g' "$remote_jar")" == "$jar_group" ]]
[[ "$(sudo -n stat -c '%a' "$remote_jar")" == "$jar_mode" ]]
rm -f -- "$uploaded_jar" || true

remote_step="systemd restart"
sudo -n systemctl restart "$service_name"
remote_step="systemd active verification"
service_state=$(sudo -n systemctl is-active "$service_name" || true)
printf 'SERVICE_STATE=%s\n' "$service_state"
[[ "$service_state" == "active" ]]
REMOTE_DEPLOY

BACKUP_PATH="$(awk -F= '$1 == "BACKUP_PATH" { print substr($0, index($0, "=") + 1) }' "$DEPLOY_RESULT_FILE" | tail -n 1)"
SERVICE_STATE="$(awk -F= '$1 == "SERVICE_STATE" { print substr($0, index($0, "=") + 1) }' "$DEPLOY_RESULT_FILE" | tail -n 1)"
[[ -n "$BACKUP_PATH" ]] || fail "Remote deployment did not report a backup path"
[[ "$SERVICE_STATE" == "active" ]] || fail "Service did not report active state"

CURRENT_STEP="public API smoke check"
printf '\n[%s] GET %s\n' "$CURRENT_STEP" "$SMOKE_URL"
if ! public_api_smoke_check; then
	fail "Public API did not become ready after $SMOKE_MAX_ATTEMPTS attempts (last HTTP: $SMOKE_HTTP_CODE)"
fi

trap - ERR
printf '\nDEPLOY SUCCESS\n'
printf 'commit:       %s\n' "$HEAD_FULL"
printf 'backup path:  %s\n' "$BACKUP_PATH"
printf 'service state:%s\n' "$SERVICE_STATE"
printf 'smoke result: HTTP %s\n' "$SMOKE_HTTP_CODE"
