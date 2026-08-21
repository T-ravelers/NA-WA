# shared/ui — 공용 컴포넌트

도메인 화면을 만들기 전에 이 목록을 먼저 본다. **여기 있는 것을 도메인 안에서 다시 만들지 않는다.**

네 명이 각자 도메인을 병렬로 작업하므로, 버튼과 카드를 각자 만들면 통합 시점에 화면마다
높이·라운드·눌림 반응이 달라진다. 필요한 변형이 있으면 `features/` 안에서 해결하지 말고
이 폴더의 컴포넌트에 prop을 추가한다.

## 목록

| 컴포넌트                                     | 용도                                                      |
| -------------------------------------------- | --------------------------------------------------------- |
| `AppButton`                                  | primary / secondary / tertiary / settle. 로딩·비활성 포함 |
| `AppCard`                                    | `surface-1` r20 면. 절취선 없는 카드                      |
| `AppTicket`                                  | **시그니처.** 노치 + 퍼포레이션 티켓                      |
| `TicketStamp`                                | 티켓 우하단 원형 도장                                     |
| `BrandWordmark`                              | `NAWA` 워드마크. Ria Sans 조판을 고정한 벡터 패스         |
| `CategoryChip`                               | 소비영역 칩. 필터 토글 / 티켓 안 정적 라벨 겸용           |
| `CategoryDot`                                | 소비영역 8px 점. 타임라인·범례                            |
| `AppBadge`                                   | 진행중·예정·정산·동행 등 짧은 상태 표식                   |
| `TextInput`                                  | 라벨·도움말·오류가 묶인 텍스트·날짜 입력                  |
| `AmountInput`                                | 통화 접두·단위 접미 + 우측 정렬 금액 입력                 |
| `SegmentedControl`                           | `Ongoing \| Past` 형태의 배타 선택                        |
| `IconOrb`                                    | 44×44 기본·48×48 헤더용 원형 아이콘 버튼                  |
| `GaugeBar`                                   | 예산·진행률 막대                                          |
| `ImagePlaceholder`                           | 이미지 결측 대체 면                                       |
| `BottomNav`                                  | 하단 탭. 2번째 탭은 Report(`/reports`)로 이동             |
| `StateEmpty` / `StateError` / `StateLoading` | 빈·오류·로딩 상태                                         |
| `LocaleSheet`                                | 로케일 선택 시트                                          |
| `CalendarGrid`                               | 달 하나짜리 달력. 하루 선택·기간 선택 겸용                |

## 지켜야 할 것

1. **색·크기·간격·라운드·그림자는 `app/styles/tokens.css`의 토큰만 쓴다.** 컴포넌트에 HEX를
   직접 쓰지 않는다. 디자인이 바뀌면 토큰 값만 교체하고 화면은 건드리지 않는다.
2. **`DESIGN_v4.md`의 HEX 값을 그대로 옮겨 쓰지 않는다.** 그 문서는 2026-08-06 시안 반영
   이전 값이라 `tokens.css`와 다르다. 값의 정본은 항상 `tokens.css`다.
3. **색으로만 정보를 말하지 않는다.** 소비영역·상태는 반드시 텍스트 라벨과 함께 쓴다.
   `CategoryDot`과 상태 점은 접근성 트리에서 감춰져 있어 그것만으로는 읽히지 않는다.
4. **문구는 `features/<domain>/i18n/<locale>.ts`에 넣는다.** 공용 파일을 고치지 않는다.
   `en`이 원본이자 폴백이다. 한국어는 서비스 로케일이 아니다.
5. 화면 좌우 여백 20px(`px-screen`), 터치 타깃 44px 이상, 주 CTA 52px.
6. 컴포넌트가 없어서 막히면 만들지 말고 프론트엔드 리드에게 말한다.

## 밝은 면 위 텍스트 토큰

`on-*`은 "무엇 위에 놓이는 글자인가"를 뜻한다. 둘 다 검정에 가까워 잘못 써도 눈으로는
잡히지 않으니, 깔린 면의 종류를 보고 고른다.

- `on-paper` (#101828) — `paper` / `paper-fill` 면 위 (종이톤 티켓 스텁, primary 버튼)
- `on-category` (#0e0e0c) — 소비영역 코어색 면 위 (채워진 칩, 카테고리 티켓)

## `AppTicket` 쓰는 법

티켓은 `body`와 `stub` 두 슬롯을 절취선으로 나눈 조형이다. `bodySize`는 분할 위치이며,
세로형은 body의 높이(px), 가로형은 body의 폭(px)이다.

```vue
<!-- 여정 티켓: 상단 이미지 154px + 하단 종이 스텁 -->
<AppTicket :body-size="154" tone="paper">
  <template #body>
    <img class="size-full object-cover" :src="journey.coverUrl" alt="" />
  </template>
  <template #stub>
    <div class="p-4">
      <h3 class="font-display text-trip-ticket-title">{{ journey.name }}</h3>
      <CategoryChip v-for="c in journey.categories" :key="c" :category="c" :label="t(`category.${c}`)" size="sm" />
    </div>
  </template>
</AppTicket>

<!-- 카테고리 티켓: 좌측 포토슬롯 88px + 우측 본문, 노치 ø12 -->
<AppTicket orientation="horizontal" :body-size="88" :notch-size="12" tone="food">
  <template #body><ImagePlaceholder /></template>
  <template #stub>…</template>
</AppTicket>
```

노치는 배경색 원을 덧대는 것이 아니라 `mask`로 실제로 파낸다. 어떤 면 위에 놓아도 어긋나지
않는다. 대신 **티켓 바깥을 `<button>`으로 감싸지 않는다** — 티켓 안에 칩과 링크가 들어가
중첩 인터랙티브가 되면 스크린 리더에서 읽히지 않는다. 탭 동작은 `stub` 안에 둔다.

## 아직 없는 것

- 날짜 선택(`date-picker-day`), 단계 표시(`stepper-indicator`) — 여정 생성 담당이 필요해지는
  시점에 여기에 추가한다.
- 리포트 차트(레이더·도넛·막대) — 리포트 담당과 협의 후 결정.
- CJK Display 폰트 3종과 로고용 Ria Sans는 번들에 없어 Body 폰트로 폴백한다.
