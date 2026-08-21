# 약속·보증금·후기 도메인 ENUM 및 상태 전이

이 문서는 약속(Appointment)·보증금(Deposit)·후기(Review) 도메인의 ENUM, 상태 전이,
현재 구현 여부를 정리합니다. HTTP 요청·응답 계약은
[APPOINTMENT_API.md](APPOINTMENT_API.md)를 따르고, 이 문서는 그 뒤에 있는 도메인
모델과 상태 머신을 다룹니다.

## 1. 현재 구현 상태

- 상태 ENUM과 `canTransitionTo()` 규칙: `Appointment`·`Deposit`·`DepositPayoutBatch`
  도메인 객체와 매퍼(CRUD)까지 구현돼 있습니다.
- 약속 생성·참여: 실제로 동작합니다. 보증금 예치(회원 지갑 → `DEPOSIT_POOL`)와
  `AppointmentStatus`/`MembershipStatus` 전환이 같은 트랜잭션에서 함께 끝납니다
  (14절).
- 방장이 아닌 회원의 참여 취소: 실제로 동작합니다. 활동 시작 전에는 `HELD`
  보증금 환급과 상태 전환이 같은 트랜잭션에서 함께 끝나고, 활동 시작~종료
  사이에는 노쇼로 굳어 보증금이 정산 분배로 넘어갑니다(12절).
- 약속 lifecycle 자동 전이(`RECRUITING → CLOSED → IN_PROGRESS`): 실제로
  동작합니다(15절).
- 출석 확정(`IN_PROGRESS → COMPLETED`): 실제로 동작합니다. 방장이 `ACTIVE` 회원
  전원의 출석을 정확히 한 번씩 확정하면, 같은 트랜잭션에서 약속을 `COMPLETED`로
  전환하고 `DepositPayoutBatch`를 `PENDING`으로 만들어둡니다.
- 정산 배치 비동기 처리: 실제로 동작합니다(16절). 출석 회원에게는 본인 보증금을
  환급하고, 노쇼 회원의 보증금은 출석 회원에게 균등 분배합니다.
- 기존 완료 약속에 대한 후기 등록은 이미 동작합니다.

## 2. 약속 상태 `AppointmentStatus`

DB 컬럼: `appointments.appointment_status`

| 상태 | 의미 |
| --- | --- |
| `PAYMENT_PENDING` | 방장의 보증금 결제를 기다리는 약속 생성 대기 상태 |
| `RECRUITING` | 방장 결제가 완료되어 참가자를 모집하는 상태 |
| `CLOSED` | 참여 마감 시각 도달 또는 정원 충족으로 모집이 종료된 상태 |
| `CONFIRMED` | 코드에는 정의돼 있으나, 활동 시작 시각에 방장 확정 없이 바로 `IN_PROGRESS`로 넘어가기로 정하면서 실제로 도달하는 경로가 없는 상태(`CANCELLED`와 같은 취급) |
| `IN_PROGRESS` | 활동 시작 시각에 도달하여 약속이 진행 중인 상태 |
| `AWAITING_ATTENDANCE` | 활동 종료 시각이 지났지만 방장이 아직 출석을 확정하지 않은 상태. **표시 전용** — DB에는 저장되지 않고 조회 응답이 `resolveDisplayStatus`에서 시간 기준으로 계산해 내보냅니다(15절). DB 컬럼은 출석 확정 전까지 `IN_PROGRESS`를 유지합니다. |
| `COMPLETED` | 출석 확정과 약속 진행이 모두 끝난 상태 |
| `CANCELLED` | 코드에는 정의돼 있으나, 17절 정책에 따라 실제로 도달하는 경로가 없는 상태 |

허용 상태 전이(코드 `AppointmentStatus.canTransitionTo()` 기준):

    PAYMENT_PENDING
    ├─→ RECRUITING
    └─→ CANCELLED   # 17절 참고 — 현재 트리거 없음

    RECRUITING
    ├─→ CLOSED
    └─→ CANCELLED   # 17절 참고 — 현재 트리거 없음

    CLOSED
    ├─→ RECRUITING  # 정원 도달로 CLOSED된 뒤 마감 시각 전 참여 취소로 빈자리 발생
    ├─→ IN_PROGRESS
    └─→ CANCELLED   # 17절 참고 — 현재 트리거 없음

    IN_PROGRESS
    └─→ COMPLETED

    COMPLETED
    └─→ 전이 불가

    CONFIRMED
    └─→ 전이 불가   # 위 표 참고 — 현재 트리거 없음

    CANCELLED
    └─→ 전이 불가

전이 조건(실제로 도달 가능한 경로만):

