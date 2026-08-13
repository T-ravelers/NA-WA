# 약속 및 후기 API 계약

이 문서는 탐색 Event·Place에서 생성하는 약속의 조회와 회원 후기 API 계약을 정의합니다.
모든 경로는 인증이 필요하며 공통 `ApiResponse` 형식을 사용합니다.

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

결제 대기 상태인 기존 `PENDING` 회원만 참여를 취소할 수 있으며, 취소 시 참가 상태는
`LEFT`, 대기 중인 보증금은 `CANCELLED`로 변경합니다. 이미 예치된 `HELD` 보증금의
환급은 지갑·에스크로 연동이 완료되기 전에는 지원하지 않습니다.

현재 범위에서 방장 취소와 방장 권한 이전은 결제 대기 상태인 기존 `PENDING` 회원의
취소에만 적용됩니다. 동일한 가입 시각이면 참여 ID가 작은 회원을 우선합니다. 승계할
회원 없이 방장만 참여 중인 약속은 취소할 수 없습니다. `ACTIVE` 회원의 취소·환급과
`HELD` 보증금의 환급은 지갑·에스크로 연동 후속 범위입니다. `IN_PROGRESS`,
`COMPLETED`, `CANCELLED` 약속에서도 참여를 취소할 수 없습니다.

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
