# 탐색 API 계약

탐색 목록 API의 요청 파라미터와 필터 결합 규칙을 정리합니다. API 응답 봉투와
오류 코드는 [API 응답 및 오류 코드 컨벤션](API_RESPONSE_CONVENTION.md)을 따릅니다.

## Event 목록

`GET /api/v1/explore/events`

`/api/**` 경로는 인증이 필요합니다. Swagger에서 호출할 때는 먼저 `Authorize`로
유효한 Bearer 토큰을 등록해야 합니다.

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
