# 폰트 자산

`src/app/styles/index.css`의 `@font-face`가 참조하는 런타임 폰트다.

## 수록 파일

| 파일             | 역할                   | 원본                 | 크기  |
| ---------------- | ---------------------- | -------------------- | ----- |
| `NotoSans.woff2` | Body / UI (`en`, `vi`) | `NotoSans.ttf` 2.0MB | 201KB |

Display 폰트는 여기에 없다. 아래 「Display 폰트」 절을 참조한다.

`NotoSans.woff2`는 가변 폰트이며 `wght 100–900`, `wdth 62.5–100` 축을 유지한다.

## 서브셋 범위

Latin, Latin Extended와 베트남어를 포함한다.

```text
U+0000-00FF, U+0100-024F, U+0259, U+1E00-1EFF,
U+2000-206F, U+20A0-20CF, U+2113, U+2122,
U+2190-2193, U+2212, U+FEFF, U+FFFD
```

`fontTools`의 `pyftsubset`으로 변환했다.

```shell
pyftsubset <원본> \
  --output-file=<대상>.woff2 \
  --flavor=woff2 \
  --layout-features='*' \
  --unicodes="<위 범위>" \
  --no-hinting --desubroutinize
```

## Display 폰트

Display 폰트는 `Sztos`이며 **Adobe Fonts(Typekit)에서 받는다.** 재배포 권한이 확인되지
않아 셀프 호스팅 `woff2` 두 개(`Sztos-BoldCondensed`, `Sztos-ExtraBoldCondensed`)와
`index.css`의 `@font-face` 두 블록을 제거했고(#122), 그 자리를 CDN 로더가 대신한다.

로더는 `index.html`의 `<link rel="stylesheet">`이며 kit ID는 `qgv6efy`다. **kit ID는
비밀값이 아니다.** 공개 페이지의 `<link>`에 그대로 노출되는 값이고, 접근 제어는 Adobe
쪽 도메인 허용 목록이 한다.

**셀프 호스팅 `woff2`를 다시 추가하지 않는다.**

### 패밀리 이름은 `sztos-variable`이다

Adobe가 kit CSS에 선언하는 `font-family`는 `Sztos`가 아니라 **`sztos-variable`**이다.
`tokens.css`의 `--font-display`도 그 이름을 쓴다. `Sztos`라고 적으면 매칭되지 않고
조용히 `Noto Sans`로 폴백하므로, 화면이 폴백처럼 보이면 여기부터 확인한다.

### kit이 싣고 있는 축

kit `qgv6efy`는 가변 폰트 하나(`sztos-variable`)를 로마자·이탤릭 두 벌로 싣는다.

| 축     | 범위    | 기본값 | 명명 인스턴스               |
| ------ | ------- | ------ | --------------------------- |
| `wght` | 100–700 | 400    | Thin … Bold                 |
| `wdth` | 100–200 | 100    | Normal(100) · Expanded(200) |

**축 이름에 `Condensed`는 없지만 기본 폭이 시안의 좁은 비율이다.** `wdth 100`에서
`WALLET`(100px)의 폭이 294.61px, `wdth 200`에서 567px로 거의 두 배가 된다. 즉 Adobe가
`Normal`이라 부르는 100이 제거된 `BoldCondensed`·`ExtraBoldCondensed`의 자리를
대신하고, 200은 그보다 훨씬 넓은 별도 폭이다. **`font-variation-settings`로 `wdth`를
건드리지 않는다.** 기본값이 곧 시안이다.

`wght` 상한은 700이라 800을 요청하는 곳(`--text-screen-title--font-weight`,
`WelcomeView`의 `font-extrabold`)은 700으로 잘린다. Chromium에서 700과 800의 렌더 폭이
294.61px로 같아 **합성 볼드는 얹히지 않는다**(2026-08-11 실측). 다른 엔진은 확인하지
않았다.

### 어디에 영향이 있는가

`--font-display`는 Tailwind의 `font-display` 클래스로 쓰인다. 사용처는 이 명령으로
확인한다. 숫자를 적어 두면 화면이 늘 때마다 낡는다.

```shell
grep -rn 'font-display' src --include='*.vue'
```

2026-08-11 기준 17개 파일 23곳이며, 각 화면의 제목(`text-screen-title`)과 섹션
헤더(`text-section-header`), 그리고 온보딩 `WelcomeView`의 40px 헤드라인
(`--text-welcome-headline`)이 여기 해당한다. **티켓 스탬프도 같은 토큰을 쓴다.**

### 도메인 허용 목록

Adobe Fonts kit는 등록된 도메인에서만 로드된다. `localhost`, 운영 도메인, 그리고
**배포마다 URL이 바뀌는 프리뷰 도메인**이 등록돼 있어야 한다. 프리뷰가 빠지면 PR
프리뷰와 스냅샷 검증에서만 폴백으로 보이고 운영에서는 정상이라, 원인을 찾기 어렵다.

## 로고

온보딩의 `NAWA` 워드마크는 `shared/ui/BrandWordmark.vue`의 SVG 패스다. Ria Sans
Regular로 조판한 네 글자만 고정했으며, 런타임 폰트 파일은 배포하지 않는다. 따라서
Sztos 로드 여부와 관계없이 워드마크 모양이 유지되고 추가 네트워크 요청이나 FOUT이
생기지 않는다.

## 아직 없는 폰트

- **CJK Body**: `ja`, `zh-CN`, `zh-TW`용 Noto Sans JP/SC/TC. 원본 합계가 37MB라
  서브셋 후 로케일별 동적 로드로 추가한다. 전체 선로드는 하지 않는다.
- **CJK Display**: Smiley Sans(`zh-CN`), Taipei Sans TC Beta(`zh-TW`),
  M PLUS 1(`ja`). 디자인 번들에 없다. 확보 전까지 Body 폰트로 폴백한다.

## Service Worker

`vite.config.ts`의 `workbox.globPatterns`가 `js`, `css`, `html`만 포함하므로 woff2는
사전 캐시되지 않는다. 폰트를 precache 대상에 추가하지 않는다.

## 라이선스

- **Noto Sans**: SIL Open Font License 1.1. 전문은 `NotoSans-OFL.txt`.
- **Ria Sans**: 온보딩 워드마크 네 글자만 SVG 패스로 포함한다. 원본 폰트 파일은
  저장소와 빌드 산출물에 넣지 않는다.
- **Sztos**: 디자인 번들에 라이선스 파일이 없어 재배포 권한을 확인할 수 없었다. 셀프
  호스팅 파일을 제거하고(#122) Adobe Fonts 웹폰트 사용권으로 대체했다. 사용 조건은
  [Typekit 이용약관](http://www.adobe.com/products/eulas/tou_typekit)을 따른다.
  이 저장소는 공개이므로 `public/` 아래에 둔 폰트는 빌드 산출물과 배포본에서 누구나
  내려받을 수 있다. **권한이 확인되지 않은 서체 파일을 여기에 두지 않는다.**
