# 탐색 API 계약

탐색 목록 API의 요청 파라미터와 필터 결합 규칙을 정리합니다. API 응답 봉투와
오류 코드는 [API 응답 및 오류 코드 컨벤션](API_RESPONSE_CONVENTION.md)을 따릅니다.

## 공통 taxonomy

Event와 Place는 2026-08-06 `operational_v9` 핸드오프의 공통 taxonomy를 사용합니다.
Sector ID는 `1~4`, Activity ID는 `1~56`이며, 같은 Activity ID를 두 item 유형의
필터에 함께 전달합니다. 로컬 미리보기 SQL은
[`database/EXPLORE_TAXONOMY_MOCK_DATA_LOCAL_ONLY.sql`](database/EXPLORE_TAXONOMY_MOCK_DATA_LOCAL_ONLY.sql)
을 기준으로 합니다.

이 핸드오프는 현재 로컬 적재와 API 연동 기준이며, 운영 taxonomy의 최종 권위로
확정된 것은 아닙니다. 실제 크롤러 분류가 제공되기 전까지 로컬 연결 데이터는
필터 동작 확인을 위한 분류입니다.

## Event 목록

`GET /api/v1/explore/events`

`/api/**` 경로는 인증이 필요합니다. 현재 백엔드는 `access_token` HttpOnly 쿠키를
읽는 방식이므로 Swagger의 Bearer `Authorize`만으로는 인증되지 않습니다. 인증된
브라우저 세션이나 로컬 테스트용 access token 쿠키를 사용해야 합니다.

### 주요 요청 파라미터

| 파라미터 | 형식 | 의미 |
| --- | --- | --- |
| `eventKinds` | 반복 가능한 문자열 목록 | Event 유형을 같은 종류 안에서 OR로 필터링합니다. |
| `sectorIds` | 반복 가능한 숫자 목록 | Sector를 같은 종류 안에서 OR로 필터링합니다. |
| `activityIds` | 반복 가능한 숫자 목록 | Activity를 같은 종류 안에서 OR로 필터링합니다. |
| `region1` | 반복 가능한 문자열 목록 | 광역 지역을 같은 종류 안에서 OR로 필터링합니다. |
| `region2` | 반복 가능한 문자열 목록 | 선택한 `region1`의 세부 지역을 같은 종류 안에서 OR로 필터링합니다. |
| `region2Other` | boolean | `region1`이 선택된 경우, `region2`가 비어 있거나 분류되지 않은 Event를 포함합니다. `region1` 없이 사용하면 결과를 만들지 않습니다. |
| `region3` | 반복 가능한 문자열 목록 | 세부 지역을 같은 종류 안에서 OR로 필터링합니다. |
| `keyword` | 문자열 | 제목·부제목·설명에 대해 부분 일치 검색을 적용합니다. |
| `startDate`, `endDate` | `yyyy-MM-dd` | 기간 조건입니다. |
| `freeOnly` | boolean | 무료 Event만 조회합니다. |
| `openWeekendOnly` | boolean | 주말 운영 Event만 조회합니다. |
| `opensLateOnly` | boolean | 야간 운영 Event만 조회합니다. |
| `preReservationOnly` | boolean | 사전 예약 가능한 Event만 조회합니다. |
| `experienceOnly` | boolean | 체험형 Event만 조회합니다. |
| `photoZoneOnly` | boolean | 포토존이 있는 Event만 조회합니다. |
| `sort` | `LATEST` 또는 `POPULAR` | 최신순 또는 인기순으로 정렬합니다. |
| `page` | 0 이상의 정수 | 0부터 시작하는 페이지 번호입니다. |
| `size` | 양의 정수 | 페이지 크기입니다. 기본값은 20입니다. |

### 필터 결합

- 같은 종류의 다중 값은 OR 조건으로 적용합니다.
- 지역, Sector, Activity, 검색어, 옵션처럼 서로 다른 종류의 필터는 AND 조건으로 적용합니다.
- 여러 Activity에 연결된 Event도 목록에는 한 번만 반환합니다.
- 공개 상태가 `APPROVED`·`VISIBLE`이고 Event 상태가 `SCHEDULED` 또는 `ONGOING`인
  데이터만 목록 대상입니다.
- `savedOnly`는 저장 API 계약이 준비되기 전까지 공개 목록 요청에서 사용하지 않습니다.
  프론트엔드도 저장 정렬/필터를 노출하지 않습니다.

### 응답 및 페이지 표시

응답의 `data.content`가 현재 페이지에 포함된 목록입니다. `totalElements`는 전체
검색 결과 수이며, 현재 프론트엔드는 무한 스크롤을 구현하지 않았으므로 화면의 건수
표시는 현재 페이지의 `content.length`를 사용합니다.

## Place 목록

`GET /api/v1/explore/places`

