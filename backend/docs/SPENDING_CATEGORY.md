# 소비 카테고리

이 문서를 읽으면 `wallet_transfers.spending_category`에 어떤 값을 넣을 수 있는지, 그 값이
어디서 들어와 어느 화면에 보이는지 알 수 있다. 값을 늘리거나 줄일 때도 이 문서를 먼저
고친다.

## 값 집합

일곱 개다. 이 목록 밖의 값은 저장하지 않는다.

| 값 | 뜻 | 리포트 칭호 |
| --- | --- | --- |
| `FOOD` | 식당·카페·주점 | Flavor Seeker |
| `SHOPPING` | 상품 구매·기념품 | Souvenir Hunter |
| `BEAUTY` | 미용·뷰티 시술 | Glow Getter |
| `SHOW` | 전시·공연·체험 | Front Row Traveler |
| `TRANSPORT` | 이동 수단 | Ground Coverer |
| `STAY` | 숙박 | Slow Traveler |
| `OTHER` | 위에 들어가지 않는 소비 | Free Spender |

앞의 네 값은 프론트엔드 Explore의 소비영역(`beauty`·`shopping`·`show`·`food`)과 같은
어휘를 쓴다. 사용자가 두 화면에서 같은 말을 보게 하려는 것이고, **타입은 공유하지
않는다.** Explore는 탐색 아이템의 분류고 이쪽은 결제 건의 분류라, 한쪽을 늘리는 결정이
다른 쪽을 끌고 가면 안 된다.

## 누가 값을 넣는가

**QR 결제를 실행하는 결제자가 결제 직전에 고른다.** `POST /api/v1/wallet/qr/payment/execute`
요청의 `spendingCategory`로 보낸다. 자세한 계약은 [QR 결제 API](./QR_PAYMENT_API.md)에 있다.

가맹점이 QR을 만들 때 정하지 않는다. 가맹점 데이터에 업종 컬럼이 없고, 리포트가 쓰는 것도
가맹점 분류가 아니라 결제자의 소비 성향이기 때문이다.

카테고리를 고르지 않은 결제도 받는다. 요청에서 값을 빼거나 `null`을 보내면 서버가 `OTHER`로
저장한다. 리포트 집계 쿼리가 이미 `COALESCE(NULLIF(spending_category, ''), 'OTHER')`로 같은
접기를 하므로, 저장 시점에 접어 두면 두 곳의 결과가 어긋나지 않는다.

## 서버가 거부하는 값

`SpendingCategory.from`이 유일한 allow-list다. `spending_category` 컬럼은
`VARCHAR(20) NULL` 자유 문자열이라 DB가 값을 막아주지 않는다.

- 앞뒤 공백을 자르고 대문자로 맞춘 뒤 목록과 비교한다. `food`와 `  FOOD  `는 통과한다.
- 목록에 없으면 `WALLET-031`(400)로 거절한다. **오타를 `OTHER`로 삼키지 않는다.** 기타
  소비로 조용히 접으면 리포트 칭호가 틀린 근거로 만들어진다.
- 같은 `Idempotency-Key`로 다른 카테고리를 보내면 `WALLET-009`(멱등 키 충돌)로 거절한다.
  금액·소비 범위와 같은 규칙이다.

## 어느 화면에 보이는가

세 곳이다.

| 화면 | 쓰는 방식 |
| --- | --- |
| QR 결제 실행 | 칩 일곱 개 중 하나를 고른다. 기본 선택은 `OTHER`다 |
| 거래 상세 | 저장된 값을 번역해 보여준다 |
| 리포트 상세 | 카테고리별 금액·비중을 그리고, 1위 카테고리로 칭호를 준다 |

## 칭호를 정하는 규칙

리포트 응답의 `analytics.categoryBreakdown` **첫 항목**이 칭호를 정한다. 백엔드가 금액
내림차순, 같으면 카테고리 이름 오름차순으로 정렬해 내려주므로 같은 리포트에서는 항상 같은
칭호가 나온다.

칭호 문구는 백엔드가 만들지 않는다. **프론트엔드 `features/report/i18n`이 소유한다.**
그래서 리포트 스냅샷에 `locale`을 실어 보낼 필요가 없고, 이미 만들어진 리포트도 사용자가
언어를 바꾸면 그 언어로 보인다.

지출이 없는 리포트에는 칭호를 주지 않는다. `categoryBreakdown`이 비어 있으면 칭호 블록을
그리지 않는다.

## 범위 밖

- **정산(`SETTLEMENT`) 거래에는 카테고리를 받지 않는다.** 리포트가 정산도 지출 후보로
  세지만(`ReportMapper.xml`의 `transfer_type IN ('QR_PAYMENT', 'SETTLEMENT')`), 정산은
  여러 사람이 나눠 낸 몫이라 결제자 한 명의 소비 성향으로 보기 어렵다. 지금은 전부
  `OTHER`로 집계된다.
- **충전(`TOPUP`)은 지출이 아니다.** 리포트 집계에서 이미 빠진다.
- 같은 국적·연령대 사용자와의 비교. `members.nationality_code`가 채워진 회원이 없고 연령
  컬럼은 아예 없다.
