# 정산 단순화 검증 기록

정산 변경은 단위 테스트, 기본 backend build, MySQL opt-in 통합 테스트를 구분해서
기록합니다. 실행하지 않은 항목은 성공으로 표시하지 않습니다.

## 검증 범위

| 영역 | 자동 검증 |
| --- | --- |
| 공통 금액 배분 | 통화 소수 자릿수, 최소 단위 나머지, 합계 보존 |
| EQUAL 생성 | 선택한 ACTIVE 참가자, 최소 통화 단위 나머지, 합계 보존 |
| ITEMIZED 생성 | 수동 품목·수량·참가자별 수량 배분, 품목/참가자/원거래 금액 일치, 스냅샷 저장 |
| 상태 전이 | 생성 즉시 `REQUESTED`, 모든 `PENDING` 지급 후 `COMPLETED` |
| 멱등성 | create/pay 동일 키 재시도와 다른 키 409, 원거래 중복 방지 |
| HTTP 계약 | 유지 API 5개, create/pay 헤더 누락 400, 제거된 request/cancel/game/receipt POST 동작 미노출 |
| MySQL | V9 적용, 축소 ENUM·제약, mapper SQL, 생성 동시성의 승자 재조회 |

## 실행 명령

```shell
cd backend
./gradlew test --tests 'me.nawa.settlement.*' --no-daemon
./gradlew build --no-daemon
RUN_MYSQL_INTEGRATION_TESTS=true ./gradlew test --no-daemon
```

## 현재 로컬 결과

- `2026-08-12 ./gradlew build --no-daemon`: 통과. 전체 단위 테스트와 WAR 생성 포함.
- MySQL opt-in 통합 테스트는 이번 검증에서 실행하지 않았다.

## V9 스키마 확인 항목

- `settlements.split_method`: `EQUAL`, `ITEMIZED`
- `settlements.settlement_status`: `REQUESTED`, `COMPLETED`
- `settlement_members.request_status`: `NOT_REQUESTED`, `PENDING`, `PAID`
- 생성 멱등성: `idempotency_key`, `request_fingerprint`,
  `(created_by_member_id, idempotency_key)` UNIQUE
- 지급 멱등성: `payment_idempotency_key`
- 생성되지 않아야 할 테이블: `receipt_analyses`, `receipt_analysis_items`,
  `receipt_item_allocations`, `settlement_games`, `settlement_game_members`

V9은 이 기능 브랜치가 아직 병합·배포되지 않았다는 전제에서 수정했습니다. 이미 어느
환경에서든 V9이 적용된 사실이 확인되면 V9을 되돌려 고치지 않고 새 migration으로
분리해야 합니다.