| 전이 | 조건 |
| --- | --- |
| `PAYMENT_PENDING → RECRUITING` | 방장의 보증금 결제와 `DEPOSIT_POOL` 예치가 같은 트랜잭션에서 완료. 예치가 실패하면 트랜잭션이 롤백되어 약속 행 자체가 생기지 않으므로, `PAYMENT_PENDING`은 DB에 지속적으로 남는 상태가 아니라 같은 트랜잭션 안에서만 존재합니다. |
| `RECRUITING → CLOSED` | 정원 도달은 `joinAppointment`가 참여 성공 트랜잭션 안에서 즉시 동기로 전환. 참여 마감 시각 도달은 시간 기반이라 스케줄러가 60초 주기로 전환(15절). |
| `CLOSED → RECRUITING` | 정원 도달로 `CLOSED`된 약속에서, 마감 시각(`joinDeadline`) 전에 참여 취소가 발생해 빈자리가 생기면 `leaveAppointment`가 같은 트랜잭션에서 즉시 되돌립니다. 마감 시각이 지난 뒤의 참여 취소(12절)에서는 빈자리가 생겨도 새로 참여할 수 없으므로 `CLOSED`를 유지합니다. |
| `CLOSED → IN_PROGRESS` | `activityStartAt` 도달. 방장의 별도 확정 액션 없이 스케줄러가 시간만 보고 전환(15절). |
| `IN_PROGRESS → COMPLETED` | 방장이 모든 `ACTIVE` 회원의 출석을 확정. `activityEndAt`이 지난 뒤에만 받습니다(4절). |
| `* → CANCELLED` | 코드에는 정의돼 있으나 17절 정책에 따라 트리거하는 API·스케줄러를 만들지 않습니다. |

## 3. 약속 회원 상태 `MembershipStatus`

DB 컬럼: `appointment_members.membership_status`

| 상태 | 의미 |
| --- | --- |
| `PENDING` | 참여 의사를 확인했지만 보증금 결제가 완료되지 않은 상태 |
| `ACTIVE` | 보증금 결제와 예치가 완료되어 참여가 확정된 상태 |
| `LEFT` | 참여를 취소하거나 약속에서 나간 최종 상태 |

허용 상태 전이:

    PENDING
    ├─→ ACTIVE
    └─→ LEFT

    ACTIVE
    └─→ LEFT

    LEFT
    └─→ PENDING   # 마감 시각 전 재참여. 기존 행을 재활용(아래 참고)

전이 조건:

| 전이 | 조건 |
| --- | --- |
| `PENDING → ACTIVE` | 보증금 결제 성공 및 `PENDING → HELD` 예치 완료 |
| `PENDING → LEFT` | 보증금 결제 전 참여 취소. **방장은 대상이 아닙니다** — 아래 참조. |
| `ACTIVE → LEFT` | 참여 취소. 활동 시작 전에는 예치된 보증금 환급까지 같은 트랜잭션에서 끝나고, 활동 시작~종료 사이에는 노쇼로 굳어 보증금이 `HELD`로 남습니다(12절). 활동 종료 후에는 취소할 수 없습니다. **방장은 대상이 아닙니다.** |
| `LEFT → PENDING` | 참여 취소한 회원이 참여 마감 시각 전에 같은 약속에 다시 참여. `appointment_members`는 `(appointment_id, member_id)` UNIQUE, `deposits`는 `appointment_member_id` UNIQUE라 새 행을 만들 수 없어 `joinAppointment`가 기존 `appointment_member`·`deposit` 행을 `PENDING`으로 되돌려 재사용합니다(이후 정상적인 `PENDING → ACTIVE`와 동일하게 예치 진행). |

**방장의 참여 취소 정책 (17절과 함께 확정)**

방장은 `PENDING`이든 `ACTIVE`든 상태와 무관하게 **자기 참여를 취소할 수 없습니다.**
`DELETE /appointments/{id}/members/me`를 방장이 호출하면 항상
`APPOINTMENT-007`(`CANCELLATION_NOT_AVAILABLE`)을 반환합니다. 과거에는 승계 가능한
다른 `PENDING` 회원이 있으면 방장 권한을 넘기고 취소를 허용했지만, 이 승계 로직은
제거했습니다(`AppointmentMapper.findHostSuccessorForUpdate`,
`updateHostMember` 삭제). 방장이 아닌 참여자만 참여를 취소할 수 있습니다.

참고: 과거의 `REMOVED` 상태는 현재 ENUM에서 제거됐고, 현재 최종 회원 상태는
`PENDING`, `ACTIVE`, `LEFT` 세 가지입니다.

## 4. 출석 상태 `AttendanceStatus`

