# ERD Cloud rev4.8 생성 설계

## 목표

ERD Cloud의 `nawa rev4.7 최종-snapshot.json`을 기준으로 Flyway V2 변경만 반영한 `nawa rev4.8 최종-snapshot.json`을 저장소 루트에 생성한다. 검토 중 발견한 추가 개선점은 JSON에 반영하지 않고 최종 보고에만 포함한다.

## 입력과 기준

- 기준 ERD: `/Users/jinho/Downloads/nawa rev4.7 최종-snapshot.json`
- 기준 DDL: `backend/src/main/resources/db/migration/V1__init_schema.sql`
- 추가 마이그레이션: `backend/src/main/resources/db/migration/V2__align_wallet_for_stripe_and_pessimistic_lock.sql`
- 논리적 최종 스키마: V1을 적용한 뒤 V2를 순서대로 적용한 결과

## 생성 방식

rev4.7 JSON의 엔티티 ID, 필드 ID, 관계 참조, 좌표, 색상과 작성자 메타데이터를 보존하는 최소 패치 방식을 사용한다.

1. `wallets.fields`에서 `version`을 제거한다.
2. `wallet_topups.fields` 끝에 다음 nullable 컬럼을 V2 선언 순서로 추가한다.
   - `provider VARCHAR(20)`
   - `provider_payment_id VARCHAR(100)`
   - `provider_status VARCHAR(50)`
   - `idempotency_key VARCHAR(100)`
3. 신규 필드마다 기존 ERD Cloud 형식과 충돌하지 않는 `_id`를 부여한다.
4. ERD Cloud snapshot에 별도 UNIQUE 제약 배열이 없으므로 다음 제약을 컬럼 설명에 기록한다.
   - `UNIQUE(provider, provider_payment_id)`
   - `UNIQUE(idempotency_key)`
5. snapshot 생성 시각인 `createdAt`만 갱신하고 나머지 메타데이터는 보존한다.

## 비교와 검증

### rev4.7과 V1

29개 테이블의 이름, 컬럼, 타입, NULL 허용 여부, 기본값, PK와 FK를 구조적으로 비교한다. JSON 형식이 UNIQUE, CHECK, INDEX를 주석으로 표현하는 경우에는 제약 의미를 주석과 대조한다. PK이면서 FK인 컬럼 및 복합 FK처럼 ERD Cloud 모델에서 직접 표현하기 어려운 관계는 형식 차이와 실제 의미 누락을 구분해 보고한다.

### rev4.8과 V1+V2

- 테이블 수와 기존 관계 참조가 유지되는지 확인한다.
- rev4.7 대비 삭제 컬럼 1개와 추가 컬럼 4개만 존재하는지 확인한다.
- V2의 두 UNIQUE 제약이 설명에 보존됐는지 확인한다.
- 모든 엔티티·필드 ID가 중복되지 않고 모든 관계 대상 ID가 존재하는지 확인한다.
- JSON 파싱 및 직렬화 왕복 검사를 수행한다.

## 검토 보고 범위

JSON 수정과 분리해 다음을 보고한다.

- V2 변경 자체의 이상 여부와 운영상 위험
- Stripe 결제 식별자 및 멱등성 컬럼의 길이·NULL·결합 제약
- 낙관적 락 컬럼 제거 후 비관적 락 사용 시 필요한 애플리케이션 전제
- rev4.7에서 관계선으로 충분히 표현되지 않은 복합키 관계
- 중복되거나 불필요할 가능성이 있는 컬럼과 그 판단 근거

## 최종 산출물

- 저장소 루트의 `nawa rev4.8 최종-snapshot.json`
- rev4.7과 rev4.8의 차이 요약
- 회원, 여행·이벤트, 지갑·결제, 보증금, 정산 액터 관점의 Mermaid ERD
- 발견된 개선 후보와 우선순위
