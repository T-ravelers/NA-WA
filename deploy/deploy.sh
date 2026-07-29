#!/bin/bash
set -euo pipefail

APP_DIR=~/nawa

echo "[deploy] $(date '+%Y-%m-%d %H:%M:%S') 배포 시작"

cd "$APP_DIR"

echo "[deploy] 최신 이미지 pull"
docker compose pull

echo "[deploy] 컨테이너 재기동 (변경된 서비스만 재생성)"
docker compose up -d

echo "[deploy] 사용하지 않는 이미지 정리"
docker image prune -f

echo "[deploy] 현재 상태"
docker compose ps

echo "[deploy] $(date '+%Y-%m-%d %H:%M:%S') 배포 완료"
