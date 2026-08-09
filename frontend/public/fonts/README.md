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

Display 폰트는 `Sztos`이며 **현재 어디에서도 로드되지 않는다.** 재배포 권한이
확인되지 않아 셀프 호스팅 `woff2` 두 개(`Sztos-BoldCondensed`,
`Sztos-ExtraBoldCondensed`)와 `index.css`의 `@font-face` 두 블록을 제거했다(#122).

`tokens.css`의 `--font-display`에는 `Sztos` 이름을 그대로 두었다. 디자인 의도가 바뀐
것이 아니라 전달 방법만 막힌 것이기 때문이다. 지금은 `Noto Sans`로 폴백한다.

### 어디에 영향이 있는가

`--font-display`는 Tailwind의 `font-display` 클래스로 쓰인다. 사용처는 이 명령으로
확인한다. 숫자를 적어 두면 화면이 늘 때마다 낡는다.

```shell
grep -rn 'font-display' src --include='*.vue'
```

2026-08-09 기준 14개 파일 21곳이며, 각 화면의 제목(`text-screen-title`)과 섹션
헤더(`text-section-header`), 그리고 온보딩 `WelcomeView`의 52px 헤드라인이 여기
해당한다. **워드마크와 티켓 스탬프도 같은 토큰을 쓴다.**

### 되돌리는 방법

CDN 구독으로 웹폰트 사용권을 확보한 뒤 **로더만 추가한다.**
`tokens.css`와 컴포넌트는 손대지 않아도 된다.

**셀프 호스팅 `woff2`를 다시 추가하지 않는다.**

## 아직 없는 폰트

- **CJK Body**: `ja`, `zh-CN`, `zh-TW`용 Noto Sans JP/SC/TC. 원본 합계가 37MB라
  서브셋 후 로케일별 동적 로드로 추가한다. 전체 선로드는 하지 않는다.
- **CJK Display**: Smiley Sans(`zh-CN`), Taipei Sans TC Beta(`zh-TW`),
  M PLUS 1(`ja`). 디자인 번들에 없다. 확보 전까지 Body 폰트로 폴백한다.
- **로고**: `Ria Sans`는 런타임 폰트로 배포하지 않는다. 벡터 로고 자산이 확보될
  때까지 워드마크는 `--font-display`를 쓰며, Sztos가 로드되지 않는 현재는 `Noto Sans`로
  폴백한다.

## Service Worker

`vite.config.ts`의 `workbox.globPatterns`가 `js`, `css`, `html`만 포함하므로 woff2는
사전 캐시되지 않는다. 폰트를 precache 대상에 추가하지 않는다.

## 라이선스

- **Noto Sans**: SIL Open Font License 1.1. 전문은 `NotoSans-OFL.txt`.
- **Sztos**: 디자인 번들에 라이선스 파일이 포함되어 있지 않아 재배포 권한을 확인할 수
  없다. 셀프 호스팅 파일을 제거했다(#122). 이 저장소는 공개이므로 `public/` 아래에 둔
  폰트는 빌드 산출물과 배포본에서 누구나 내려받을 수 있다. **권한이 확인되지 않은
  서체 파일을 여기에 두지 않는다.**
