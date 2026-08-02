# ERD rev5.0 탐색 도메인 및 Flyway V3 설계

## 목표

`nawa rev4.9 확정-snapshot.json`을 기준으로 Event·Place 공통 부모인
`explore_items` 중심의 탐색 도메인을 반영한 `nawa rev5.0 확정-snapshot.json`을
생성한다. 동시에 V1과 V2가 적용된 MySQL 8 스키마를 rev5.0과 일치시키는
`V3__introduce_explore_domain_and_align_rev5.sql`을 생성한다.

## 최종 범위

- rev4.9의 29개 테이블을 기준으로 신규 11개, 제거 3개를 적용해 37개 테이블로 만든다.
- 신규 테이블은 `categories`, `subcategories`, `explore_items`,
  `explore_item_subcategories`, `explore_item_translations`, `places`,
  `explore_item_likes`, `explore_item_views`, `explore_item_metrics`,
  `member_review_keywords`, `member_review_keyword_selections`다.
- `event_contents`, `event_likes`, `event_views`는 데이터 이관 후 제거한다.
- `events`, `appointments`, `appointment_members`를 탐색 구조에 맞게 변경한다.
- V2의 Stripe 컬럼과 지갑 비관적 잠금 구조, rev4.9의 보증금 변경을 보존한다.

## 탐색 부모·자식 구조

`explore_items.item_id`는 `AUTO_INCREMENT` 독립 PK다. 기존 Event 이관 시에는
기존 `event_id` 값을 명시적으로 같은 `item_id`로 삽입한다.

`events.event_id`와 `places.place_id`는 각각 `explore_items.item_id`를 공유하는
PK+FK이며 `AUTO_INCREMENT`를 사용하지 않는다. `item_type`과 실제 자식 행의
일치는 서비스 생성 트랜잭션에서 검증한다.

`explore_items`의 직접 등록자와 검토자는 모두 `members.member_id`를 참조하는
`created_by_member_id`, `reviewed_by_member_id`로 표현한다. 관리자 권한은 향후
회원 역할 기능에서 검사하며 관리자 전용 테이블은 만들지 않는다.

공식 한국어 도로명주소 하나를 `address_road`에 저장한다. 다국어 주소 컬럼은
추가하지 않는다. `title`, `description`만 언어별 translation 행으로 저장한다.

## 약속 구조

`appointments.event_id`를 `appointments.item_id`로 교체하고
`explore_items.item_id`를 참조한다. `appointment_members`에는 `item_id`를 중복
저장하지 않는다.

다음 기존 컬럼은 정책 결정에 따라 제거한다.

- `meeting_at`
- `meeting_confirmed_at`
- `notice_content`

다음 컬럼을 추가한다.

- `meeting_address`
- `activity_start_at`
- `activity_end_at`
- `host_started_at`

`appointment_status`에는 `IN_PROGRESS`를 추가한다. 일정은
`join_deadline <= activity_start_at < activity_end_at`을 만족해야 한다.
`host_started_at`은 `IN_PROGRESS` 또는 `COMPLETED`일 때 필수이고 그 이전에는
NULL이어야 한다. Event 약속의 활동 시간이 Event 전체 시간 안에 포함되는지는
서비스 트랜잭션에서 검증한다.

`appointment_members`에는 `removed_at`을 추가하고 `removal_reason`을 유지한다.
ACTIVE, LEFT, REMOVED 상태별로 left/removed 시각과 사유의 NULL 조합을 CHECK로
제한한다.

## 분류·상호작용·집계

- `category_code`는 전역 UNIQUE다.
- `subcategory_code`는 같은 category 안에서 UNIQUE다.
- 항목과 세부분류 연결은 `(item_id, subcategory_id)` 복합 PK다.
- 좋아요와 조회는 `(item_id, member_id)` 복합 PK 및 두 FK를 가진다.
- 집계값은 모두 0 이상 CHECK를 가지며 `calculated_at`으로 계산 기준 시각을 남긴다.
- 기존 `events.category_code`는 이관 중 `explore_items.tags` JSON 배열에 보존하고,
  신규 분류 마스터 연결은 별도 운영 데이터 적재로 수행한다.

## 승인·외부 출처 제약

- 외부 항목은 `source`와 `source_item_id`가 모두 존재해야 하고 조합이 UNIQUE다.
- 직접 등록 항목은 두 출처 컬럼이 모두 NULL이고 `created_by_member_id`가 필수다.
- `REVIEW_REQUIRED`, `REJECTED` 항목은 HIDDEN이어야 한다.
- 승인·거절에는 `reviewed_by_member_id`, `reviewed_at`이 필요하고 심사 대기에는
  둘 다 없어야 한다.
- 기존 Event는 ID와 노출을 보존하기 위해 APPROVED로 이관하고, 역사적 검토자는
  기존 creator, 검토 시각은 기존 created_at으로 채운다.

## 후기 구조

V1에 이미 존재하는 `member_review_scores.review_id` FK와 평점 CHECK는 유지한다.
신규 키워드 마스터와 선택 연결 테이블을 추가한다. 제공된 문서에 실제 다섯
`keyword_code` 값이 없으므로 V3는 임의 seed를 만들지 않는다. 애플리케이션은
활성 키워드 중 중복 없이 1~5개 선택을 검증한다.

## rev4.9 보증금 SQL 반영

rev4.9 JSON에서 변경했지만 V1·V2 SQL에는 없는 다음 내용을 V3에 포함한다.

- `deposits.deposit_status`에서 FORFEITED 제거
- `deposit_payout_batches.resolution_reason` 추가
- `deposit_payouts.allocation_type`에 CANCELLATION_REFUND 추가
- 지급자·수취자 출석 snapshot 두 컬럼 추가
- 행 내부에서 표현 가능한 상태·합계 CHECK 갱신

기존 FORFEITED 행은 ENUM 축소 전에 DISTRIBUTED로 이관한다. 기존 payout의 출석
snapshot은 source deposit과 recipient appointment member의 현재 출석 상태로
채운 뒤 NOT NULL로 변경한다.

## V3 데이터 이관 순서

1. rev4.9 보증금 변경을 적용한다.
2. 신규 탐색 부모·분류·번역·Place·상호작용·집계 테이블을 생성한다.
3. 기존 Event ID를 유지해 explore_items와 translations, likes, views로 이관한다.
4. events를 공통 부모의 PK+FK 자식으로 축소한다.
5. appointments를 item 기반 및 activity 시간 기반 구조로 변경한다.
6. appointment_members에 removed_at과 상태 CHECK를 추가한다.
7. 집계 초기값을 기존 데이터로 계산한다.
8. 기존 event_contents, event_likes, event_views를 제거한다.
9. 후기 키워드 테이블을 생성한다.

## 검증

- V1→V2→V3 SQL에서 최종 테이블 수가 37개인지 확인한다.
- rev5.0 JSON도 37개 테이블이며 SQL과 테이블·컬럼·PK·FK가 일치하는지 비교한다.
- 모든 JSON 엔티티·필드 ID가 유일하고 관계 대상이 존재하는지 확인한다.
- V2 Stripe 컬럼과 rev4.9 보증금 필드가 유지되는지 확인한다.
- 제거 대상 3개 테이블과 약속 제거 컬럼이 양쪽 모두 존재하지 않는지 확인한다.
- SQL의 FK, UNIQUE, CHECK 이름 중복과 참조 순서를 검사한다.
- 가능하면 로컬 MySQL 8 임시 스키마에서 V1, V2, V3를 순서대로 실행한다.