DB 컬럼: `appointment_members.attendance_status`
Java 패키지: `me.nawa.deposit.domain`(보증금 정산이 출석 결과를 참조하기 때문에 이
패키지에 있습니다. `appointment` 패키지가 아닌 점에 주의하세요.)

| 상태 | 의미 |
| --- | --- |
| `PENDING` | 출석 여부가 아직 확정되지 않은 상태 |
| `ATTENDED` | 약속에 참석한 상태 |
| `NO_SHOW` | 약속에 참석하지 않은 상태 |

허용 상태 전이:

    PENDING
    ├─→ ATTENDED
    └─→ NO_SHOW

    ATTENDED / NO_SHOW
    └─→ 전이 불가

`PENDING → NO_SHOW`는 방장의 출석 확정 외에 한 경로가 더 있습니다 — 활동
시작~종료 사이의 참여 취소가 같은 트랜잭션에서 노쇼를 굳힙니다(12절). 이렇게
굳은 회원은 `LEFT`라 아래 출석 확정 대상(`ACTIVE` 전원)에는 들어가지 않고,
이미 확정된 결과로 남습니다.

출석 확정 조건:

- 약속 상태가 `IN_PROGRESS`
- **활동 종료 시각(`activityEndAt`)이 지났음** — `IN_PROGRESS`는 활동 *시작*
  시각에 스케줄러가 붙이므로 상태만으로는 활동 도중 확정을 막지 못합니다. 늦게
  도착한 참여자가 노쇼로 굳으면 보증금을 잃는데 되돌리는 전이가 없습니다.
  지나지 않았거나 값을 읽지 못하면 `APPOINTMENT-009`
- 요청자는 해당 약속의 방장
- 대상은 약속의 모든 `ACTIVE` 회원
- 각 회원을 요청에 정확히 한 번씩 포함
- 이미 확정한 출석 결과는 다시 변경하지 않음

## 5. 보증금 상태 `DepositStatus`

DB 컬럼: `deposits.deposit_status`

| 상태 | 의미 |
| --- | --- |
| `PENDING` | 보증금 결제 및 예치 대기 |
| `HELD` | 회원 지갑에서 `DEPOSIT_POOL`로 예치 완료 (코드 주석엔 이전 이름인 `SYSTEM_ESCROW`로 남아있음, 14절 참고) |
| `REFUNDED` | 원래 회원에게 환급 완료 |
| `DISTRIBUTED` | 노쇼 회원의 보증금을 참석 회원에게 분배 완료 |
| `CANCELLED` | 실제 예치 전에 결제 또는 참여가 취소된 상태 |

허용 상태 전이:

    PENDING
    ├─→ HELD
    └─→ CANCELLED

    HELD
    ├─→ REFUNDED
    └─→ DISTRIBUTED

    REFUNDED
    └─→ PENDING   # 마감 시각 전 재참여. MembershipStatus의 LEFT → PENDING과 함께 일어남

    DISTRIBUTED / CANCELLED
    └─→ 전이 불가

중요한 금융 정합성 규칙(코드로 강제됨, `Deposit.java`):

- `HELD`가 되려면 `heldTransferId`, `heldAt`이 반드시 있어야 합니다.
- `REFUNDED`, `DISTRIBUTED`가 되려면 `resolvedAt`이 반드시 있어야 합니다.
- 실제 지갑 이체와 상태 변경은 같은 트랜잭션에서 처리합니다.
- `REFUNDED → PENDING`(재참여)은 `heldTransferId`·`heldAt`·`resolvedAt`을 모두
  `NULL`로 되돌립니다. `deposits.appointment_member_id`가 UNIQUE라 같은 참여
  행에 새 보증금 행을 만들 수 없어, 참여 취소로 환급된 기존 행을 재사용합니다.

참고: 과거 `FORFEITED` 상태는 제거됐고 기존 `FORFEITED` 데이터는 `DISTRIBUTED`로
정규화됐습니다(V3 마이그레이션에서 이미 완료).

## 6. 보증금 정산 배치 상태 `ResolutionStatus`

| 상태 | 의미 |
| --- | --- |
| `PENDING` | 정산 대기 |
| `PROCESSING` | 환급·분배 처리 중 |
| `COMPLETED` | 정산 완료 |
| `FAILED` | 정산 실패, 재처리 가능 |

허용 상태 전이:

    PENDING → PROCESSING
    PROCESSING → COMPLETED | FAILED
    FAILED → PROCESSING
    COMPLETED → 전이 불가

정산 사유 `ResolutionReason`:

