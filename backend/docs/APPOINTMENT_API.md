# 약속 및 후기 API 계약

이 문서는 탐색 Event·Place에서 생성하는 약속의 조회와 회원 후기 API 계약을 정의합니다.
모든 경로는 인증이 필요하며 공통 `ApiResponse` 형식을 사용합니다. ENUM·상태 전이·
구현 여부는 [APPOINTMENT_DEPOSIT_STATE_MACHINE.md](APPOINTMENT_DEPOSIT_STATE_MACHINE.md)
를 따릅니다.

## 공통 정책

- 날짜와 시각은 `yyyy-MM-dd'T'HH:mm:ss` 형식의 문자열을 사용합니다.
- 약속 대상 `itemType`은 `EVENT`, `PLACE` 중 하나입니다.
- 지원 언어는 `en`, `ja`, `zh-TW`, `vi`입니다.
- `maxMembers`는 방장을 포함하여 2명 이상 10명 이하입니다.
- 보증금은 5,000원 이상 50,000원 이하의 정수 금액입니다.
- 보증금 결제 연동 전에는 생성·참여·출석 확정을 완료 처리하지 않습니다.
  이 요청은 `APPOINTMENT-008`(409)을 반환하며, 보증금·회원·지갑 거래 상태를
  변경하지 않습니다.
- 결제 연동은 회원 지갑 차감, 시스템 에스크로 입금, 양쪽 원장 기록을 하나의
  트랜잭션으로 처리한 뒤 보증금을 `HELD`, 회원을 `ACTIVE`로 전환해야 합니다.
- `LEFT`가 된 회원은 같은 약속에 다시 참여할 수 없습니다.

## 약속 상태 전이

결제 연동 전에는 약속 상태 전이 API와 자동 전이를 실행하지 않습니다. 결제 연동 후
상태 전이는 방장의 시작 버튼이 아닌 서버 조건으로 처리합니다.

- `RECRUITING` → `CLOSED`: 참여 마감 시각이 지났거나 결제 완료 회원이 정원에 도달한 경우
- `CLOSED` → `CONFIRMED`: 남은 모든 `ACTIVE` 회원의 보증금이 실제로 `HELD`인 경우
- `CONFIRMED` → `IN_PROGRESS`: 활동 시작 시각이 도달한 경우
- `IN_PROGRESS` → `COMPLETED`: 방장이 모든 `ACTIVE` 회원의 출석을 확정한 경우

상태 전이 API는 결제·에스크로 연동 PR에서 함께 제공합니다.

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

## 내가 참여 중인 진행 중 약속 조회

`GET /api/v1/appointments/me`

로그인 회원이 `ACTIVE`로 참여 중이고, 여행(trip)이 연결돼 있으며, `IN_PROGRESS`
상태인 약속만 배열로 반환합니다. 참여만 하고 여행에 연결되지 않은 약속은 QR 공동
소비(SHARED) 결제가 트립 비용 연결(`trip_expense_links`)을 만들 수 없어 제외합니다.

```json
[
  {
    "appointmentId": 42,
    "appointmentName": "Seongsu K-Beauty Tour",
    "tripId": 9,
    "meetingPlace": "Olive Young N Seongsu",
    "activityStartAt": "2026-08-21T18:30:00",
    "activityEndAt": "2026-08-21T22:00:00"
  }
]
```

참여 자체가 보증금 결제 연동 전까지 `APPOINTMENT-008`로 막혀 있어, 그 전까지는
항상 빈 배열을 반환합니다.

## 약속 생성

`POST /api/v1/appointments`

```json
{
  "itemId": 100,
  "itemType": "EVENT",
  "languageCode": "en",
  "appointmentName": "Seongsu K-Beauty Tour",
  "maxMembers": 5,
  "joinDeadline": "2026-08-20T18:00:00",
  "depositAmount": 10000,
  "meetingPlace": "Olive Young N Seongsu",
  "meetingAddress": "Seongdong-gu, Seoul",
  "activityStartAt": "2026-08-21T18:30:00",
  "activityEndAt": "2026-08-21T22:00:00"
}
```

참여 마감은 활동 시작 시각보다 늦을 수 없으며, 활동 시작 시각은 종료 시각보다
빨라야 합니다. 현재는 실제 보증금 예치 경로가 없어 요청이 `APPOINTMENT-008`(409)을
반환하며 데이터를 생성하지 않습니다.

## 참여 요청과 취소

- `POST /api/v1/appointments/{appointmentId}/members`
- `DELETE /api/v1/appointments/{appointmentId}/members/me`

참여 요청은 실제 보증금 예치 경로가 준비되기 전까지 `APPOINTMENT-008`(409)을 반환하며
회원·보증금 데이터를 생성하지 않습니다. 결제 연동 후에는 `RECRUITING` 상태, 참여
마감 전, 정원 미달 조건에서만 참여할 수 있습니다.

결제 연동 후에는 참여 성공 시 보증금 예치와 `ACTIVE` 전환이 같은 트랜잭션에서 함께
끝나므로, 방장이 아닌 회원의 참여 취소는 실질적으로 `ACTIVE` 상태에서 일어납니다.
이 경우 `HELD` 보증금을 환급(`DEPOSIT_REFUND`)하고 참가 상태를 `LEFT`로 변경하는
걸 같은 트랜잭션에서 처리합니다. 결제 전(`PENDING`) 취소 경로도 남아있지만, 참여
성공 시 `PENDING`으로 남는 경우가 없어 참여가 실패해 트랜잭션이 롤백된 경우를 빼면
실질적으로 도달하지 않습니다. 현재는 결제 연동 전이라 참여 자체가
`APPOINTMENT-008`로 막혀 있어 이 취소 경로도 아직 실행되지 않습니다.

방장은 참여 취소 API로 약속에서 빠질 수 없습니다. 상태와 무관하게 항상
`APPOINTMENT-007`을 반환합니다. `IN_PROGRESS`, `COMPLETED`, `CANCELLED` 약속에서도
참여를 취소할 수 없습니다.

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

- 결제·상태 전이 연동 전에는 `APPOINTMENT-008`(409)을 반환하며 출석과 약속 상태를
  변경하지 않습니다.
- 결제 연동 후에는 방장만 `IN_PROGRESS` 상태에서 모든 `ACTIVE` 회원을 정확히 한 번씩
  `ATTENDED` 또는 `NO_SHOW`로 확정할 수 있습니다.

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

- 기존 `COMPLETED` 약속에서만 작성할 수 있습니다. 결제·상태 전이 연동 전에는 새 약속을
  `COMPLETED` 상태까지 진행할 수 없습니다.
- 작성자와 평가 대상 모두 `ACTIVE`, `ATTENDED` 회원이어야 합니다.
- 자기 자신과 이미 평가한 회원을 다시 평가할 수 없습니다.
- `PUNCTUALITY`, `MANNERS`, `COMMUNICATION` 점수를 모두 입력하며 각 점수는
  1~5의 정수입니다.
- 키워드는 0~5개이며 중복 없이 `FRIENDLY`, `ON_TIME`, `CONSIDERATE`,
  `GOOD_COMMUNICATOR`, `WOULD_JOIN_AGAIN` 중에서 선택합니다.
- 성공하면 `201 Created`를 반환합니다.
