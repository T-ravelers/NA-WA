# Journey API 계약

이 문서는 Journey 설정 수정 API의 요청·응답 및 충돌 처리 계약을 정의합니다.
공통 응답 형식은 [API_RESPONSE_CONVENTION.md](API_RESPONSE_CONVENTION.md)를
따릅니다.

## Journey 설정 일괄 수정

```http
PUT /api/v1/journeys/{tripId}
```

- 인증이 필요합니다.
- 해당 Journey의 소유자만 수정할 수 있습니다.
- 부분 수정이 아닌 전체 설정 교체입니다. 화면의 `Save changes`는 현재 값을 모두
  전송해야 합니다.
- Journey 설정과 지역 목록은 하나의 트랜잭션에서 저장됩니다. 중간 단계가 실패하면
  제목, 기간, 예산, 동행 성향, 지역 변경이 모두 롤백됩니다.
- 설정 수정과 일정 항목 추가는 같은 Journey 행을 `FOR UPDATE`로 잠급니다. 두 요청이
  동시에 들어오면 먼저 잠금을 획득한 요청이 끝난 뒤 다음 요청이 최신 설정을 기준으로
  처리됩니다.

### 요청

```json
{
  "title": "제주 여름 여행",
  "startDate": "2026-08-20",
  "endDate": "2026-08-24",
  "budgetAmount": 1320000,
  "companionPreference": "FRIENDS",
  "regions": [
    {
      "regionCode": "JEJU",
      "regionName": "제주특별자치도",
      "displayOrder": 0
    }
  ]
}
```

| 필드 | 필수 | 계약 |
| --- | ---: | --- |
| `title` | O | 공백 제외 1~100자 |
| `startDate` | O | `yyyy-MM-dd` |
| `endDate` | O | `yyyy-MM-dd`, `startDate` 이상 |
| `budgetAmount` | X | `null` 허용, 0 이상, 정수부 최대 15자리·소수부 최대 4자리 |
| `companionPreference` | X | `null` 또는 공백 허용, 공백 제외 최대 30자 |
| `regions` | O | 배열 자체는 필수이며 빈 배열 허용 |
| `regions[].regionCode` | O | 공백 제외 1~30자, 대소문자 무시 중복 불가 |
| `regions[].regionName` | O | 공백 제외 1~100자 |
| `regions[].displayOrder` | O | 0~32767 정수 |

`regions`는 최종 목록입니다. 기존 목록에 추가하는 방식이 아닙니다.

- `regions: []`이면 활성 지역을 모두 제거합니다.
- 같은 `regionCode`로 soft-delete된 행이 있으면 새 행을 만들지 않고 이름과 순서를
  갱신해 복구합니다.
- `regions` 필드를 생략하거나 `null`로 보내면 `JOURNEY-003`입니다.

### 성공 응답

`200 OK`

```json
{
  "success": true,
  "data": {
    "tripId": 20,
    "title": "제주 여름 여행",
    "startDate": "2026-08-20",
    "endDate": "2026-08-24",
    "budgetAmount": 1320000,
    "companionPreference": "FRIENDS",
    "regions": [
      {
        "regionCode": "JEJU",
        "regionName": "제주특별자치도",
        "displayOrder": 0
      }
    ]
  }
}
```

### 기간 변경 충돌

변경할 `startDate`~`endDate` 밖에 삭제되지 않은 일정 항목(`trip_items`)이 하나라도
있으면 서버는 일정을 자동 삭제하거나 이동하지 않고 전체 수정을 거절합니다.

`409 Conflict`

```json
{
  "success": false,
  "error": {
    "code": "JOURNEY-009",
    "message": "변경할 Journey 기간을 벗어나는 일정이 있습니다."
  }
}
```

프론트엔드는 `JOURNEY-009`를 받으면 기간 밖 일정이 남아 있어 저장할 수 없다는 경고를
보여주고, 사용자가 Journey 상세에서 해당 이벤트·플레이스를 먼저 삭제하도록 안내해야
합니다.

### 오류 코드

| HTTP | 오류 코드 | 발생 조건 |
| ---: | --- | --- |
| 400 | `JOURNEY-003` | `tripId` 또는 요청 필드·지역 목록이 유효하지 않음 |
| 403 | `JOURNEY-002` | 다른 회원이 소유한 Journey 수정 요청 |
| 404 | `JOURNEY-001` | 삭제됐거나 존재하지 않는 Journey |
| 409 | `JOURNEY-009` | 변경 기간 밖에 활성 일정 항목이 존재함 |

## Journey 일정 항목 추가