| 값 | 의미 | 현재 사용 여부 |
| --- | --- | --- |
| `APPOINTMENT_COMPLETED` | 약속 정상 완료 후 출석 결과에 따른 정산 | 사용함 (16절) |
| `APPOINTMENT_CANCELLED` | 약속 취소 후 보증금 환급 | 코드엔 있으나 17절 정책상 트리거 없음 |

보증금 분배 유형 `AllocationType`:

| 값 | 의미 | 현재 사용 여부 |
| --- | --- | --- |
| `SELF_REFUND` | 참석 회원에게 본인 보증금 환급 | 사용함 |
| `NO_SHOW_SHARE` | 노쇼 회원 보증금을 참석 회원에게 분배 | 사용함 |
| `CANCELLATION_REFUND` | 약속 취소로 원소유자에게 환급 | 코드엔 있으나 17절 정책상 트리거 없음 |

**알려진 DB 결함(참고용, 조치 불필요)**: `deposit_payouts.allocation_type`의 실제
DB `ENUM`은 `'SELF_REFUND'`, `'NO_SHOW_SHARE'`만 허용하고 `'CANCELLATION_REFUND'`가
빠져 있습니다(`V1__init_schema.sql`). `CANCELLATION_REFUND`를 저장하는 코드를 만들지
않기로 했으므로(17절) 지금은 영향이 없지만, 이 값을 쓰는 기능을 나중에 추가한다면
먼저 이 `ENUM`을 넓히는 마이그레이션이 선행돼야 합니다.

## 7. 후기 분류 `ReviewCategory`

DB 컬럼: `member_review_scores.review_category`

| 값 | 의미 |
| --- | --- |
| `PUNCTUALITY` | 시간 준수 |
| `MANNERS` | 매너 |
| `COMMUNICATION` | 의사소통 |

후기 점수 규칙: 세 항목 모두 입력, 각 항목 1~5 정수. 상태 전이 ENUM이 아니라 후기
점수의 분류 코드이며, 저장된 후기를 수정하는 상태 전이는 없습니다.

후기 작성 조건: 약속 상태가 `COMPLETED`, 작성자와 평가 대상 모두
`MembershipStatus.ACTIVE`이자 `AttendanceStatus.ATTENDED`, 자기 자신 평가 불가,
동일 약속에서 같은 회원에게 중복 후기 작성 불가.

## 8. 후기 키워드 `ReviewKeywordCode`

| 값 | 화면 문구 |
| --- | --- |
| `FRIENDLY` | Friendly |
| `ON_TIME` | On time |
| `CONSIDERATE` | Considerate |
| `GOOD_COMMUNICATOR` | Good communicator |
| `WOULD_JOIN_AGAIN` | Would join again |

0~5개 선택, 중복 선택 불가. DB엔 안정적인 코드만 저장하고 화면 문구는 Vue i18n에서
관리합니다. 상태 전이 ENUM이 아니라 후기 선택 코드입니다.

## 9. 후기 노출 상태

DB 컬럼: `member_reviews.visibility_status`

| 상태 | 의미 |
| --- | --- |
| `VISIBLE` | 정상 노출 |
| `HIDDEN` | 숨김 처리 |

후기 생성 시 항상 `VISIBLE`입니다. `VISIBLE → HIDDEN`(신고·관리자 숨김)과
`HIDDEN → VISIBLE` 복구는 현재 API가 없는 후속 범위입니다.

## 10. 약속 대상 유형

DB 컬럼: `appointments.item_type`

| 값 | 의미 |
| --- | --- |
| `EVENT` | Event 상세에서 생성하는 약속 |
| `PLACE` | Place 상세에서 생성하는 약속 |

상태 전이 값이 아니며 약속 생성 이후 변경하지 않습니다.

## 11. 전체 정상 흐름

    1. 방장이 여정(Journey) 항목과 방문 날짜를 골라 약속 생성 요청
       → 같은 트랜잭션 안에서 host 보증금 HELD, host MembershipStatus ACTIVE,
         AppointmentStatus RECRUITING, 해당 Journey 항목(trip_items) CONFIRMED
         까지 한 번에 확정 (2절, JOURNEY_API.md 참고). 활동 시작·종료 시각은
         이 방문 날짜 하루 위에서만 조립된다.

    2. 일반 회원 참여 및 보증금 결제 성공
       MembershipStatus: PENDING → ACTIVE
       DepositStatus: PENDING → HELD
       (같은 트랜잭션, 같은 요청 안에서 함께 처리)

    3. 참여 마감 또는 정원 도달 → AppointmentStatus: RECRUITING → CLOSED
       (정원 도달은 참여 트랜잭션 안에서 즉시, 마감 시각 도달은 스케줄러가 처리)

    4. 활동 시작 시각 도달 → AppointmentStatus: CLOSED → IN_PROGRESS
       (방장의 별도 확정 액션 없이 스케줄러가 시간만 보고 전환, 2절)

    5. 방장이 ACTIVE 회원 전원의 출석 확정
       AttendanceStatus: PENDING → ATTENDED 또는 NO_SHOW
       AppointmentStatus: IN_PROGRESS → COMPLETED
       DepositPayoutBatch를 PENDING으로 생성 (돈은 아직 안 움직임, 16절)

    6. 보증금 정산 (스케줄러가 비동기 처리, 16절)
       참석자: HELD → REFUNDED
       노쇼 회원: HELD → DISTRIBUTED

    7. 후기 작성
       COMPLETED + ACTIVE + ATTENDED 회원끼리 후기 작성 가능

