# 탐색 API 계약

탐색 목록 API의 요청 파라미터와 필터 결합 규칙을 정리합니다. API 응답 봉투와
오류 코드는 [API 응답 및 오류 코드 컨벤션](API_RESPONSE_CONVENTION.md)을 따릅니다.

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
| `region2Other` | boolean | `region1`이 선택된 경우, 공식 `region2` 목록 밖의 값과 `region2`가 비어 있는 Event를 포함합니다. `region1` 없이 사용하면 결과를 만들지 않습니다. |
| `region3` | 반복 가능한 문자열 목록 | 세부 지역을 같은 종류 안에서 OR로 필터링합니다. |
| `keyword` | 문자열 | 제목·부제목·설명에 대해 부분 일치 검색을 적용합니다. |
| `datePreset` | `ONGOING`, `OPENING_SOON`, `THIS_WEEKEND`, `THIS_MONTH` | DB의 현재 날짜를 기준으로 Event 기간을 필터링합니다. `startDate`·`endDate`와 함께 사용할 수 없습니다. |
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

Sector와 Activity는 `operational_v9`의 기준을 사용합니다. Sector는 `1~4`, Activity는
`1~56`이며 Event와 Place가 같은 분류표를 공유합니다.

### 필터 결합

- 같은 종류의 다중 값은 OR 조건으로 적용합니다.
- 지역, 검색어, 옵션처럼 서로 다른 종류의 필터는 AND 조건으로 적용합니다.
- Sector와 Activity는 예외로 **서로 OR**입니다. 둘을 함께 보내면 두 조건 중 하나라도
  맞는 항목을 반환하고, 이 한 묶음이 나머지 필터와 AND로 결합합니다. AND로 묶으면 한
  대분류를 통째로 고르고 다른 대분류의 소분류를 일부만 고른 조합에서 교집합이 비어
  결과가 0건이 됩니다.
- 여러 Activity에 연결된 Event도 목록에는 한 번만 반환합니다.
- `APPROVED`·`VISIBLE`이며 삭제되지 않은 Event 중 `end_date`가 없거나 애플리케이션이
  넘긴 기준일 이후인 데이터만 목록과 상세에 반환합니다. 종료일 당일은 공개합니다.
  기준일은 DB의 `CURRENT_DATE()`가 아닙니다 — 세션 시간대에 따라 DB가 다른 날을 보면
  공개 여부와 아래 `status` 파생이 서로 다른 날을 기준으로 갈립니다.
- 응답의 `status`는 **저장 컬럼이 아니라 같은 기준일로 계산한 값**입니다. 저장 컬럼은
  적재 파이프라인이 준 스냅샷이라 시간이 지나도 스스로 옮겨가지 않습니다.
