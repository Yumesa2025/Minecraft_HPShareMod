#!/usr/bin/env bash
# SharedFate 서버를 켭니다 (Linux · macOS). 이 파일과 sharedfate-server-loop.sh 를
# 서버 폴더에 함께 두고 실행하십시오.
#
#   chmod +x start-sharedfate-server.sh sharedfate-server-loop.sh
#   ./start-sharedfate-server.sh
#
# 메모리는 아래 두 값을 고쳐서 정합니다. 4인 기준 4G 를 권합니다.

set -u
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

exec "$HERE/sharedfate-server-loop.sh" \
	--root "$HERE" \
	--jar "fabric-server-launch.jar" \
	--min "2G" \
	--max "4G"