## 12. 참여 취소 흐름

약속 자체를 취소하는 기능은 없습니다(17절). 아래는 **개인 참여** 취소만 다룹니다.

### 결제 전 참여 취소 (방장이 아닌 회원만)

    MembershipStatus: PENDING → LEFT
    DepositStatus: PENDING → CANCELLED

과거(결제 연동 전) 참여는 `PENDING`으로 남아 결제를 기다리는 구조였고, 이 취소
경로는 그때 짜여진 것입니다. 이번 이슈 이후로는 참여 성공 시 보증금 예치와
`ACTIVE` 전환이 같은 트랜잭션에서 함께 끝나므로(14절), 참여에 성공한 회원은
`PENDING`으로 남아있지 않습니다. 즉 이 경로는 참여가 실패해 트랜잭션이 롤백된
경우를 빼면 실질적으로 도달하지 않습니다.

### 결제 후 참여 취소 (방장이 아닌 회원만)

    DepositStatus: HELD → REFUNDED
    MembershipStatus: ACTIVE → LEFT

참여 성공 후 활동 시작 전에 취소하는 경우는 전부 이 경로입니다. 환급 이체
(`DEPOSIT_POOL` → 회원, `DEPOSIT_REFUND`)와 두 상태 변경은 같은 트랜잭션에서
처리합니다. 이체 방향만 반대일 뿐 보증금 예치(14절)와 같은 패턴이라 별도 설계가
필요 없습니다.

취소 가능 여부는 약속 상태가 아니라 **시각** 기준으로 판단합니다.

- **활동 시작 전**(`activityStartAt` 이전): 위의 환급 경로. 참여 마감
  (`joinDeadline`)이 지났어도 환급합니다. 단 마감 후에는 빈자리를 새로 채울 수
  없으므로, 정원 도달로 `CLOSED`였던 약속을 `RECRUITING`으로 되돌리는 것은 마감
  전 취소에서만 일어납니다(2절).
- **활동 시작 후 ~ 종료 전**(`activityStartAt` ~ `activityEndAt`): 취소는 되지만
  **노쇼로 굳습니다**. 같은 트랜잭션에서 `AttendanceStatus`를 `PENDING → NO_SHOW`로
  먼저 확정한 뒤 `ACTIVE → LEFT`로 바꾸고, 보증금은 환급하지 않고 `HELD`로
  남깁니다. 이 보증금은 방장의 출석 확정이 만든 정산 배치가 출석 회원에게
  분배합니다(16절). 노쇼 확정에는 되돌리는 전이가 없고, 마감이 지나 재참여
  (`LEFT → PENDING`)도 불가능합니다.
- **활동 종료 후**(`activityEndAt` 이후): 취소할 수 없습니다(`APPOINTMENT-007`).
  종료 후에는 출석 확정 흐름이 전원을 처리하므로, 나갈 수 있으면 노쇼 확정을
  피해 몰수를 빠져나가는 구멍이 됩니다.

    활동 중 참여 취소:
    AttendanceStatus: PENDING → NO_SHOW
    MembershipStatus: ACTIVE → LEFT
    DepositStatus: HELD (유지) → 정산 배치에서 DISTRIBUTED

주의: 출석 확정 합산과 정산 분배는 `ACTIVE` 회원만 봐서는 안 됩니다. 활동 중
탈퇴로 굳은 노쇼는 `LEFT + NO_SHOW + HELD` 조합으로 남으므로
(`findLeftNoShowMembersByAppointmentId`), 이 목록을 빠뜨리면 그 보증금이
`DEPOSIT_POOL`에 영영 남습니다.

### 방장

방장은 어떤 상태에서도 개인 참여를 취소할 수 없습니다(3절). 참여 취소 API를
호출하면 항상 `APPOINTMENT-007`을 반환합니다.

## 13. 현재 PR에서 실제로 동작하는 범위

