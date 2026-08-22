#!/usr/bin/env python3
"""
CJK 폰트를 unicode-range 슬라이스 woff2로 나누고 `@font-face` CSS를 만든다.

본문용 Noto Sans JP·TC(가변, `wght 100–900`)와 디스플레이용 M PLUS 1p(ja)·Taipei Sans TC Beta(zh-TW)
(정적, Light 300 + Bold 700)를 한 번에 만든다. 실행(저장소 frontend 디렉터리에서):

    uvx --from 'fonttools[woff]' python scripts/subset-cjk-fonts.py \
        --jp <NotoSansJP-VariableFont_wght.ttf> --tc <NotoSansTC-VariableFont_wght.ttf> \
        --mplus-light <MPLUS1p-Light.ttf> --mplus-bold <MPLUS1p-Bold.ttf> \
        --taipei-light <TaipeiSansTCBeta-Light.ttf> --taipei-bold <TaipeiSansTCBeta-Bold.ttf>
    pnpm --filter @na-wa/frontend exec prettier --write src/app/styles/fonts-cjk.css

산출물:
- public/fonts/noto-sans-jp/NotoSansJP.<n>.woff2, public/fonts/noto-sans-tc/NotoSansTC.<n>.woff2
- public/fonts/m-plus-1p/MPLUS1p-{Light,Bold}.<n>.woff2, public/fonts/taipei-sans-tc/TaipeiSansTC-{Light,Bold}.<n>.woff2
- src/app/styles/fonts-cjk.css (슬라이스마다 `@font-face` 한 블록)

슬라이스 범위는 scripts/cjk-unicode-ranges.json에 있다. Google Fonts가 각 패밀리에 쓰는 분할을
그대로 가져온 것이라, 화면에 실제로 나온 글자가 속한 슬라이스만 내려받게 된다(결정 6 —
DB 유래 문자열이 있어 정적 글리프 서브셋은 쓸 수 없다). Taipei Sans TC Beta는 Google Fonts에
없어 Noto Sans TC의 분할을 같이 쓴다.

`--only N`으로 패밀리당 슬라이스 N개만 만들어 빠르게 확인할 수 있다.
"""

from __future__ import annotations

import argparse
import json
import time
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont

FRONTEND = Path(__file__).resolve().parent.parent
RANGES = FRONTEND / 'scripts' / 'cjk-unicode-ranges.json'
CSS_OUT = FRONTEND / 'src' / 'app' / 'styles' / 'fonts-cjk.css'

# weights가 None이면 가변 폰트(파일 하나, `font-weight: 100 900`), 있으면 굵기별 정적 파일이다.
FAMILIES = {
    'jp': {'family': 'Noto Sans JP', 'dir': 'noto-sans-jp', 'stem': 'NotoSansJP', 'ranges': 'jp'},
    'tc': {'family': 'Noto Sans TC', 'dir': 'noto-sans-tc', 'stem': 'NotoSansTC', 'ranges': 'tc'},
    'mplus': {'family': 'M PLUS 1p', 'dir': 'm-plus-1p', 'stem': 'MPLUS1p', 'ranges': 'mplus'},
    'taipei': {'family': 'Taipei Sans TC Beta', 'dir': 'taipei-sans-tc', 'stem': 'TaipeiSansTC', 'ranges': 'tc'},
}

# 디스플레이 토큰이 쓰는 굵기는 700(제목·섹션·티켓)과 200·300(웰컴 헤드라인·라벨)이다. Light와
# Bold 두 벌이면 전부 덮인다. 500(카테고리 티켓 제목)은 CSS 매칭상 Light로 떨어진다.
STATIC_WEIGHTS = {'Light': 300, 'Bold': 700}

CSS_HEADER = """/**
 * CJK 본문·디스플레이 폰트 `@font-face`. **생성 파일이다 — 직접 고치지 않는다.**
 *
 * `scripts/subset-cjk-fonts.py`가 `scripts/cjk-unicode-ranges.json`의 슬라이스 범위로 만든다.
 * 슬라이스 범위는 Google Fonts가 각 패밀리에 쓰는 분할을 그대로 가져왔다. 브라우저는
 * 화면에 실제로 나온 글자가 속한 슬라이스만 내려받는다.
 *
 * - 본문: Noto Sans JP·TC (가변, wght 100–900)
 * - 디스플레이: M PLUS 1p (ja)·Taipei Sans TC Beta (zh-TW), Light 300 + Bold 700
 *
 * 폴백 체인 편입은 `tokens.css`의 `--font-body`·`--font-display`, 로케일별 우선순위는
 * `index.css`의 `:lang()` 규칙을 본다. Service Worker precache에는 넣지 않는다.
 */
"""


