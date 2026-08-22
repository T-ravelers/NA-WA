# Report API 계약

이 문서를 읽으면 여정 최종 리포트(스냅샷)를 만들고 읽는 API와, 리포트를 다른 사람과
비교하는 API의 요청·응답을 알 수 있다. 공통 응답 형식과 오류 코드 규칙은
[API_RESPONSE_CONVENTION.md](./API_RESPONSE_CONVENTION.md)를 따른다. 소비 카테고리 값과
칭호 규칙은 [SPENDING_CATEGORY.md](./SPENDING_CATEGORY.md)에 있다.

모든 엔드포인트는 인증이 필요하고, 리포트의 소유자(`trips.member_id`)만 읽을 수 있다.
다른 회원의 리포트는 `REPORT-002`(403)다.

## 스냅샷

| 메서드 | 경로 | 하는 일 |
| --- | --- | --- |
| `POST` | `/api/v1/journeys/{tripId}/reports` | 종료된 여정의 리포트 스냅샷을 만든다. 본문 `transferIds`(선택)로 포함할 지출을 고른다 |
| `GET` | `/api/v1/journeys/{tripId}/report-expense-candidates` | 리포트에 넣을 수 있는 지출 목록 |
| `GET` | `/api/v1/reports` | 내 리포트 목록 |
| `GET` | `/api/v1/reports/{reportId}` | 리포트 상세. `reportContent.analytics`에 총 지출·일 평균·카테고리 구성·일별 추이가 있다 |

스냅샷은 만든 뒤 바뀌지 않는다. 같은 여정에 활성 리포트가 있으면 같은 지출 집합일 때만
기존 리포트를 돌려주고, 아니면 `REPORT-005`(409)다.

## 비교

```http
GET /api/v1/reports/{reportId}/comparison?scope=GROUP
```

`scope`는 `GROUP`(기본) 또는 `SIMILAR`다. 그 밖의 값은 enum 바인딩에 실패해 `COMMON-001`(400)이다.
**숫자는 전부 0 이상이고 문구는 싣지 않는다.** 차이의 부호와 표현은 프론트엔드가 비중으로 계산한다.

### 응답

```json
{
  "success": true,
  "data": {
    "scope": "GROUP",
    "basis": "LIVE",
    "me": {
      "memberId": 1,
      "displayName": "Mingyu",
      "profileImageUrl": null,
      "totalSpent": 1284500.0000,
      "dailyAverage": 128450.00,
      "categoryBreakdown": [
        { "category": "FOOD", "amount": 539500.0000, "percentage": 42.00 }
      ]
    },
    "peers": [
      {
        "memberId": 2,
        "displayName": "Mina",
        "profileImageUrl": null,
        "totalSpent": 978400.0000,
        "dailyAverage": 97840.00,
        "categoryBreakdown": [
          { "category": "SHOPPING", "amount": 600000.0000, "percentage": 61.32 }
        ]
      }
    ],
    "cohort": {
      "size": 1,
      "avgTotalSpent": 978400.00,
      "avgDailyAverage": 97840.00,
      "categoryBreakdown": [
        { "category": "SHOPPING", "amount": 600000.00, "percentage": 61.32 }
      ]
    },
    "ranks": [
      { "category": "FOOD", "rank": 1, "of": 2 }
    ]
  }
}
```

| 필드 | 뜻 |
| --- | --- |
| `basis` | 숫자의 출처. `LIVE`는 여정 기간의 결제를 지금 다시 합산한 것, `SNAPSHOT`은 저장된 리포트 스냅샷을 읽은 것 |
| `me` | 내 지출. `categoryBreakdown`은 금액 내림차순·카테고리 오름차순이고 `percentage`는 0–100 |
| `peers` | 비교 대상 개인 목록. `SIMILAR`에서는 항상 비어 있다(개인을 노출하지 않는다) |
| `cohort` | 비교 대상 전체의 평균. `size`가 0이면 나머지는 0과 빈 목록 — 화면은 빈 상태를 그린다 |
| `ranks` | 내 카테고리마다 나보다 많이 쓴 사람 수 + 1. `of`는 나를 포함한 인원. 비교 대상이 없으면 빈 목록 |