| 기능 | 현재 상태 |
| --- | --- |
| 약속 목록·상세·활성 회원·내 참여 상태 조회 | 사용 가능 |
| 약속 생성·참여(보증금 예치 포함) | 사용 가능 |
| 방장이 아닌 회원의 참여 취소(보증금 환급 포함) | 사용 가능 |
| 약속 lifecycle 자동 전이(`RECRUITING → CLOSED → IN_PROGRESS`) | 사용 가능(15절) |
| 회원 약속 프로필 조회 | 사용 가능 |
| 조건을 만족하는 기존 완료 약속의 후기 등록 | 사용 가능 |
| 출석 확정(`IN_PROGRESS → COMPLETED`, 정산 배치 `PENDING` 생성까지) | 사용 가능 |
| 정산 배치 비동기 처리(환급·노쇼 분배 지갑 이체) | 사용 가능(16절) |

정산 배치가 `PENDING`인 동안은 아직 아무 지갑도 움직이지 않습니다. 실제
환급·노쇼 분배 이체는 16절의 비동기 처리가 60초 이내에 이어서 실행합니다.

## 14. `DEPOSIT_POOL` 지갑 설정

보증금이 실제로 예치될 장소인 시스템 지갑을 만드는 선행 작업입니다. `wallet_owners`
테이블은 V1부터 `owner_type = 'SYSTEM'`과 `system_code`로 시스템 소유 지갑을 표현할
수 있게 설계돼 있었지만, 실제로 이 행을 만드는 마이그레이션은 없었습니다. 다른
설계 결정과 달리 이건 선택지가 아니라, 보증금 예치 자체가 성립하기 위한 전제
조건입니다.

- `system_code` 값은 `"DEPOSIT_POOL"`로 정합니다. 과거 종료된 이슈(#46·#47·#50·#62,
  보증금 도메인·정산 흐름 구현)에서 이미 이 이름으로 정하고 "외부 에스크로 기능이나
  범용 에스크로 도메인은 만들지 않는다"고 명시했던 규칙을 그대로 따릅니다. 이미
  merge된 코드 주석(`Deposit.java`, `DepositMapper.java`)에는 이전 표현인
  `SYSTEM_ESCROW`가 남아있지만, 이건 주석이라 코드 동작에 영향이 없고 고치지
  않습니다.
- `system_code` 값은 순수한 내부 키가 아니라 `TransactionServiceImpl
  .resolveCounterparty()`를 통해 회원의 거래 상세 화면에 상대방 이름으로 그대로
  노출됩니다(코드 대신 `"Stripe"`를 직접 보여주는 기존 TOPUP 처리와 같은 방식).
  `DEPOSIT_POOL`도 번역 없이 그대로 노출되므로, 회원 화면에는 개발자용 코드처럼
  보이는 문자열이 뜹니다. 더 읽기 편한 표시 문구가 필요해지면 `resolveCounterparty`
  에 `DEPOSIT_POOL` 전용 분기를 추가하는 건 후속 범위입니다.
- 같은 값을 마이그레이션(시드 INSERT)과 애플리케이션 코드(지갑 조회) 양쪽에서
  써야 하므로, 문자열을 여러 곳에 직접 쓰지 않고 Java 상수 하나로 정의해
  재사용합니다. 두 곳의 값이 어긋나면 지갑을 못 찾아 모든 보증금 예치가 조용히
  실패합니다.
- `WalletMapper`에 `system_code`로 지갑을 조회하는 메서드를 추가합니다.

## 15. 약속 lifecycle 전이 — 계산해서 표시 + 폴링 스케줄러로 뒷정리

시간 기반 전이(`RECRUITING → CLOSED`, `CLOSED → IN_PROGRESS`)는 사용자 액션이
아니라 시간이 조건입니다. `IN_PROGRESS → COMPLETED`는 방장의 출석 확정 액션이
트리거이므로 아래 내용의 대상이 아닙니다. 정원이 차서 `CLOSED`가 되는 경우는
시간과 무관한 이벤트라 스케줄러가 아니라 `joinAppointment`가 참여 성공 시점에
동기로 처리합니다(2절).

**화면에 보여주는 값과 DB에 저장된 값을 분리합니다.** 약속 목록·상세 조회
(`AppointmentService.toSummaryResponse`/`toDetailResponse`가 호출하는
`resolveDisplayStatus`)는 `appointment_status` 컬럼을 그대로 반환하지 않고,
`join_deadline`·`activityStartAt`·`activityEndAt`과 현재 시각을 비교해 "지금
시점의 실제 상태"를 계산해서 반환합니다. 예를 들어 컬럼 값이 아직
`RECRUITING`이어도 `join_deadline`이 이미 지났으면 응답은 `CLOSED`로 나갑니다.
이러면 사용자는 스케줄러 주기와 무관하게 항상 정확한 상태를 봅니다.