def make_options() -> subset.Options:
    options = subset.Options()
    options.flavor = 'woff2'
    options.layout_features = ['*']
    options.hinting = False
    options.desubroutinize = True
    # 세로쓰기를 쓰지 않는다. 세로 메트릭 테이블을 빼서 슬라이스를 줄인다.
    options.drop_tables += ['vhea', 'vmtx', 'VORG']
    return options


def build_family(
    key: str, source: Path, only: int | None, weight: tuple[str, int] | None = None
) -> tuple[list[str], int]:
    meta = FAMILIES[key]
    slices = json.loads(RANGES.read_text())[meta['ranges']]
    if only is not None:
        slices = slices[:only]

    out_dir = FRONTEND / 'public' / 'fonts' / meta['dir']
    out_dir.mkdir(parents=True, exist_ok=True)

    stem = meta['stem'] if weight is None else f"{meta['stem']}-{weight[0]}"
    weight_css = '100 900' if weight is None else str(weight[1])
    format_css = 'woff2-variations' if weight is None else 'woff2'

    css_blocks: list[str] = []
    total = 0
    started = time.time()
    for index, item in enumerate(slices):
        # recalcTimestamp=False: 저장할 때 head.modified를 현재 시각으로 바꾸지 않는다. 안 그러면 같은
        # 입력으로 다시 만들어도 슬라이스 전부가 바이트 단위로 달라져 git diff가 229장으로 불어난다.
        font = TTFont(str(source), recalcTimestamp=False)
        subsetter = subset.Subsetter(make_options())
        subsetter.populate(unicodes=subset.parse_unicodes(item['range']))
        subsetter.subset(font)
        font.flavor = 'woff2'
        target = out_dir / f'{stem}.{index}.woff2'
        font.save(str(target))
        font.close()
        size = target.stat().st_size
        total += size
        css_blocks.append(
            f"/* {meta['family']} {weight[0] if weight else ''} {item['name']} */\n".replace('  ', ' ')
            + '@font-face {\n'
            f"  font-family: '{meta['family']}';\n"
            '  font-style: normal;\n'
            f'  font-weight: {weight_css};\n'
            '  font-display: swap;\n'
            f"  src: url('/fonts/{meta['dir']}/{target.name}') format('{format_css}');\n"
            f"  unicode-range: {item['range']};\n"
            '}\n'
        )
        print(f"{stem} {index + 1}/{len(slices)} {item['name']} {size:,}B", flush=True)

    print(f'{stem}: {len(slices)} slices, {total:,}B, {time.time() - started:.0f}s', flush=True)
    return css_blocks, total


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--jp', type=Path, required=True, help='Noto Sans JP 가변 TTF')
    parser.add_argument('--tc', type=Path, required=True, help='Noto Sans TC 가변 TTF')
    parser.add_argument('--mplus-light', type=Path, required=True, help='MPLUS1p-Light.ttf')
    parser.add_argument('--mplus-bold', type=Path, required=True, help='MPLUS1p-Bold.ttf')
    parser.add_argument('--taipei-light', type=Path, required=True, help='TaipeiSansTCBeta-Light.ttf')
    parser.add_argument('--taipei-bold', type=Path, required=True, help='TaipeiSansTCBeta-Bold.ttf')
    parser.add_argument('--only', type=int, default=None, help='패밀리당 앞쪽 N개 슬라이스만 생성(확인용)')
    args = parser.parse_args()

    blocks: list[str] = []
    for key, source in (('jp', args.jp), ('tc', args.tc)):
        family_blocks, _ = build_family(key, source, args.only)
        blocks.extend(family_blocks)
    for key, sources in (
        ('mplus', {'Light': args.mplus_light, 'Bold': args.mplus_bold}),
        ('taipei', {'Light': args.taipei_light, 'Bold': args.taipei_bold}),
    ):
        for name, value in STATIC_WEIGHTS.items():
            family_blocks, _ = build_family(key, sources[name], args.only, (name, value))
            blocks.extend(family_blocks)

    CSS_OUT.write_text(CSS_HEADER + '\n' + '\n'.join(blocks))
    print(f'wrote {CSS_OUT.relative_to(FRONTEND)} ({len(blocks)} @font-face)')


if __name__ == '__main__':
    main()