### `GROUP` — 같은 약속 동료

- 동료 = 내 약속들의 **다른 ACTIVE 참가자**. 내 약속은 세 갈래로 모은다.
  - 리포트 여정의 `trip_items`(CONFIRMED)가 가리키는 약속 — 내가 만든 약속만 여기 잡힌다.
  - 내가 ACTIVE로 참여한 약속 중 **활동일이 여정 기간 안**인 것. 약속에 참여만 하면
    `trip_items`도 `appointment_members.trip_id`도 생기지 않아, 이 갈래가 없으면 참가자에게는
    `peers`가 항상 빈다.
  - `appointment_members.trip_id` — 운영 코드가 아직 채우지 않는다. 장래 대비다.
- **취소된 약속(`appointment_status = 'CANCELLED'`)은 제외한다.** 취소는 `deleted_at`을 남기지
  않고, 취소 약속은 방장 아닌 사람에게 목록에서 감춰지기 때문이다.
- **알려진 한계**: 참가자 갈래에 여정 연결이 없어 활동일로 근사한다. 기간이 겹치는 여정을 여러 개
  가지면 다른 여정의 약속 동료가 섞일 수 있다. `appointment_members.trip_id`를 채우기 시작하면
  이 갈래를 뺀다.
- 나와 동료 모두 **여정 기간 안의 완료된 QR 결제·정산**(결제자 본인 지갑의 DEBIT, KRW)을 지금 다시
  합산한다(`basis: LIVE`). 저장된 스냅샷은 내가 고른 지출만 담고 있어 동료와 정의가 다르다.
  그래서 `me.totalSpent`가 상세 화면의 `analytics.totalSpent`와 다를 수 있다.
- **알려진 한계**: 정산은 카테고리가 없어 `OTHER`로 잡히고, 한 계산서를 결제자의 QR 전액과 동료의
  정산 분담이 각각 세므로 그룹 안에서 이중 계상된다. 환급 CREDIT은 빼지 않는다.
- `wallet_transfers.initiator_member_id`에는 인덱스가 없다. 시연 규모에서는 문제없고, 인덱스는
  후속 마이그레이션이다.

### `SIMILAR` — 같은 국적 회원

- 코호트 = `members.nationality_code`가 나와 같고 `COMPLETED` 리포트가 있는 다른 TRAVELER 회원.
  각자의 **최신 리포트 스냅샷** `analytics`를 평균한다(`basis: SNAPSHOT`). 최대 200명.
- 내 국적이 비어 있으면 `cohort.size`는 0이다. 연령대는 `members`에 컬럼이 없어 아직 범위 밖이다.
- `analytics`가 없던 시절의 스냅샷은 코호트에서 뺀다.

## 오류 코드

| enum 상수 | 오류 코드 | HTTP 상태 | 의미 |
| --- | --- | --- | --- |
| `REPORT_NOT_FOUND` | `REPORT-001` | 404 | 리포트가 없음 |
| `REPORT_JOURNEY_FORBIDDEN` | `REPORT-002` | 403 | 내 여정·리포트가 아님 |
| `INVALID_REPORT_INPUT` | `REPORT-003` | 400 | 입력값이 올바르지 않음 |
| `JOURNEY_NOT_COMPLETED` | `REPORT-004` | 400 | 종료되지 않은 여정 |
| `REPORT_ALREADY_EXISTS` | `REPORT-005` | 409 | 활성 리포트가 이미 있음 |
| `REPORT_JOURNEY_NOT_FOUND` | `REPORT-006` | 404 | 여정이 없음 |
| `INVALID_REPORT_EXPENSE` | `REPORT-007` | 400 | 고른 지출이 적격이 아님 |
| `REPORT_EXPENSE_ALREADY_LINKED` | `REPORT-008` | 409 | 다른 여정 리포트에 이미 연결된 지출 |