이 계산에는 DB에 존재하지 않는 **표시 전용 상태 `AWAITING_ATTENDANCE`**가 하나
있습니다. `activityEndAt`이 지났지만 방장이 아직 출석을 확정하지 않은 약속은 DB
컬럼상 `IN_PROGRESS`지만, 응답에는 `AWAITING_ATTENDANCE`로 나갑니다.
`IN_PROGRESS → COMPLETED`가 출석 확정 액션으로만 일어나는 이상(2절) "활동이
끝났는데 진행 중"으로 보이는 구간이 무한정 남는데, 그 구간을 화면이 클라이언트
시계로 다시 재지 않고 서버 판정 하나로 알 수 있게 한 것입니다. 이 값은
`resolveDisplayStatus`만 만들어내며 DB에 쓰이지 않고, `canTransitionTo()`에서도
어떤 전이에도 등장하지 않습니다. 프론트엔드 출석 확정 화면 게이트가 이 값을
사용합니다.

이 라이브 계산은 **화면 표시에만** 적용합니다. `GET /appointments/me`(트립 연결·
QR 공동결제 게이팅에 쓰이는 엔드포인트, `findMyOngoingAppointments`)는 라이브
계산을 거치지 않고 DB에 저장된 `appointment_status` 값만 그대로 사용합니다. 이
값에 걸리는 다른 로직(트립 비용 연결 등)과의 일관성이 즉시 반영보다 더
중요하다고 판단했습니다. 따라서 활동이 실제로 시작된 뒤에도 스케줄러가 아직
`IN_PROGRESS`로 못 바꿨다면 최대 60초까지는 이 엔드포인트에 나타나지 않을 수
있습니다.

DB 컬럼 값 자체는 스케줄러가 뒤에서 맞춥니다. 아무도 이 쓰기 작업의 결과를
실시간으로 보고 있지 않으므로 주기가 길어도 됩니다.

- `RootConfig`에 `@EnableScheduling`이 이미 있어 별도 설정이 필요 없습니다.
- `me.nawa.appointment.service.AppointmentLifecycleScheduler`가
  `@Scheduled(fixedDelay = 60_000)`로 60초마다 `advanceLifecycle()`을
  실행합니다. `fixedDelay`를 쓰는 이유는 이전 tick이 끝나야 다음 tick이
  시작돼서 겹쳐 돌지 않기 때문입니다. 화면 표시가 이미 실시간이므로 이 주기를
  더 줄일 이유는 없어 값은 코드에 고정해뒀습니다(설정 파일로 뺄 만큼의 실익이
  없음).
- 전이는 약속마다 개별 트랜잭션으로 처리하지 않고, `AppointmentMapper`의 벌크
  `UPDATE` 두 개(`closeExpiredRecruitingAppointments`,
  `startDueClosedAppointments`)를 한 트랜잭션(`advanceLifecycle()`) 안에서
  차례로 실행합니다. 각 `UPDATE`는 조건에 맞는 행을 한 번에 전환하는 단일
  문장이라 그 자체로 원자적이고, 약속 수가 늘어나도 라운드트립이 늘지 않습니다.
  `advanceLifecycle()`은 `@Scheduled` 메서드 자체가 직접 호출 가능한 public
  메서드라 스케줄을 기다리지 않고 단위 테스트에서 바로 호출해 검증합니다.

## 16. 보증금 정산 — 비동기 배치

방장의 출석 확정 요청(`confirmAttendance`)은 무거운 지갑 이체를 직접 하지 않고
`DepositPayoutBatch`를 `PENDING`으로 남기기만 합니다. 실제 이체는
`me.nawa.deposit.service.DepositPayoutProcessingScheduler`가 60초 주기로 별도
tick에서 처리합니다. 이 앱의 다른 결제성 기능(정산·QR결제)은 요청 하나 안에서
동기로 처리하지만, 보증금 정산은 참가자 수만큼 이체가 한 번에 여러 건 필요해
무거워질 수 있어 비동기로 갑니다.

- `DepositPayoutProcessingScheduler`가 `DepositPayoutBatchMapper.
  findPendingOrFailedBatchIds()`로 대상 배치를 훑고, 배치마다
  `DepositPayoutBatchProcessor.processBatch(batchId)`를 호출합니다. 각 배치는
  독립된 트랜잭션(`@Transactional`)으로 처리해 하나가 실패해도 나머지에
  영향이 없습니다.
