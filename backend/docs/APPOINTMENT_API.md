# 약속 및 후기 API 계약

이 문서는 탐색 Event·Place에서 생성하는 약속의 조회와 회원 후기 API 계약을 정의합니다.
모든 경로는 인증이 필요하며 공통 `ApiResponse` 형식을 사용합니다. ENUM·상태 전이·
구현 여부는 [APPOINTMENT_DEPOSIT_STATE_MACHINE.md](APPOINTMENT_DEPOSIT_STATE_MACHINE.md)
를 따릅니다.

## 공통 정책

- 날짜와 시각은 `yyyy-MM-dd'T'HH:mm:ss` 형식의 문자열을 사용합니다. 단, 약속
  생성 요청의 `visitDate`는 `yyyy-MM-dd`, `activityStartTime`/
  `activityEndTime`은 `HH:mm:ss`(시각만)입니다.
- 약속 대상 `itemType`은 `EVENT`, `PLACE` 중 하나입니다.
- 약속은 항상 여정(Journey) 항목 하나에 연결되어 생성됩니다. 여정 없이 만드는
  경로는 없습니다.
- 지원 언어는 `en`, `ja`, `zh-TW`, `vi`입니다.
- `maxMembers`는 방장을 포함하여 2명 이상 10명 이하입니다.
- 보증금은 5,000 이상 50,000 이하의 정수 포인트(P) 금액입니다. 전송 값·검증
  범위·DB 제약은 기존과 같습니다.
- 약속 생성·참여는 회원 지갑 차감, 시스템 에스크로(`DEPOSIT_POOL`) 입금, 양쪽
  원장 기록을 하나의 트랜잭션으로 처리한 뒤 보증금을 `HELD`, 회원을 `ACTIVE`로
  전환합니다.
- 출석 확정이 성공하면 `DepositPayoutBatch`를 `PENDING`으로 생성하고, 60초 주기
  비동기 배치 처리가 실제 환급·노쇼 분배 지갑 이체를 실행합니다.
- 참여를 취소(`LEFT`)한 회원도 활동 시작 시각 전이면 같은 약속에 다시 참여할 수
  있습니다. 기존 참여·보증금 행을 재사용합니다(자세한 내용은
  [APPOINTMENT_DEPOSIT_STATE_MACHINE.md](./APPOINTMENT_DEPOSIT_STATE_MACHINE.md)
  3·5절 참고).

## 약속 상태 전이

상태 전이는 별도 API 없이 서버가 조건에 따라 자동으로 처리합니다. 방장의 확정
버튼 같은 수동 액션은 없습니다.

- `RECRUITING` → `FULL`: 정원이 다 차면 참여 성공 시점에 즉시 전환합니다.
  `FULL`은 정원 충족만을 뜻하며, 시간으로 도달하는 경로는 없습니다.
- `FULL` → `RECRUITING`: 활동 시작 시각 전에 참여 취소가 발생해 빈자리가 생기면
  즉시 되돌아갑니다.
- `RECRUITING`·`FULL` → `IN_PROGRESS`: 활동 시작 시각이 되면 60초 주기 스케줄러가
  전환합니다. 방장의 별도 확정 절차는 없습니다. 정원이 차지 않은 약속은 `FULL`을
  거치지 않으므로 `RECRUITING`에서 곧바로 넘어갑니다.
- `IN_PROGRESS` → `COMPLETED`: 방장이 모든 `ACTIVE` 회원의 출석을 확정한 경우

목록·상세 조회 응답의 `appointmentStatus`는 스케줄러가 아직 못 따라잡았어도
정원과 시작·종료 시각 기준으로 즉시 계산한 값을 보여줍니다. 이 계산에는 DB에는
저장되지 않는 **표시 전용 값 `AWAITING_ATTENDANCE`**가 하나 더 있습니다 —
`activityEndAt`이 지났지만 방장이 아직 출석을 확정하지 않은 약속은 DB에
`IN_PROGRESS`로 남아 있어도 응답에는 `AWAITING_ATTENDANCE`로 옵니다(활동
종료를 시간으로 완료 처리하는 전이는 없습니다 — `COMPLETED`는 출석 확정만이
만듭니다). 단, `GET /appointments/me`는
DB에 실제로 반영된 값만 사용하므로 활동 시작 후 최대 60초까지 지연될 수
있습니다. 자세한 내용은
[APPOINTMENT_DEPOSIT_STATE_MACHINE.md](./APPOINTMENT_DEPOSIT_STATE_MACHINE.md)
15절을 참고하세요.

## 약속 목록 조회

