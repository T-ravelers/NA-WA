# 폰트 자산

`src/app/styles/index.css`의 `@font-face`가 참조하는 런타임 폰트다.

## 수록 파일

| 파일                             | 역할                   | 원본                                | 크기  |
| -------------------------------- | ---------------------- | ----------------------------------- | ----- |
| `NotoSans.woff2`                 | Body / UI (`en`, `vi`) | `NotoSans.ttf` 2.0MB                | 201KB |
| `Sztos-BoldCondensed.woff2`      | Display 700            | `Sztos-BoldCondensed.otf` 66KB      | 20KB  |
| `Sztos-ExtraBoldCondensed.woff2` | Display 800            | `Sztos-ExtraBoldCondensed.otf` 66KB | 20KB  |

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

## 아직 없는 폰트

- **CJK Body**: `ja`, `zh-CN`, `zh-TW`용 Noto Sans JP/SC/TC. 원본 합계가 37MB라
  서브셋 후 로케일별 동적 로드로 추가한다. 전체 선로드는 하지 않는다.
- **CJK Display**: Smiley Sans(`zh-CN`), Taipei Sans TC Beta(`zh-TW`),
  M PLUS 1(`ja`). 디자인 번들에 없다. 확보 전까지 Body 폰트로 폴백한다.
- **로고**: `Ria Sans`는 런타임 폰트로 배포하지 않는다. 벡터 로고 자산이 확보될
  때까지 워드마크는 Sztos로 폴백한다.

## Service Worker

`vite.config.ts`의 `workbox.globPatterns`가 `js`, `css`, `html`만 포함하므로 woff2는
사전 캐시되지 않는다. 폰트를 precache 대상에 추가하지 않는다.

## 라이선스

- **Noto Sans**: SIL Open Font License 1.1. 전문은 `NotoSans-OFL.txt`.
- **Sztos**: 디자인 번들에 라이선스 파일이 포함되어 있지 않다. 배포 전 원저작자의
  라이선스를 확인하고 이 문서에 근거를 추가해야 한다.
