#!/usr/bin/env python3
"""
Noto Sans JP·TC 가변 폰트를 unicode-range 슬라이스 woff2로 나누고 `@font-face` CSS를 만든다.

실행(저장소 frontend 디렉터리에서):

    uvx --from 'fonttools[woff]' python scripts/subset-cjk-fonts.py \
        --jp <NotoSansJP-VariableFont_wght.ttf> --tc <NotoSansTC-VariableFont_wght.ttf>

산출물:
- public/fonts/noto-sans-jp/NotoSansJP.<n>.woff2, public/fonts/noto-sans-tc/NotoSansTC.<n>.woff2
- src/app/styles/fonts-cjk.css (슬라이스마다 `@font-face` 한 블록)

슬라이스 범위는 scripts/cjk-unicode-ranges.json에 있다. Google Fonts가 두 패밀리에 쓰는 분할을
그대로 가져온 것이라, 화면에 실제로 나온 글자가 속한 슬라이스만 내려받게 된다(결정 6 —
DB 유래 문자열이 있어 정적 글리프 서브셋은 쓸 수 없다).

`--only N`으로 슬라이스 N개만 만들어 빠르게 확인할 수 있다.
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

FAMILIES = {
    'jp': {'family': 'Noto Sans JP', 'dir': 'noto-sans-jp', 'stem': 'NotoSansJP'},
    'tc': {'family': 'Noto Sans TC', 'dir': 'noto-sans-tc', 'stem': 'NotoSansTC'},
}

CSS_HEADER = """/**
 * CJK 본문 폰트 `@font-face`. **생성 파일이다 — 직접 고치지 않는다.**
 *
 * `scripts/subset-cjk-fonts.py`가 `scripts/cjk-unicode-ranges.json`의 슬라이스 범위로 만든다.
 * 슬라이스 범위는 Google Fonts가 Noto Sans JP·TC에 쓰는 분할을 그대로 가져왔다. 브라우저는
 * 화면에 실제로 나온 글자가 속한 슬라이스만 내려받는다.
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


def build_family(key: str, source: Path, only: int | None) -> tuple[list[str], int]:
    meta = FAMILIES[key]
    slices = json.loads(RANGES.read_text())[key]
    if only is not None:
        slices = slices[:only]

    out_dir = FRONTEND / 'public' / 'fonts' / meta['dir']
    out_dir.mkdir(parents=True, exist_ok=True)

    css_blocks: list[str] = []
    total = 0
    started = time.time()
    for index, item in enumerate(slices):
        font = TTFont(str(source))
        subsetter = subset.Subsetter(make_options())
        subsetter.populate(unicodes=subset.parse_unicodes(item['range']))
        subsetter.subset(font)
        font.flavor = 'woff2'
        target = out_dir / f"{meta['stem']}.{index}.woff2"
        font.save(str(target))
        font.close()
        size = target.stat().st_size
        total += size
        css_blocks.append(
            f"/* {meta['family']} {item['name']} */\n"
            '@font-face {\n'
            f"  font-family: '{meta['family']}';\n"
            '  font-style: normal;\n'
            '  font-weight: 100 900;\n'
            '  font-display: swap;\n'
            f"  src: url('/fonts/{meta['dir']}/{target.name}') format('woff2-variations');\n"
            f"  unicode-range: {item['range']};\n"
            '}\n'
        )
        print(f"{meta['stem']} {index + 1}/{len(slices)} {item['name']} {size:,}B", flush=True)

    print(f"{meta['stem']}: {len(slices)} slices, {total:,}B, {time.time() - started:.0f}s", flush=True)
    return css_blocks, total


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--jp', type=Path, required=True, help='Noto Sans JP 가변 TTF')
    parser.add_argument('--tc', type=Path, required=True, help='Noto Sans TC 가변 TTF')
    parser.add_argument('--only', type=int, default=None, help='패밀리당 앞쪽 N개 슬라이스만 생성(확인용)')
    args = parser.parse_args()

    blocks: list[str] = []
    for key, source in (('jp', args.jp), ('tc', args.tc)):
        family_blocks, _ = build_family(key, source, args.only)
        blocks.extend(family_blocks)

    CSS_OUT.write_text(CSS_HEADER + '\n' + '\n'.join(blocks))
    print(f'wrote {CSS_OUT.relative_to(FRONTEND)} ({len(blocks)} @font-face)')


if __name__ == '__main__':
    main()