`GET /api/v1/appointments`

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | ---: | --- |
| `itemId` | number | N | 탐색 항목 ID |
| `itemType` | string | N | `EVENT`, `PLACE` |
| `language` | string | N | 약속 언어 |
| `keyword` | string | N | 약속명·설명·만남 장소 검색어 |
| `status` | string | N | 약속 상태 |
| `page` | number | N | 0부터 시작, 기본값 0 |
| `size` | number | N | 기본값 20, 최대 100 |

`PAYMENT_PENDING` 약속은 공개 목록에서 제외합니다. 응답은 `content`, `page`,
`size`, `totalElements`, `totalPages`, `hasNext`를 포함합니다.

## 약속 상세 및 회원 조회

- `GET /api/v1/appointments/{appointmentId}`
- `GET /api/v1/appointments/{appointmentId}/members`
- `GET /api/v1/appointments/{appointmentId}/members/me`

상세 응답에는 약속 정보와 현재 `ACTIVE`인 회원 목록이 포함됩니다. 회원 목록 API도
같은 활성 회원 계약을 사용합니다. `PAYMENT_PENDING`, `CANCELLED` 약속은 방장만 상세
조회할 수 있으며 다른 회원에게는 `APPOINTMENT-001`을 반환합니다.
`members/me`는 현재 로그인 회원의 참여 여부, 참여·출석 상태와 방장 여부를 반환합니다.

## 내가 참여 중인 약속 조회

`GET /api/v1/appointments/me?scope=ONGOING`

로그인 회원이 `ACTIVE`로 참여 중이고 여행(trip)이 연결된 약속을 배열로
반환합니다. 참여만 하고 여행에 연결되지 않은 약속은 QR 공동 소비(SHARED) 결제가
트립 비용 연결(`trip_expense_links`)을 만들 수 없어 제외합니다.

`scope`가 범위와 정렬을 정합니다. 그 밖의 값은 `COMMON-001`을 반환합니다.

| scope | 범위 | 정렬 |
| --- | --- | --- |
| `ONGOING`(기본) | `IN_PROGRESS` 약속만 | `activityStartAt` 오름차순 — QR 공동 소비 결제가 쓰는 기존 계약 그대로 |
| `ALL` | `CANCELLED`를 제외한 전체 | 예정 약속을 임박한 순으로 먼저, 지난 약속을 최근 순으로 뒤에 — 프로필의 약속 목록이 사용 |

`ALL`은 `PAYMENT_PENDING`을 포함합니다. `PAYMENT_PENDING`에 한해 상세 조회의
호스트 노출 기준을 따릅니다 — 보증금 결제 전에는 모집이 열리지 않아 호스트 외에는
`ACTIVE` 멤버십이 생길 수 없고, 본인 목록에서는 결제를 마치도록 보이는 편이 맞습니다.
`CANCELLED`는 상세 조회가 호스트 본인에게는 보여주는 것과 달리, 프로필 목록에 노출할
근거가 없어 호스트에게도 제외합니다. 공개 약속 목록(`GET /api/v1/appointments`)이
`PAYMENT_PENDING`을 제외하는 것과 근거가 다르니 혼동하지 마세요.

```json
[
  {
    "appointmentId": 42,
    "appointmentName": "Seongsu K-Beauty Tour",
    "tripId": 9,
    "meetingPlace": "Olive Young N Seongsu",
    "activityStartAt": "2026-08-21T18:30:00",
    "activityEndAt": "2026-08-21T22:00:00",
    "itemId": 100,
    "itemType": "EVENT",
    "appointmentStatus": "IN_PROGRESS"
  }
]
```

`itemType`은 `EVENT` 또는 `PLACE`이며 화면의 탭 구분에 사용합니다.

이 엔드포인트는 스케줄러가 실제로 반영한 DB의 `appointment_status` 값만
사용합니다(목록·상세 조회와 달리 즉시 계산한 값을 쓰지 않습니다 — 근거는
[APPOINTMENT_DEPOSIT_STATE_MACHINE.md](./APPOINTMENT_DEPOSIT_STATE_MACHINE.md)
15절). 그래서 활동 시작 시각이 지나도 스케줄러가 아직 상태를 못 바꿨다면,
`scope=ONGOING`에서는 해당 약속이 최대 60초까지 배열에서 빠질 수 있고
`scope=ALL`에서는 `appointmentStatus`가 그만큼 늦게 반영될 수 있습니다.

## 약속 생성

`POST /api/v1/appointments`

