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
