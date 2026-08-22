#!/usr/bin/env bash
set -euo pipefail

readonly PROJECT="nawa-loadtest"
readonly MYSQL_SERVICE="mysql"
readonly SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
readonly REPO_ROOT="$(CDPATH= cd -- "${SCRIPT_DIR}/.." && pwd)"
readonly ENV_FILE="${REPO_ROOT}/.env.loadtest"

vu_count="${VUS:-8920}"
payee_pool_size="${PAYEE_POOL_SIZE:-100}"

if [[ ! ${vu_count} =~ ^[0-9]+$ ]] || ((vu_count < 1 || vu_count > 8920)); then
  echo "VUS는 1~8920 정수여야 합니다: ${vu_count}" >&2
  exit 1
fi
if [[ ! ${payee_pool_size} =~ ^[0-9]+$ ]] || ((payee_pool_size < 1 || payee_pool_size > 100)); then
  echo "PAYEE_POOL_SIZE는 1~100 정수여야 합니다: ${payee_pool_size}" >&2
  exit 1
fi
if [[ ! -f ${ENV_FILE} ]]; then
  echo ".env.loadtest가 없습니다. .env.loadtest.example을 복사해 먼저 채우세요." >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "${ENV_FILE}"
set +a

if [[ -z ${MYSQL_ROOT_PASSWORD:-} || -z ${MYSQL_DATABASE:-} ]]; then
  echo ".env.loadtest의 MYSQL_ROOT_PASSWORD와 MYSQL_DATABASE가 필요합니다." >&2
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

existing="$({
  docker exec -e MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" "${mysql_container}" \
    mysql -uroot "${MYSQL_DATABASE}" --batch --skip-column-names \
    -e "SELECT COUNT(*) FROM members WHERE member_id BETWEEN 900000 AND 999999"
})"
if [[ ${existing} != "0" ]]; then
  echo "부하 테스트 예약 ID 범위에 이미 ${existing}개의 회원이 있습니다." >&2
  echo "README의 '시드 재생성' 절차로 볼륨을 초기화한 뒤 다시 실행하세요." >&2
  exit 1
fi

{
  printf 'SET @vu_count = %s; SET @payee_pool_size = %s;\n' \
    "${vu_count}" "${payee_pool_size}"
  sed -e 's/\r$//' "${SCRIPT_DIR}/seed.sql"
} | docker exec -i -e MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" "${mysql_container}" \
    mysql -uroot "${MYSQL_DATABASE}" --show-warnings

echo "시드 적재 완료: VUS=${vu_count}, PAYEE_POOL_SIZE=${payee_pool_size}"