```json
{
  "itemId": 100,
  "itemType": "EVENT",
  "tripId": 20,
  "visitDate": "2026-08-21",
  "languageCode": "en",
  "appointmentName": "Seongsu K-Beauty Tour",
  "maxMembers": 5,
  "depositAmount": 10000,
  "meetingPlace": "Olive Young N Seongsu",
  "activityStartTime": "18:30:00",
  "activityEndTime": "22:00:00"
}
```

`tripId`는 이 약속을 확정할 Journey입니다. `visitDate`는 그 Journey 안에서
활동이 이루어지는 방문 날짜이며, `activityStartTime`/`activityEndTime`은
`visitDate` 하루 위에서의 시각만 받습니다 — 서버가 둘을 합쳐 실제
`activity_start_at`/`activity_end_at`을 만들므로, 활동 시작·종료는 항상 같은
날짜 안에서만 성립합니다.

`tripId`는 요청 회원이 소유한 Journey여야 하고, `visitDate`는 그 Journey의
`startDate`~`endDate` 안이어야 하며, 같은 `(tripId, itemId, visitDate)`
조합의 활성 일정이 이미 있으면 안 됩니다 — 위반 시 각각 `JOURNEY-002`, `JOURNEY-007`,
`JOURNEY-004`를 반환합니다([JOURNEY_API.md](./JOURNEY_API.md) 참고). 이 경로는
Journey 일정 추가와 달리 **항목 자체의 운영 기간(`JOURNEY-012`)은 보지 않습니다** —
`validateJourneyLink`가 `addJourneyItem`과 별개의 검사를 갖고 있기 때문입니다. 활동
시작 시각은 종료 시각보다 빨라야 하며 현재 시각 이후여야 합니다. 참여 마감 시각은
따로 받지 않습니다 — 참여는 활동이 시작되기 전까지 열려 있습니다.

성공하면 방장의 보증금을 즉시 예치(`DEPOSIT_HOLD`)하고 약속을 `RECRUITING`
상태로 생성하는 것과 같은 트랜잭션에서, 해당 Journey 항목(`trip_items`)을
`ADDED`를 거치지 않고 곧바로 `CONFIRMED`로 만듭니다. `appointment_id`와
`confirmed_at`이 이때 채워집니다.

## 참여 요청과 취소

- `POST /api/v1/appointments/{appointmentId}/members`
- `DELETE /api/v1/appointments/{appointmentId}/members/me`

참여 요청은 `RECRUITING` 상태, 활동 시작 전, 정원 미달 조건을 모두 만족해야
하며, 성공하면 참여자의 보증금을 즉시 예치(`DEPOSIT_HOLD`)하고 `ACTIVE`로
전환합니다.

참여 성공 시 보증금 예치와 `ACTIVE` 전환이 같은 트랜잭션에서 함께 끝나므로,
방장이 아닌 회원의 참여 취소는 실질적으로 `ACTIVE` 상태에서 일어납니다.
취소 결과는 시각에 따라 갈립니다.

- **활동 시작 전**: `HELD` 보증금을 환급(`DEPOSIT_REFUND`)하고 참가 상태를
  `LEFT`로 변경하는 걸 같은 트랜잭션에서 처리합니다. 정원이 차서 `FULL`이던
  약속이라면 빈자리가 생기므로 같은 트랜잭션에서 `RECRUITING`으로 되돌립니다.
- **활동 시작 후 ~ 종료 전**: 취소는 되지만 **노쇼로 굳습니다**. 출석 상태를
  `NO_SHOW`로 확정하고 `LEFT`로 바꾸며, 보증금은 환급하지 않고 출석 확정 후
  정산 배치가 출석 회원에게 분배합니다.
- **활동 종료 후(`activityEndAt` 이후)**: 취소할 수 없으며 `APPOINTMENT-007`을
  반환합니다.

결제 전(`PENDING`) 취소 경로도 남아있지만, 참여 성공 시 `PENDING`으로 남는
경우가 없어 참여가 실패해 트랜잭션이 롤백된 경우를 빼면 실질적으로 도달하지
않습니다.

방장은 참여 취소 API로 약속에서 빠질 수 없습니다. 상태와 무관하게 항상
`APPOINTMENT-007`을 반환합니다. 자세한 근거는
[APPOINTMENT_DEPOSIT_STATE_MACHINE.md](./APPOINTMENT_DEPOSIT_STATE_MACHINE.md)
12절을 참고하세요.

## 출석 확정

`PATCH /api/v1/appointments/{appointmentId}/attendance`

```json
{
  "members": [
    { "memberId": 1, "attendanceStatus": "ATTENDED" },
    { "memberId": 2, "attendanceStatus": "NO_SHOW" }
  ]
}
```

