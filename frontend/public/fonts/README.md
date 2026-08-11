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
비밀값이 아니다.** 공개 페이지의 `<link>`에 그대로 노출되며 Adobe Fonts 웹 프로젝트는
도메인 허용 목록을 사용하지 않는다.

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

**기본 폭 `wdth 100`은 Condensed 컷이다. 시안 폭은 `wdth 200`이다.** Adobe가 100을
`Normal`, 200을 `Expanded`라 부르지만 이름과 실제가 어긋난다. 시안 텍스트 폭과 비교하면
분명하다.

| 요소                        | 시안  | `wdth 100` | `wdth 200` |
| --------------------------- | ----- | ---------- | ---------- |
| `Your trip, on record` 40px | 297px | 138.4px    | 295.7px    |
| `Journeys` 34px             | 256px | 143.5px    | 259.3px    |

그래서 `index.css`의 `.font-display`가 `font-stretch: 200%`와
`font-variation-settings: 'wdth' 200`으로 폭을 고정한다. Adobe kit CSS가
`@font-face`의 stretch 범위를 `normal`로만 선언해 표준 속성만으로는 Chromium에서만
축이 움직이므로, Firefox·WebKit까지 같은 결과를 내기 위해 `wdth`를 직접 지정한다.
두 속성은 상속되며 Body 폰트 `Noto Sans`의 `wdth` 상한은 100이라 기본 폭으로 제한된다.

### Display 굵기 상한은 700이다

**시안의 `Sztos ExtraBold`(800)는 쓸 수 없다.** Adobe Fonts가 싣고 있는 패밀리는
`Sztos Variable` 하나뿐이고 `wght` 축이 700에서 끝난다. 정적 `Sztos` 패밀리는
Adobe Fonts에 존재하지 않는다(`fonts.adobe.com/fonts/sztos`는 404). 시안의 ExtraBold은
디자이너 데스크톱 폰트에서 온 것이며, 그 파일은 라이선스상 셀프 호스팅할 수 없다.

800을 적어도 브라우저가 700으로 자른다. Chromium에서 700과 800의 렌더 폭이 294.61px로
같아 합성 볼드는 얹히지 않았다(2026-08-11 실측). 다른 엔진은 확인하지 않았다.

**그래서 Display 토큰의 굵기 상한을 700으로 확정했다.** 렌더되지 않는 값을 토큰에
남기지 않는다. 시안이 요구하는 ExtraLight(200)와 Bold(700)는 모두 정상 동작한다.

이 상한은 `--font-display`를 쓰는 곳에만 적용된다. Body 폰트 `Noto Sans`는
`wght 100–900`이라 지갑 금액처럼 `font-extrabold`(800)를 쓰는 곳은 영향이 없다.

800을 되살리려면 제작사 [Capitalics](https://capitalics.wtf)에서 웹폰트 라이선스를
직접 구매해야 한다. Adobe Fonts kit에 웨이트를 추가하는 방법으로는 해결되지 않는다.

### 어디에 영향이 있는가

`--font-display`는 Tailwind의 `font-display` 클래스로 쓰인다. 사용처는 이 명령으로
확인한다. 숫자를 적어 두면 화면이 늘 때마다 낡는다.

```shell
grep -rn 'font-display' src --include='*.vue'
```

2026-08-11 기준 17개 파일 23곳이며, 각 화면의 제목(`text-screen-title`)과 섹션
헤더(`text-section-header`), 그리고 온보딩 `WelcomeView`의 40px 헤드라인
(`--text-welcome-headline`)이 여기 해당한다. **티켓 스탬프도 같은 토큰을 쓴다.**

### 운영 소유권

Adobe Fonts 웹 프로젝트는 도메인 허용 목록을 사용하지 않아 같은 embed code를 어느
도메인에서든 불러올 수 있다. 대신 kit을 소유한 계정의 Creative Cloud 구독이 끝나면
웹폰트 제공도 중단되고 `Noto Sans`로 폴백한다. 현재는 개인 계정 소유 kit을 운영
의존성으로 사용하는 결정을 수용한다. 계정 이전·구독 종료 시에는 새 kit의 embed code로
교체하며, 계정 이메일이나 결제 정보는 공개 저장소에 기록하지 않는다.

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
  [Adobe Fonts 웹폰트 라이선스](https://helpx.adobe.com/fonts/using/webfont-licensing.html)를
  따른다.
  이 저장소는 공개이므로 `public/` 아래에 둔 폰트는 빌드 산출물과 배포본에서 누구나
  내려받을 수 있다. **권한이 확인되지 않은 서체 파일을 여기에 두지 않는다.**