```http
POST /api/v1/journeys/{tripId}/items
```

- 인증이 필요하며 해당 Journey의 소유자만 추가할 수 있습니다.
- Journey 행을 `FOR UPDATE`로 잠근 뒤 처리하므로, 설정 수정과 동시에 들어오면 먼저
  잠금을 획득한 요청이 끝난 다음 최신 기간을 기준으로 판정합니다.
- 추가할 수 있는 Explore 항목은 `EVENT`와 `PLACE`뿐입니다. 삭제됐거나 승인·노출
  상태가 아닌 항목, 이미 종료된 이벤트는 `JOURNEY-005`입니다.

### 요청

```json
{
  "itemId": 301,
  "visitDate": "2026-08-21",
  "displayOrder": 0,
  "note": "오전 방문"
}
```

| 필드 | 필수 | 계약 |
| --- | ---: | --- |
| `itemId` | O | 1 이상의 Explore 항목 id |
| `visitDate` | O | `yyyy-MM-dd` |
| `displayOrder` | X | `null` 허용, 0~32767 정수. 생략하면 0 |
| `note` | X | `null`·공백 허용, 공백 제외 최대 500자 |

### 방문 날짜가 지켜야 하는 두 기간

`visitDate`는 **서로 다른 두 기간을 모두** 만족해야 하며, 위반한 기간에 따라 오류
코드가 다릅니다.

| 기간 | 조건 | 위반 시 |
| --- | --- | --- |
| Journey 기간 | `startDate ≤ visitDate ≤ endDate` | `JOURNEY-007` |
| 항목 운영 기간 | `event.start_date ≤ visitDate ≤ event.end_date` | `JOURNEY-012` |

항목 운영 기간은 **`EVENT`에만 적용됩니다.** `place` 테이블에는 운영 기간 컬럼이
없으므로 `PLACE` 항목은 Journey 기간만 봅니다.

종료일을 받지 못한 Event(`is_permanent = TRUE`)는 `chk_event_period` 불변식에 따라
`end_date`가 반드시 `NULL`이므로 운영 기간을 `[start_date, ∞)`로 봅니다. 이 값은
"상시 운영"이 아니라 끝을 모른다는 뜻입니다([EXPLORE_API.md](./EXPLORE_API.md) 참고).
**상한만 없을 뿐 하한은 있습니다** — 아직 시작하지 않은 Event는 `JOURNEY-012`로
거절됩니다.

두 검사는 Journey 기간이 먼저입니다. 둘 다 어긋나면 `JOURNEY-007`을 받습니다.

### 성공 응답

`201 Created`

```json
{
  "success": true,
  "data": {
    "tripItemId": 901,
    "journeyId": 20,
    "itemId": 301,
    "itemType": "EVENT",
    "visitDate": "2026-08-21",
    "tripItemStatus": "ADDED",
    "displayOrder": 0,
    "note": "오전 방문",
    "appointmentId": null,
    "confirmedAt": null
  }
}
```

이 경로로 만든 일정은 항상 `ADDED`이며 `appointmentId`와 `confirmedAt`은 비어
있습니다. `CONFIRMED`는 약속 생성 경로에서만 만들어집니다
([APPOINTMENT_API.md](APPOINTMENT_API.md) 참고).

### 오류 코드

| HTTP | 오류 코드 | 발생 조건 |
| ---: | --- | --- |
| 400 | `JOURNEY-003` | `tripId`·`itemId`·`visitDate`가 없거나 유효하지 않음, `note`가 500자 초과 |
| 400 | `JOURNEY-006` | `EVENT`·`PLACE`가 아닌 Explore 항목 유형 |
| 400 | `JOURNEY-007` | `visitDate`가 Journey 기간을 벗어남 |
| 400 | `JOURNEY-008` | `displayOrder`가 0 미만이거나 32767 초과 |
| 400 | `JOURNEY-012` | `visitDate`가 항목(이벤트)의 운영 기간을 벗어남 |
| 403 | `JOURNEY-002` | 다른 회원이 소유한 Journey에 추가 요청 |
| 404 | `JOURNEY-001` | 삭제됐거나 존재하지 않는 Journey |
| 404 | `JOURNEY-005` | 삭제·미승인·비노출이거나 이미 종료된 Explore 항목 |
| 409 | `JOURNEY-004` | 같은 `(tripId, itemId, visitDate)` 활성 일정이 이미 존재함 |

## Journey 항목·방문 날짜 조합 중복 확인

```http
GET /api/v1/journeys/{tripId}/items/exists?itemId={itemId}&visitDate={visitDate}
```