- 방장만 `IN_PROGRESS` 상태에서 모든 `ACTIVE` 회원을 정확히 한 번씩 `ATTENDED`
  또는 `NO_SHOW`로 확정할 수 있습니다. 방장이 아니면 `APPOINTMENT-004`, 약속이
  `IN_PROGRESS`가 아니거나 출석자가 한 명도 없거나 요청이 활성 회원 전원을
  정확히 한 번씩 포함하지 않으면 `APPOINTMENT-006`을 반환합니다.
- **`IN_PROGRESS`인 것만으로는 부족합니다.** 이 상태는 활동 **시작** 시각에
  스케줄러가 바꾸므로, 상태만 보면 활동이 진행되는 도중에도 확정이 통과합니다.
  아직 오는 중인 참여자가 노쇼로 굳어 보증금을 잃고, 확정에는 되돌리는 상태
  전이가 없습니다. 그래서 `activityEndAt`이 지났는지 함께 보고, 지나지 않았거나
  값을 읽지 못하면 `APPOINTMENT-009`를 반환합니다. 화면은 이 조건을 직접 재지
  않고, 조회 응답이 같은 판정을 담아 내려주는 표시 전용 상태
  `AWAITING_ATTENDANCE`(위 "약속 상태 전이" 참고)로 진입을 엽니다 — 다만 화면
  게이트가 무엇이든 화면을 거치지 않는 요청은 이 서버 검사가 막습니다.
- 성공하면 같은 트랜잭션에서 각 회원의 출석 상태를 반영하고 약속을 `COMPLETED`로
  전환한 뒤, 보증금 정산 배치(`DepositPayoutBatch`)를 `PENDING`으로 생성합니다.
  이 시점에는 아직 지갑 이체가 일어나지 않습니다 — 실제 환급(출석 회원 본인
  보증금)·노쇼 분배(노쇼 회원 보증금을 출석 회원에게 균등 분배)는 60초 주기
  비동기 배치 처리가 이어서 실행합니다. 자세한 내용은
  [APPOINTMENT_DEPOSIT_STATE_MACHINE.md](./APPOINTMENT_DEPOSIT_STATE_MACHINE.md)
  16절을 참고하세요.

## 회원 후기 등록

`POST /api/v1/appointments/{appointmentId}/reviews`

```json
{
  "reviewedAppointmentMemberId": 30,
  "scores": {
    "PUNCTUALITY": 5,
    "MANNERS": 4,
    "COMMUNICATION": 5
  },
  "keywordCodes": ["FRIENDLY", "ON_TIME"]
}
```

- 기존 `COMPLETED` 약속에서만 작성할 수 있습니다.
- 작성자와 평가 대상 모두 `ACTIVE`, `ATTENDED` 회원이어야 합니다.
- 자기 자신과 이미 평가한 회원을 다시 평가할 수 없습니다.
- `PUNCTUALITY`, `MANNERS`, `COMMUNICATION` 점수를 모두 입력하며 각 점수는
  1~5의 정수입니다.
- 키워드는 0~5개이며 중복 없이 `FRIENDLY`, `ON_TIME`, `CONSIDERATE`,
  `GOOD_COMMUNICATOR`, `WOULD_JOIN_AGAIN` 중에서 선택합니다.
- 성공하면 `201 Created`를 반환합니다.

## 내가 작성한 후기 대상 조회

`GET /api/v1/appointments/{appointmentId}/reviews/me`

```json
{
  "reviewedAppointmentMemberIds": [30, 31]
}
```

- 로그인 회원이 이 약속에서 이미 후기를 쓴 대상의 `appointmentMemberId` 목록만
  반환합니다. 점수·키워드는 포함하지 않습니다.
- 후기 작성 화면이 "이미 씀" 상태를 복원하는 데 씁니다. 이 목록 없이는 재진입 시
  전원이 미작성으로 보여 재제출 → `REVIEW-002`가 납니다.
- 진입 조건은 후기 등록과 같습니다 — 약속이 `COMPLETED`이고, 방장이 출석을
  확인한(`ACTIVE` + `ATTENDED`) 참여자만 조회할 수 있습니다. 그 밖에는
  `REVIEW-001`을 반환합니다. 후기를 쓸 자격이 없는 회원은 작성 화면에 진입하지
  못하므로 조회도 같은 잣대로 막습니다.
- 없는 약속이면 `APPOINTMENT-001`을 반환합니다.
- 소프트 삭제된 후기는 목록에서 제외합니다.