인증된 요청으로 공개 Place를 조회할 수 있습니다. 단, `savedOnly=true`는 인증된
회원만 사용할 수 있습니다.

### 주요 요청 파라미터

| 파라미터 | 형식 | 의미 |
| --- | --- | --- |
| `placeKinds` | 반복 가능한 문자열 목록 | `RESTAURANT`, `CAFE`, `MARKET`, `BEAUTY`, `ETC`를 같은 종류 안에서 OR로 필터링합니다. |
| `sectorIds` | 반복 가능한 숫자 목록 | Sector를 같은 종류 안에서 OR로 필터링합니다. |
| `activityIds` | 반복 가능한 숫자 목록 | Activity를 같은 종류 안에서 OR로 필터링합니다. |
| `region1`, `region2`, `region3` | 반복 가능한 문자열 목록 | 각 지역 단계 안에서는 OR, 단계 사이에는 AND로 필터링합니다. 현재 프론트엔드는 `region1`·`region2`를 사용하며, 사용자 위치 기반 기능은 `region3`까지 전달할 수 있습니다. |
| `keyword` | 문자열 | 이름·브랜드·지점·도로명 주소·상세 주소에 부분 일치 검색을 적용합니다. |
| `hasForeignLang` | boolean | 외국어 안내가 있는 Place만 조회합니다. |
| `hasParking` | boolean | 주차 가능한 Place만 조회합니다. |
| `reservable` | boolean | 예약 가능한 Place만 조회합니다. |
| `takeoutAvailable` | boolean | 포장 가능한 Place만 조회합니다. |
| `cardPaymentAvailable` | boolean | 카드 결제 가능한 Place만 조회합니다. |
| `smokeFree` | boolean | 금연 Place만 조회합니다. |
| `kidFacility` | boolean | 유아 시설이 있는 Place만 조회합니다. |
| `hasRestroom` | boolean | 화장실이 있는 Place만 조회합니다. |
| `savedOnly` | boolean | 인증한 회원이 저장한 Place만 조회합니다. |
| `openNow` | boolean | `true`이면 서울 현재 시각에 영업 중임을 확실히 판정할 수 있는 Place만 조회합니다. |
| `sort` | `LATEST` 또는 `POPULAR` | 최신순 또는 인기순으로 정렬합니다. |
| `language` | 문자열 | Activity·Sector 이름 언어입니다. `en`은 영문, 그 외에는 한글입니다. |
| `page` | 0 이상의 정수 | 0부터 시작하며 잘못된 음수는 0으로 보정합니다. |
| `size` | 양의 정수 | 기본값은 20, 최댓값은 100입니다. |

### 필터 및 데이터 공개 규칙

- 같은 종류의 다중 값은 OR, 서로 다른 종류의 필터는 AND로 결합합니다.
- 여러 Activity에 연결된 Place도 목록에는 한 번만 반환합니다.
- `APPROVED`·`VISIBLE`이며 삭제되지 않은 활성 Place만 목록과 상세에 반환합니다.
- 저장 여부는 `explore_item_likes`의 삭제되지 않은 데이터로 판단합니다.
- DB의 세부 Place 유형은 API에서 `RESTAURANT`, `CAFE`, `MARKET`, `BEAUTY`,
  `ETC`로 정규화합니다. 알 수 없는 유형은 `ETC`로 반환합니다.

### 현재 영업 중 판정

크롤러가 전달하는 `openingHours`는 주로 다음처럼 원문 문자열을 가진 JSON입니다.

```json
{
  "raw": "11:30~22:00 (브레이크타임 14:30~17:00)"
}
```

`closedDays`는 `［"매주 일요일"］`, `［"연중무휴"］`,
`［"설·추석 연휴"］` 같은 JSON 배열입니다. 상세 응답은 이 원문 JSON을 변경하지
않고 반환합니다.

`openNow=true`는 `Asia/Seoul` 기준으로 단순 영업시간, 자정을 넘는 영업시간,
24시간 운영, 명시된 휴게·준비 시간, 정기 요일 휴무를 보수적으로 해석합니다.
운영시간이 없거나 복잡해서 확실히 판정할 수 없는 Place는 결과에서 제외합니다.
향후 구조화된 운영시간 데이터가 제공되면 DB 필터로 교체할 수 있습니다.

### 응답

목록 응답은 `data.content`, `page`, `size`, `totalElements`, `totalPages`,
`last`를 반환합니다. 상세 응답은 기본 정보와 주소·좌표, 운영시간 원문, 편의 옵션,
조회·저장 수, 연결된 Activity와 Sector를 반환합니다.

## Place 상세

`GET /api/v1/explore/places/{placeId}?language=en`

공개·활성 조건을 만족하는 Place만 조회합니다. 없거나 비공개·비활성·삭제 상태이면
HTTP 404와 `EXPLORE-002`를 반환합니다.