- 약속 생성 폼의 날짜 선택 단계에서, 선택하려는 `(tripId, itemId, visitDate)`
  자리의 상태를 미리 확인하기 위한 조회 전용 API입니다.
- **두 값을 따로 돌려줍니다.** 서로 다른 질문에 답하기 때문입니다.
  - `exists`: 그 자리에 활성 일정이 있는지. **Journey 일정 추가**(`POST
    /api/v1/journeys/{tripId}/items`)가 `JOURNEY-004`로 거절되는 조건입니다.
  - `appointmentLinked`: 그 자리에 **다른 약속이** 걸려 있는지. **약속 생성**(`POST
    /api/v1/appointments`)이 `JOURNEY-004`로 거절되는 조건입니다.
- 담아만 둔 자리(`ADDED`, `appointment_id`가 비어 있음)는 약속 생성이 약속 항목으로
  승격시키므로 `exists`가 `true`여도 `appointmentLinked`는 `false`입니다. **약속
  생성 화면은 `appointmentLinked`로 판단해야 합니다** — `exists`로 막으면 담아 둔
  장소로는 약속을 만들 수 없습니다.
- 인증 회원이 소유한 Journey만 조회할 수 있습니다.
- `visitDate`는 `yyyy-MM-dd` 형식입니다.
- 이 API는 조회만 하며 아무것도 저장하지 않습니다. 실제 확정은
  `POST /api/v1/appointments`([APPOINTMENT_DEPOSIT_STATE_MACHINE.md](APPOINTMENT_DEPOSIT_STATE_MACHINE.md)
  참고)가 같은 트랜잭션에서 처리하므로, 이 조회와 실제 약속 생성 사이의 시간차
  동안 다른 세션이 같은 자리를 먼저 약속으로 차지하면 최종 생성 요청이
  `JOURNEY-004`로 거부될 수 있습니다.

### 성공 응답

`200 OK`

```json
{
  "success": true,
  "data": {
    "exists": false,
    "appointmentLinked": false
  }
}
```

### 오류 코드

| HTTP | 오류 코드 | 발생 조건 |
| ---: | --- | --- |
| 400 | `COMMON-001` | `itemId`가 숫자가 아니거나 `visitDate`가 `yyyy-MM-dd` 형식이 아니거나 누락됨(Spring이 컨트롤러 진입 전에 파라미터 바인딩에서 거부) |
| 400 | `JOURNEY-003` | `itemId`가 파싱은 되지만 0 이하로 유효하지 않음 |
| 403 | `JOURNEY-002` | 다른 회원이 소유한 Journey 조회 요청 |
| 404 | `JOURNEY-001` | 삭제됐거나 존재하지 않는 Journey |

## Journey 타임라인 조회 언어

```http
GET /api/v1/journeys/{tripId}/timeline?language={language}
```

- `language`는 선택이며 생략하거나 공백이면 `en`으로 처리합니다.
- 지원 값은 `en`, `ja`, `zh-TW`, `vi`입니다. 대소문자와 앞뒤 공백은 정규화하며,
  지원하지 않는 값은 `COMMON-001`(400)입니다. 정규화 규칙은
  `me.nawa.common.i18n.SupportedLanguagePolicy` 하나를 Explore 조회와 함께 씁니다 —
  두 도메인이 각자 정규화하면 같은 요청에 서로 다른 언어가 나갑니다.