- `processBatch`가 하는 일: 출석한 회원에게는 본인 보증금을 환급
  (`SELF_REFUND`, `DEPOSIT_REFUND` 이체), 노쇼 회원의 보증금은 출석 회원에게
  균등 분배(`NO_SHOW_SHARE`, `DEPOSIT_NO_SHOW_DISTRIBUTION` 이체)합니다. 분배
  대상 노쇼에는 `ACTIVE` 노쇼뿐 아니라 활동 중 탈퇴로 굳은 `LEFT` 노쇼
  (`findLeftNoShowMembersByAppointmentId`, 12절)도 포함합니다 — `ACTIVE`만 보면
  그 보증금이 `HELD`인 채 `DEPOSIT_POOL`에 영영 남습니다. `confirmAttendance`의
  배치 합산(`totalHeldAmount`)도 같은 목록을 함께 더합니다. 분배는
  [SETTLEMENT.md](SETTLEMENT.md)의 `EQUAL` 분담과 같은
  `SettlementAmountAllocator`를 그대로 재사용합니다(참가 ID 오름차순으로 최소
  단위 금액을 하나씩 배분).
- 이체마다 `DepositPayoutMapper.countByAllocation`으로 이미 처리됐는지 먼저
  확인합니다. 중간에 실패해 다음 tick이 같은 배치를 재처리하더라도, 이미 끝난
  지급 건은 다시 이체하지 않고 건너뜁니다 — `deposit_payouts`의
  `(source_deposit_id, recipient_appointment_member_id, allocation_type)`
  UNIQUE 제약과도 맞습니다.
- `ATTENDED` 참가자가 0명이면 노쇼 보증금을 나눠줄 대상이 없어 분배 계산이
  성립하지 않습니다. 이 배치 처리 단계가 아니라 `confirmAttendance` 자체에서
  미리 거부합니다 — 출석자가 한 명도 없는 요청은 배치를 만들기 전에
  `APPOINTMENT-006`으로 막히므로, 이 시나리오의 배치는 애초에 생기지 않습니다.
- 도중에 실패하면 배치가 `FAILED`로 남고, 이미 처리된 지급 건은 그대로 유지됩니다.
  실패 표시(`markBatchFailed`)는 실패를 일으킨 트랜잭션과 별도로
  (`Propagation.REQUIRES_NEW`) 커밋합니다 — 같은 트랜잭션이면 롤백될 때 `FAILED`
  반영도 함께 사라지기 때문입니다. 다음 tick이 `FAILED` 배치를 다시 집어
  `PROCESSING`으로 돌리고 이어서 처리합니다. 방장의 출석 확정 요청 자체는 이미
  성공한 뒤라 사용자가 재시도를 신경 쓸 필요가 없습니다.
- 시스템(스케줄러)이 자동으로 시작한 이체는 `wallet_transfers.
  initiator_member_id`를 `null`로 남깁니다(스키마 주석 "시스템 자동 이체는
  null" 참고). 이를 위해 `WalletTransferService.transferFromSystemWallet`의
  `initiatorMemberId` 파라미터를 `long`에서 `Long`으로 바꿔 null을 받을 수
  있게 했습니다.
- `deposit_payout_batches.appointment_id`에 UNIQUE 제약이 있어 같은 약속에 배치가
  두 번 생기지 않습니다.

## 17. 이번 범위에서 제외한 것

| 항목 | 결정 | 이유 |
| --- | --- | --- |
| 약속 전체 취소 | 만들지 않음 | 활동 시작 시각이 되면 그 시점의 `ACTIVE` 회원끼리(최소 방장 1인) 항상 진행합니다. 방장이 참여를 취소할 수 없으므로(3절) 약속이 무산되는 경로 자체가 없습니다. `AppointmentStatus.CANCELLED`, `ResolutionReason.APPOINTMENT_CANCELLED`, `AllocationType.CANCELLATION_REFUND`는 코드에 남겨두되(2·6절), 이 값으로 가는 트리거는 만들지 않습니다. |
| 에스크로 지갑 잔액 조회용 관리자 화면 | 만들지 않음 | 이 코드베이스엔 admin 권한 개념 자체가 없습니다(`SecurityConfig`에 역할 기반 분기 없음). 회원 본인의 보증금 예치·환급은 기존 지갑 화면에 일반 거래로 자동 노출되므로 별도 작업이 필요 없습니다. `DEPOSIT_POOL`도 `wallets` 테이블의 평범한 행이라 DB 조회로 확인 가능합니다. |
| 참여자 본인 보증금 상태 조회 API(`GET .../members/me/deposit`) | 만들지 않음 | 지금 이 정보를 쓰는 화면이 프론트엔드에 없습니다. 필요해지면 별도 이슈로 진행합니다. |
