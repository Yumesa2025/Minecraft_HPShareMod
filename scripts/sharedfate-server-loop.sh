#!/usr/bin/env bash
# SharedFate 서버 재시작 루프 (Linux · macOS)
#
# 팀이 전멸하면 모드는 월드를 직접 지우지 않습니다. 서버 루트에 표식 파일을 하나 남기고
# 정상 종료할 뿐이고, 표식에 적힌 월드 폴더를 지우고 서버를 다시 켜는 일은 이 스크립트가
# 합니다. `java -jar` 로 직접 띄우면 전멸 뒤 서버가 다시 켜지지 않습니다.
#
# 사용법:
#   ./sharedfate-server-loop.sh [--root <서버폴더>] [--jar <파일>] [--java <실행파일>]
#                               [--min <1G>] [--max <4G>] [--process-pending-reset-only]
#
# Windows 에서는 같은 폴더의 sharedfate-server-loop.ps1 을 쓰십시오.

set -u

MARKER_NAME='.sharedfate-world-reset.pending'
MARKER_HEADER='sharedfate-world-reset-v1'
RUN_STATE_NAME='sharedfate-run-state.json'

SERVER_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVA_EXECUTABLE='java'
JAR_FILE='fabric-server-launch.jar'
MIN_MEMORY='1G'
MAX_MEMORY='2G'
PENDING_ONLY=0

die() {
	printf '[SharedFate] %s\n' "$1" >&2
	exit 1
}

while [ $# -gt 0 ]; do
	case "$1" in
		--root) SERVER_ROOT="${2:-}"; shift 2 ;;
		--jar) JAR_FILE="${2:-}"; shift 2 ;;
		--java) JAVA_EXECUTABLE="${2:-}"; shift 2 ;;
		--min) MIN_MEMORY="${2:-}"; shift 2 ;;
		--max) MAX_MEMORY="${2:-}"; shift 2 ;;
		--process-pending-reset-only) PENDING_ONLY=1; shift ;;
		*) die "알 수 없는 인자입니다: $1" ;;
	esac
done

[ -d "$SERVER_ROOT" ] || die "서버 폴더가 없습니다: $SERVER_ROOT"
SERVER_ROOT="$(cd "$SERVER_ROOT" && pwd -P)"
MARKER_PATH="$SERVER_ROOT/$MARKER_NAME"
RUN_STATE_PATH="$SERVER_ROOT/$RUN_STATE_NAME"

# 회차 번호를 읽는다. 파일이 없으면 1회차로 본다.
read_run_number() {
	if [ ! -e "$RUN_STATE_PATH" ]; then
		printf '1'
		return
	fi
	[ -f "$RUN_STATE_PATH" ] || die "회차 상태가 파일이 아닙니다: $RUN_STATE_PATH"
	local value
	value="$(sed -n 's/.*"runNumber"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p' \
		"$RUN_STATE_PATH" | head -n 1)"
	case "$value" in
		''|*[!0-9]*) die "회차 번호가 올바르지 않습니다: $RUN_STATE_PATH" ;;
	esac
	[ "$value" -ge 1 ] || die "회차 번호가 올바르지 않습니다: $value"
	printf '%s' "$value"
}

write_run_state() {
	local number="$1"
	local temporary="$RUN_STATE_PATH.tmp"
	printf '{\n  "runNumber": %s,\n  "status": "playing",\n  "winningTeam": ""\n}\n' \
		"$number" > "$temporary" || die "회차 상태를 쓰지 못했습니다: $temporary"
	mv -f "$temporary" "$RUN_STATE_PATH" || die "회차 상태를 옮기지 못했습니다: $RUN_STATE_PATH"
}

# 표식에 적힌 월드 폴더 하나만 검증해서 지운다. 검증에 하나라도 걸리면 아무것도 지우지 않는다.
process_pending_reset() {
	[ -f "$MARKER_PATH" ] || die "월드 초기화 표식이 없습니다: $MARKER_PATH"

	local header world_target world_parent extra
	header="$(sed -n '1p' "$MARKER_PATH")"
	world_target="$(sed -n '2p' "$MARKER_PATH")"
	extra="$(sed -n '3,$p' "$MARKER_PATH" | tr -d '[:space:]')"
	[ "$header" = "$MARKER_HEADER" ] || die "표식 형식이 올바르지 않습니다: $MARKER_PATH"
	[ -z "$extra" ] || die "표식에 줄이 더 있습니다: $MARKER_PATH"
	[ -n "$world_target" ] || die "표식에 월드 경로가 없습니다: $MARKER_PATH"
	case "$world_target" in
		/*) ;;
		*) die "월드 경로는 절대 경로여야 합니다: $world_target" ;;
	esac

	world_target="${world_target%/}"
	world_parent="$(dirname "$world_target")"
	[ "$world_parent" = "$SERVER_ROOT" ] || \
		die "월드 폴더는 서버 루트 바로 아래여야 합니다: $world_target"
	[ "$world_target" != "$SERVER_ROOT" ] || die '서버 루트 자체는 절대 삭제할 수 없습니다.'
	[ ! -L "$world_target" ] || die "링크 월드는 자동 삭제하지 않습니다: $world_target"
	[ -d "$world_target" ] || die "삭제할 월드 폴더가 없습니다: $world_target"

	printf '[SharedFate] 검증된 월드 한 폴더를 초기화합니다: %s\n' "$world_target"
	rm -rf "$world_target" || die "월드 폴더를 지우지 못했습니다: $world_target"
	[ ! -e "$world_target" ] || die "월드 폴더 삭제가 완료되지 않았습니다: $world_target"

	local current next
	current="$(read_run_number)"
	next="$((current + 1))"
	write_run_state "$next"
	rm -f "$MARKER_PATH" || die "표식을 지우지 못했습니다: $MARKER_PATH"
	printf '[SharedFate] 월드 초기화 완료. %s회차 새 월드를 생성합니다.\n' "$next"
}

INITIAL_RUN_NUMBER="$(read_run_number)"
[ -e "$RUN_STATE_PATH" ] || write_run_state "$INITIAL_RUN_NUMBER"

if [ "$PENDING_ONLY" -eq 1 ]; then
	process_pending_reset
	exit 0
fi

[ ! -e "$MARKER_PATH" ] || \
	die "이전 실행의 월드 초기화 표식이 남아 있습니다. 자동 재시도를 막았습니다: $MARKER_PATH"

JAR_PATH="$SERVER_ROOT/$JAR_FILE"
[ "$(dirname "$JAR_PATH")" = "$SERVER_ROOT" ] && [ -f "$JAR_PATH" ] || \
	die "서버 실행 JAR 가 서버 루트 바로 아래에 없습니다: $JAR_PATH"

cd "$SERVER_ROOT" || die "서버 폴더로 이동하지 못했습니다: $SERVER_ROOT"
while true; do
	printf '[SharedFate] Minecraft 서버를 시작합니다.\n'
	"$JAVA_EXECUTABLE" "-Xms$MIN_MEMORY" "-Xmx$MAX_MEMORY" \
		-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 \
		-jar "$JAR_PATH" nogui
	exit_code=$?

	if [ ! -e "$MARKER_PATH" ]; then
		printf '[SharedFate] 서버가 종료되었습니다. exit=%s\n' "$exit_code"
		exit "$exit_code"
	fi

	process_pending_reset
	sleep 2
done