- `savedOnly=true`는 인증한 회원이 찜한 항목만 남깁니다. 찜 등록·취소는
  [찜 등록·취소](#찜-등록취소)를 사용합니다.

### 날짜 프리셋

| 값 | 기준 |
| --- | --- |
| `ONGOING` | `start_date`가 오늘 이전 또는 오늘이며, 공통 공개 조건상 아직 종료되지 않은 Event |
| `OPENING_SOON` | `start_date`가 오늘 이후인 Event |
| `THIS_WEEKEND` | 다가오는 토요일·일요일과 기간이 겹치는 Event |
| `THIS_MONTH` | 현재 달과 기간이 겹치는 Event |

모든 프리셋에는 공통 공개 조건인
`end_date IS NULL OR end_date >= 기준일`이 함께 적용됩니다.

### status 판정

응답의 `status`는 요청 시점 기준일로 계산합니다.

| 조건 | 값 |
| --- | --- |
| 기준일 < `start_date` | `SCHEDULED` |
| `start_date` ≤ 기준일 ≤ `end_date` | `ONGOING` |
| `end_date` < 기준일 | `ENDED` |
| `end_date`가 `NULL`인 상시 Event | `start_date`를 지나면 계속 `ONGOING` |

`ENDED`는 위의 공개 조건이 먼저 걸러 내므로 실제 응답에는 나오지 않습니다. 값 집합은
유지합니다 — 공개 조건을 완화하는 날 판정을 다시 만들지 않기 위해서입니다.

저장 컬럼 `event.status`도 `EventStatusScheduler`가 1시간마다 같은 규칙으로 맞춥니다.
다만 **응답은 그 결과를 기다리지 않습니다.** 조회가 같은 식을 조회 시점에 다시 계산하므로
스케줄러가 늦거나 멈춰도, 적재가 방금 덮어썼어도 클라이언트가 받는 값은 정확합니다.
저장 컬럼을 직접 읽는 쪽만 최대 1시간까지 낡을 수 있습니다.

### 응답 및 페이지 표시

응답의 `data.content`가 현재 페이지에 포함된 목록이며 `totalElements`는 필터가 적용된
전체 검색 결과 수입니다. 프론트엔드는 Event와 Place 모두 `size=20`으로 요청하고,
`totalPages`를 사용해 번호형 페이지네이션을 표시합니다.

목록·상세 응답의 `saved`는 요청한 회원이 해당 Event를 찜했는지입니다. 비인증
요청에서는 항상 `false`입니다.

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
| `region2Other` | boolean | `region1`이 선택된 경우, 공식 `region2` 목록 밖의 값과 `region2`가 비어 있는 Place를 포함합니다. `region1` 없이 사용하면 결과를 만들지 않습니다. |
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
| `sort` | `LATEST` 또는 `POPULAR` | 최신순 또는 인기순으로 정렬합니다. |
| `language` | 문자열 | Activity·Sector 이름 언어입니다. `en`은 영문, 그 외에는 한글입니다. |
| `page` | 0 이상의 정수 | 0부터 시작하며 잘못된 음수는 0으로 보정합니다. |
| `size` | 양의 정수 | 기본값은 20, 최댓값은 100입니다. |

현재 Explore 화면은 서울 데이터만 노출합니다. 화면에는 locale별 번역값을 표시하지만
목록 API에는 `operational_v9`의 원본 값인 `region1=서울`과 선택한 한국어 `region2`를
전달합니다. 다른 `region1`과 사용자 위치 기반 `region3` UI는 현재 범위에 포함하지
않습니다.

`All of Seoul`은 `region2`와 `region2Other`를 모두 보내지 않는 전체 선택이고,
`Other areas`는 `region2Other=true`로 전달하며 operational_v9의 서울 세부지역 목록에
속하지 않는 값과 비어 있는 값을 포함합니다. Event와 Place가 같은 동작을 사용합니다.
지역 라벨은 Vue i18n으로 표시합니다.

### 필터 및 데이터 공개 규칙

- 같은 종류의 다중 값은 OR, 서로 다른 종류의 필터는 AND로 결합합니다.
- Event 목록과 같이 **Sector와 Activity만 서로 OR**입니다(위 [필터 결합](#필터-결합)).
- 여러 Activity에 연결된 Place도 목록에는 한 번만 반환합니다.
- `APPROVED`·`VISIBLE`이며 삭제되지 않은 활성 Place만 목록과 상세에 반환합니다.
- 저장 여부는 `explore_item_likes`의 삭제되지 않은 데이터로 판단합니다.
- DB의 세부 Place 유형은 API에서 `RESTAURANT`, `CAFE`, `MARKET`, `BEAUTY`,
  `ETC`로 정규화합니다. 알 수 없는 유형은 `ETC`로 반환합니다.

운영시간과 휴무일 원문은 상세 응답에서 그대로 반환합니다. 현재 목록 API에는
운영시간 문자열을 애플리케이션에서 재해석하는 `openNow` 필터를 포함하지 않습니다.
크롤러의 구조화된 운영시간 데이터와 DB 필터 기준이 확정된 뒤 별도 작업으로 추가합니다.

### 응답

목록 응답은 `data.content`, `page`, `size`, `totalElements`, `totalPages`,
`hasNext`를 반환합니다. 상세 응답은 기본 정보와 주소·좌표, 운영시간 원문, 편의 옵션,
조회·저장 수, 연결된 Activity와 Sector를 반환합니다.

목록·상세 응답의 `saved`는 요청한 회원이 해당 Place를 찜했는지입니다. 비인증
요청에서는 항상 `false`입니다.

## Place 상세

`GET /api/v1/explore/places/{placeId}?language=en`

공개·활성 조건을 만족하는 Place만 조회합니다. 없거나 비공개·비활성·삭제 상태이면
HTTP 404와 `EXPLORE-002`를 반환합니다.

## 상세 조회수

`GET /api/v1/explore/events/{eventId}` ·
`GET /api/v1/explore/places/{placeId}`

두 상세 조회는 `countView` 파라미터를 받습니다. **기본값은 `false`이고, `true`일 때만
`view_count`를 1 올립니다.** 회원과 중복을 가리지 않으므로 같은 사람이 여러 번 열면
그만큼 올라갑니다.

이 파라미터가 필요한 이유는 상세 API를 상세 화면만 부르지 않기 때문입니다. 약속 생성
화면도 "이 항목 자리에서 만난다"를 그리려고 같은 API로 위치를 읽습니다. 서버가 요청을
가리지 않고 세면 약속을 만들 때마다 조회수가 오릅니다. **값만 읽어 가는 호출부는 이
파라미터를 붙이지 않습니다.**

기본값을 `false`로 둔 것은 새 호출부가 모르고 조회수를 부풀리는 쪽보다, 모르고 세지
않는 쪽이 되돌리기 쉽기 때문입니다.

조회수 집계가 실패해도 상세 응답은 그대로 반환합니다. 집계는 별도 트랜잭션에서
처리하고 실패는 서버 로그에만 남깁니다.

`sort=POPULAR`는 이 `view_count`를 기준으로 정렬합니다.

## 찜 등록·취소

`POST /api/v1/explore/items/{itemId}/like` ·
`DELETE /api/v1/explore/items/{itemId}/like`

Event·Place 공통으로 `explore_items`의 `item_id`를 사용합니다(Event·Place 응답의
`itemId`와 같은 값입니다). 두 요청 모두 멱등입니다 — 이미 찜한 항목의 재등록,
찜하지 않은 항목의 취소는 아무것도 바꾸지 않고 같은 응답을 반환합니다. 단,
등록의 멱등은 항목이 아래 등록 조건을 계속 만족할 때만 성립합니다. 찜한 뒤
`HIDDEN`·미승인·삭제로 바뀐 항목의 재등록은 HTTP 404와 `EXPLORE-003`입니다 —
등록 조건을 기존 찜 여부보다 먼저 확인하기 때문입니다.

```json
{
  "success": true,
  "data": { "saved": true }
}
```

- 등록은 `explore_items` 행의 노출 조건(`APPROVED`·`VISIBLE`·
  `explore_items.deleted_at` 미설정)만 확인합니다. Event 종료(`end_date`), Place
  비활성(`is_active = FALSE`), 그리고 `event`·`place` 행 자체의 `deleted_at`은
  검사하지 않습니다. 목록·상세 쿼리는 이 셋을 모두 확인합니다. 조건을 만족하지
  않으면 HTTP 404와 `EXPLORE-003`을 반환합니다.
- 그래서 목록·상세에서 빠진 항목도 등록됩니다. 이렇게 등록된 찜은 목록 API의
  `savedOnly=true`에 나오지 않습니다.
- 취소는 노출이 꺼진(`HIDDEN`) 항목에도 허용합니다 — 회원이 자기 찜 목록을 정리할
  수 있어야 하므로 노출 조건 대신 삭제되지 않았는지만 확인합니다. 삭제된 항목의
  취소는 등록과 같이 HTTP 404와 `EXPLORE-003`을 반환합니다.
- 찜 상태가 실제로 바뀔 때만 해당 Event·Place의 `favorite_count`를 같은
  트랜잭션에서 1 증감합니다.
- 내 찜 목록은 목록 API의 `savedOnly=true`로 조회합니다. 노출 조건을 만족하는
  항목만 나오므로, 등록 뒤 조건을 잃은 찜은 여기에 보이지 않습니다. 개별 항목의
  찜 여부는 목록·상세 응답의 `saved` 필드로 확인합니다.
