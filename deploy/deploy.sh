#!/bin/bash
set -euo pipefail

APP_DIR=~/nawa

echo "[deploy] $(date '+%Y-%m-%d %H:%M:%S') 배포 시작"

cd "$APP_DIR"

echo "[deploy] 최신 이미지 pull"
docker compose pull

echo "[deploy] 컨테이너 재기동 (변경된 서비스만 재생성)"
docker compose up -d

echo "[deploy] 헬스 체크"
healthy=false
for i in $(seq 1 10); do
  code=$(curl -s -o /dev/null -w '%{http_code}' http://localhost/ || true)
  if [ "$code" != "000" ]; then
    echo "[deploy] 응답 확인됨 (HTTP $code, ${i}번째 시도)"
    healthy=true
    break
  fi
  echo "[deploy] 아직 응답 없음, 3초 대기 (${i}/10)"
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
