#!/usr/bin/env bash
# 시나리오 4(공개 프로필 집계, #582)의 집계 쿼리를 EXPLAIN ANALYZE로 실행한다.
#
# 쿼리는 매퍼 XML에서 그대로 꺼낸다. 이 파일에 SQL을 옮겨 두면 쿼리를 고칠 때(#583)
# 둘이 어긋나기 때문이다. 인자로 받은 회원마다 계획 하나를 출력한다(기본: 풀 회원 셋).
set -euo pipefail

readonly PROJECT="nawa-loadtest"
readonly MYSQL_SERVICE="mysql"
readonly SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
readonly REPO_ROOT="$(CDPATH= cd -- "${SCRIPT_DIR}/.." && pwd)"
readonly ENV_FILE="${REPO_ROOT}/.env.loadtest"
readonly MAPPER="${REPO_ROOT}/backend/src/main/resources/me/nawa/member/mapper/MemberMapper.xml"

if [[ $# -eq 0 ]]; then
  set -- 980001 980500 981000
fi

if [[ ! -f ${ENV_FILE} ]]; then
  echo ".env.loadtest가 없습니다. .env.loadtest.example을 복사해 먼저 채우세요." >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "${ENV_FILE}"
set +a

# findAppointmentProfile의 본문만 꺼낸다. 여는 태그가 두 줄이라 SELECT부터 읽는다.
query="$(awk '
  /id="findAppointmentProfile"/ { found = 1; next }
  found && /<\/select>/ { exit }
  found && !started && /^[[:space:]]*SELECT/ { started = 1 }
  started { print }
' "${MAPPER}" | sed -e 's/&lt;/</g' -e 's/&gt;/>/g' -e 's/&amp;/\&/g')"

if [[ -z ${query} ]]; then
  echo "${MAPPER}에서 findAppointmentProfile을 찾지 못했습니다." >&2
  exit 1
fi
if [[ ${query} == *"<"[a-z]* ]]; then
  echo "findAppointmentProfile에 MyBatis 동적 태그가 있어 그대로 실행할 수 없습니다." >&2
  exit 1
fi

mysql_container="$({
  docker compose -p "${PROJECT}" \
    -f "${REPO_ROOT}/docker-compose.yml" \
    -f "${REPO_ROOT}/docker-compose.ec2-clone.yml" \
    ps -q "${MYSQL_SERVICE}"
} | head -n 1)"

if [[ -z ${mysql_container} ]]; then
  echo "${PROJECT} MySQL 컨테이너가 떠 있지 않습니다." >&2
  exit 1
fi

for member_id in "$@"; do
  if [[ ! ${member_id} =~ ^[0-9]+$ ]]; then
    echo "회원 번호는 정수여야 합니다: ${member_id}" >&2
    exit 1
  fi
  echo "== member_id=${member_id}"
  printf 'EXPLAIN ANALYZE %s\\G\n' "${query//\#\{memberId\}/${member_id}}" |
    docker exec -i -e MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" "${mysql_container}" \
      mysql -uroot "${MYSQL_DATABASE}" --skip-column-names
done
