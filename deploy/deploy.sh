#!/bin/bash
set -euo pipefail

APP_DIR=~/nawa

echo "[deploy] $(date '+%Y-%m-%d %H:%M:%S') 배포 시작"

cd "$APP_DIR"

echo "[deploy] 최신 이미지 pull"
docker compose pull

echo "[deploy] 컨테이너 재기동 (변경된 서비스만 재생성)"
docker compose up -d

# bind mount라 파일은 갱신돼도 nginx 프로세스는 재시작 전까지 기존 설정을 계속 씀
echo "[deploy] nginx 설정 검증 및 반영"
docker compose exec -T nginx nginx -t
docker compose exec -T nginx nginx -s reload

echo "[deploy] 헬스 체크"
healthy=false
for i in $(seq 1 10); do
  # HTTP는 이제 항상 308 리다이렉트만 반환하므로, 실제 TLS/SNI와 backend까지
  # 통과하는 HTTPS 경로를 직접 검사한다. DNS 대신 로컬 nginx로 강제 resolve.
  code=$(curl -s -o /dev/null -w '%{http_code}' --resolve api.clearpng.cloud:443:127.0.0.1 https://api.clearpng.cloud/ || true)
  # 000(무응답)과 5xx(서버 오류)는 실패로 간주 - 2xx/4xx만 정상 응답으로 취급
  if [[ "$code" =~ ^[24][0-9]{2}$ ]]; then
    echo "[deploy] 응답 확인됨 (HTTP $code, ${i}번째 시도)"
    healthy=true
    break
  fi
  echo "[deploy] 정상 응답 아님 (HTTP $code), 3초 대기 (${i}/10)"
  sleep 3
done

if [ "$healthy" = false ]; then
  echo "[deploy] 헬스 체크 실패 - 컨테이너 로그"
  docker compose logs --tail=50 backend
  echo "[deploy] 배포 실패로 처리"
  exit 1
fi

echo "[deploy] 사용하지 않는 이미지 정리"
docker image prune -f

echo "[deploy] 현재 상태"
docker compose ps

echo "[deploy] $(date '+%Y-%m-%d %H:%M:%S') 배포 완료"