- Event와 Place의 사용자 표시 필드는 필드별로 다음 순서로 선택합니다.
  1. 요청 언어의 비어 있지 않은 번역
  2. 영어(`en`)의 비어 있지 않은 번역
  3. Event 또는 Place 본체의 한국어 원문

  Explore 목록·상세([EXPLORE_API.md](EXPLORE_API.md#표시-언어))도 같은 3단 순서를
  씁니다. 처음에는 Explore가 "요청 언어 → 한국어" 2단만 썼는데, 그러면 요청 언어 번역이
  없고 영어 번역만 있는 항목을 Explore에서는 한국어로, Journey에 담은 뒤에는 영어로 보는
  어긋남이 있었다(#536).
- 이 규칙은 제목, 표시 주소와 상세 주소, 주최자·장소 상세·메뉴 요약처럼 타임라인
  응답에 포함되는 번역 가능 필드에 적용합니다.
- `region1`, `region2`, `region3`는 번역 테이블의 필드가 아니므로 원본 값을 유지합니다.
  Journey 화면은 번역된 `addressRoad`가 있으면 원본 지역명보다 먼저 표시합니다.
- 응답 언어는 저장된 `trip_items`를 바꾸지 않습니다. 같은 일정도 요청 `language`에 따라
  현재 표시 문자열이 달라집니다.

### 오류 코드

| HTTP | 오류 코드 | 발생 조건 |
| ---: | --- | --- |
| 400 | `COMMON-001` | `language`가 지원 목록 밖임 |
| 403 | `JOURNEY-002` | 다른 회원이 소유한 Journey 타임라인 요청 |
| 404 | `JOURNEY-001` | 삭제됐거나 존재하지 않는 Journey |

## Journey 개별 일정 삭제

```http
DELETE /api/v1/journeys/{tripId}/items/{tripItemId}
```

- 인증 회원이 소유한 Journey의 일정만 삭제할 수 있습니다.
- `ADDED` 일정은 `trip_items`에서 soft delete합니다.
- `CONFIRMED` 일정은 현재 회원이 Appointment 방장이 아닐 때만 기존 Appointment 참여
  취소를 먼저 수행합니다. 참여 취소와 일정 삭제는 같은 트랜잭션이며, 둘 중 하나라도
  실패하면 일정은 유지됩니다.
- Journey 삭제 경로는 Appointment 방장 승계를 수행하지 않습니다. Issue #205의 제품
  계약이 방장 Appointment 삭제와 방장 승계를 범위에서 제외하므로, 방장 일정은
  `JOURNEY-011`로 거부합니다. 방장은 Appointment 참여 취소 API에서 후계자에게 방장을
  승계하고 참여 취소를 완료한 뒤 Journey 일정 삭제를 다시 요청할 수 있습니다. 이미
  `LEFT`인 참여는 Journey 경로에서 다시 취소하지 않습니다.

성공하면 응답 본문 없이 `204 No Content`를 반환합니다.

### soft-delete 일정 재추가

V17부터 `trip_items` 유니크 키는 `deleted_at IS NULL`인 활성 일정에만 적용됩니다.
따라서 soft delete한 일정과 같은 `itemId`·`visitDate` 조합 또는 같은 Appointment
연결을 새 일정으로 다시 추가할 수 있습니다. 활성 일정의 중복은 계속
`JOURNEY-004`(409)를 반환합니다. 삭제 이력은 복구하거나 hard delete하지 않습니다.

### 오류 코드

| HTTP | 오류 코드 | 발생 조건 |
| ---: | --- | --- |
| 400 | `JOURNEY-003` | `tripId` 또는 `tripItemId`가 유효하지 않음 |
| 403 | `JOURNEY-002` | 다른 회원이 소유한 Journey의 일정 삭제 요청 |
| 404 | `JOURNEY-001` | 삭제됐거나 존재하지 않는 Journey |
| 404 | `JOURNEY-010` | 삭제됐거나 존재하지 않는 일정 |
| 409 | `JOURNEY-011` | 현재 회원이 연결된 Appointment의 방장임 |
| 409 | `APPOINTMENT-007` | 참여가 `ACTIVE`이거나 Appointment 상태상 취소할 수 없음 |

## Journey 전체 삭제

```http
DELETE /api/v1/journeys/{tripId}
```

- 삭제 전에 모든 `CONFIRMED` 일정을 검사합니다. 방장 Appointment가 하나라도 있으면
  어떤 참여 취소나 soft delete도 시작하지 않고 `JOURNEY-011`로 거부합니다.
- 비방장 Appointment 참여 취소를 모두 완료한 뒤 `trip_items`, `trip_regions`,
  `reports`, `trip_expense_links`, `trips`를 한 트랜잭션에서 soft delete합니다.
- `trip_expense_links`만 삭제하며 Wallet 원장과 결제·보증금·Appointment 기록은
  삭제하지 않습니다. Report와 expense link를 Journey와 함께 숨기는 범위는 Issue
  #206의 명시 계약입니다.
- 참여 취소 또는 하위 데이터 처리 하나라도 실패하면 전체 변경을 롤백합니다.

성공하면 응답 본문 없이 `204 No Content`를 반환합니다.

### 오류 코드

| HTTP | 오류 코드 | 발생 조건 |
| ---: | --- | --- |
| 400 | `JOURNEY-003` | `tripId`가 유효하지 않음 |
| 403 | `JOURNEY-002` | 다른 회원이 소유한 Journey 삭제 요청 |
| 404 | `JOURNEY-001` | 삭제됐거나 존재하지 않는 Journey |
| 409 | `JOURNEY-011` | 방장으로 참여 중인 Appointment가 하나 이상 존재함 |
| 409 | `APPOINTMENT-007` | 비방장 참여 중 취소할 수 없는 상태가 하나 이상 존재함 |
