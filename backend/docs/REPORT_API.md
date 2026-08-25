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

### 지출은 순액이다

지출 한 건의 금액은 결제액이 아니라 **정산으로 회수하고 남은 액수**다. 5만 원을 결제하고
정산으로 2만 원을 돌려받았으면 그 지출은 3만 원이다. 후보 목록과 스냅샷, 비교 응답이 모두
같은 정의를 쓴다.

- 회수액은 **실제로 받은 몫만** 센다(`settlement_members.request_status = 'PAID'`). 아직 받지
  못한 몫은 빼지 않는다 — 스냅샷은 만든 뒤 갱신되지 않으므로, 못 받은 돈을 미리 빼면 리포트가
  실제로 부담한 액수보다 작게 굳는다.
- 원결제자 본인 몫은 정산을 만들 때 `NOT_REQUESTED`로 들어가고 `PENDING`에서만 `PAID`로
  넘어가므로 회수액에 섞이지 않는다.
- 정산을 낸 사람 쪽에서는 그 이체가 본인의 지출로 잡힌다. 실제 지급일이 여정 종료 뒤여도
  빠지지 않도록 날짜와 카테고리는 원 결제에서 가져온다.
- 회수액은 원 결제 행에서 뺀다. 그래서 날짜와 카테고리가 원 결제 그대로 남는다.
  정산으로 들어온 CREDIT 원장을 따로 세지는 않는다.

## 비교

```http
GET /api/v1/reports/{reportId}/comparison?scope=GROUP
```

`scope`는 `GROUP`(기본) 또는 `SIMILAR`다. 그 밖의 값은 enum 바인딩에 실패해 `COMMON-001`(400)이다.
`?scope=`처럼 값을 비우면 기본값이 아니라 `null`로 바인딩되어 `REPORT-003`(400)이 된다.
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

- 동료 = 내 약속들의 **다른 ACTIVE 참가자**. 내 약속은 두 갈래로 모은다 — 리포트 여정의
  `trip_items`(CONFIRMED)가 가리키는 약속, 그리고 `appointment_members.trip_id`가 이 여정인 내 ACTIVE
  참여. #407부터 만들 때도 참여할 때도 둘 다 채워지므로 방장·참가자가 같은 길로 잡힌다. 그 전에
  참여한 약속(둘 다 비어 있음)은 잡히지 않는다.
- **취소된 약속(`appointment_status = 'CANCELLED'`)은 제외한다.** 취소는 `deleted_at`을 남기지
  않고, 취소 약속은 방장 아닌 사람에게 목록에서 감춰지기 때문이다.
- 기간이 겹치는 여정을 여러 개 가져도 다른 여정의 약속 동료는 섞이지 않는다 — 활동일로 근사하던
  갈래는 #415에서 뺐다.
- 나와 동료 모두 **원 결제일을 기준으로 여정 기간 안인 완료된 QR 결제·정산**(결제자 본인 지갑의
  DEBIT, KRW)을 지금 다시 합산한다(`basis: LIVE`). 저장된 스냅샷은 내가 고른 지출만 담고 있어
  동료와 정의가 다르다.
  그래서 `me.totalSpent`가 상세 화면의 `analytics.totalSpent`와 다를 수 있다.
- 금액은 상세 화면과 같은 순액이다 — 결제자의 QR 결제에서 실제로 받은 정산 몫을 뺀다. 그래서 한
  계산서를 결제자와 동료가 이중으로 세지 않는다. 정의는 위 「지출은 순액이다」와 같다.
- 정산을 낸 사람의 분담액도 원 결제 날짜와 카테고리에 귀속한다. 종료 뒤 지급된 분담액이
  기간 필터에서 사라지거나 `OTHER`로 갈라지지 않는다.
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
