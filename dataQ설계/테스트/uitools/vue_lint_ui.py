"""
Vue UI 정적 분석기 — 객관적 문제 후보를 자동 추출.

스캔 대상: q-center/vue/front/src/components/*.vue + src/views/**/*.vue

검사 항목:
  1. 반응 없는 버튼 — <v-btn> 인데 @click / @click.stop / @click.native / :to / href / type=submit 없음
  2. 핸들러 깨짐 — @click="someFn" 인데 methods 에 someFn 없음 (computed/data 도 X)
  3. 버튼 사이즈 mix — 같은 파일 안 small / x-small / large / size 없는 것 혼재
  4. v-icon 사이즈 mix — 같은 파일 안 small / x-small / large / size 없는 것 혼재
  5. disabled 빈 바인딩 — :disabled="" 또는 :disabled (값 빈)
  6. v-btn 안에 v-icon + text 인데 v-icon left/right 없음 (정렬 깨질 가능성)
  7. 파일별 통계 — v-btn 수, 핸들러 함수 명세

출력: stdout (Markdown) + 옵션 --out 파일
"""
import os
import re
import sys
from collections import defaultdict, Counter

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
SRC_DIRS = [
    os.path.join(ROOT, "q-center", "vue", "front", "src", "components"),
    os.path.join(ROOT, "q-center", "vue", "front", "src", "views"),
]

# 패턴들
RE_TEMPLATE = re.compile(r"<template>(.*?)</template>", re.DOTALL | re.IGNORECASE)
RE_SCRIPT   = re.compile(r"<script[^>]*>(.*?)</script>", re.DOTALL | re.IGNORECASE)
# v-btn 태그 (open + 내부) — multiline 허용
RE_VBTN     = re.compile(r"<v-btn\b([^>]*?)(?:/>|>(.*?)</v-btn>)", re.DOTALL | re.IGNORECASE)
RE_VICON    = re.compile(r"<v-icon\b([^>]*?)(?:/>|>(.*?)</v-icon>)", re.DOTALL | re.IGNORECASE)
RE_AT_CLICK = re.compile(r"(?:@click|v-on:click)(?:\.[\w]+)?\s*=\s*[\"']([^\"']+)[\"']")
RE_TO_PROP  = re.compile(r"\b:to\s*=|\bto\s*=|\bv-bind:to\s*=")
RE_HREF     = re.compile(r"\bhref\s*=")
RE_TYPE_SUB = re.compile(r'type\s*=\s*["\']submit["\']')
RE_DISABLED = re.compile(r":disabled\s*=\s*[\"']([^\"']*)[\"']")
RE_SIZE_BTN = re.compile(r"\b(x-small|small|large|x-large)\b")
RE_VICON_LEFT_RIGHT = re.compile(r"\b(left|right)\b")

# methods/computed/data 함수/필드명 추출
RE_METHODS_BLOCK = re.compile(r"methods\s*:\s*{(.*?)\n\s*}", re.DOTALL)
RE_COMPUTED_BLOCK = re.compile(r"computed\s*:\s*{(.*?)\n\s*}", re.DOTALL)
RE_DATA_RETURN = re.compile(r"data\s*\(\s*\)\s*\{[^}]*?return\s*\{(.*?)\n\s*\};?", re.DOTALL)
RE_FN_DEF      = re.compile(r"\b([a-zA-Z_$][\w$]*)\s*(?:\([^)]*\)|\([^)]*\)\s*\{|:\s*function|:\s*\(?[^)]*\)?\s*=>)", re.MULTILINE)
RE_KEY         = re.compile(r"^\s*([a-zA-Z_$][\w$]*)\s*[:=]", re.MULTILINE)


def parse_vue(path):
    with open(path, "r", encoding="utf-8") as f:
        src = f.read()
    tpl_m = RE_TEMPLATE.search(src)
    scr_m = RE_SCRIPT.search(src)
    return src, (tpl_m.group(1) if tpl_m else ""), (scr_m.group(1) if scr_m else "")


def extract_identifiers(script_src):
    """script 전체에서 함수/필드 정의 패턴을 직접 매칭 (brace counting 회피)."""
    ids = set()
    # 패턴 1: ES6 메서드 — `name(args) {` (Vuetify methods 흔한 패턴)
    for m in re.finditer(r"^\s*(?:async\s+)?([a-zA-Z_$][\w$]*)\s*\([^)]*\)\s*\{", script_src, re.MULTILINE):
        ids.add(m.group(1))
    # 패턴 2: object literal property — `name: function (...)` 또는 `name: (` 또는 `name: ()=>{`
    for m in re.finditer(r"^\s*([a-zA-Z_$][\w$]*)\s*:\s*(?:function|async|\(|\[a-zA-Z_$])", script_src, re.MULTILINE):
        ids.add(m.group(1))
    # 패턴 3: 화살표 함수 — `name: () =>` 또는 `name: arg =>`
    for m in re.finditer(r"^\s*([a-zA-Z_$][\w$]*)\s*:\s*[^=]*=>", script_src, re.MULTILINE):
        ids.add(m.group(1))
    # 패턴 4: data return 안의 단순 키 — `name: value`
    for m in re.finditer(r"^\s*([a-zA-Z_$][\w$]*)\s*:", script_src, re.MULTILINE):
        ids.add(m.group(1))
    # 패턴 5: const/let/var — top-level 변수 / import 명
    for m in re.finditer(r"\b(?:const|let|var)\s+([a-zA-Z_$][\w$]*)", script_src):
        ids.add(m.group(1))
    for m in re.finditer(r"^\s*import\s+(?:\{[^}]*\}|[a-zA-Z_$][\w$]*|\*\s+as\s+[a-zA-Z_$][\w$]*)", script_src, re.MULTILINE):
        # import { a, b } from '...'  → a, b 추출
        line = m.group(0)
        for n in re.finditer(r"([a-zA-Z_$][\w$]*)", line):
            ids.add(n.group(1))
    # 흔한 글로벌
    ids.update({"$emit", "$swal", "$APIURL", "$store", "$router", "$route",
                 "axios", "console", "true", "false", "null", "this", "window", "document",
                 "Object", "Array", "JSON", "Date", "Math", "String", "Number",
                 "Promise", "Map", "Set"})
    return ids


def expression_root_id(expr):
    """`fnA(arg)` → `fnA`, `obj.method` → `obj`, `() => ...` → None"""
    s = expr.strip()
    # 화살표/함수 표현식이면 skip
    if "=>" in s or s.startswith("function"):
        return None
    # `prop.fn(args)` → prop
    m = re.match(r"([a-zA-Z_$][\w$]*)", s)
    return m.group(1) if m else None


def find_vbtns(template_src):
    """template 에서 모든 v-btn 의 (raw_attrs_str, inner_text) 추출 + 라인번호."""
    btns = []
    for m in RE_VBTN.finditer(template_src):
        attrs = m.group(1) or ""
        inner = m.group(2) or ""
        line = template_src[:m.start()].count("\n") + 1
        btns.append((line, attrs, inner))
    return btns


def find_vicons(template_src):
    icons = []
    for m in RE_VICON.finditer(template_src):
        attrs = m.group(1) or ""
        inner = m.group(2) or ""
        line = template_src[:m.start()].count("\n") + 1
        icons.append((line, attrs, inner))
    return icons


def lint_file(path):
    rel = os.path.relpath(path, ROOT)
    src, tpl, scr = parse_vue(path)
    if not tpl:
        return None
    ids = extract_identifiers(scr)
    btns = find_vbtns(tpl)
    icons = find_vicons(tpl)

    issues = {
        "no_handler": [],          # 반응 없는 버튼
        "broken_handler": [],      # methods 에 없는 함수 참조
        "btn_size_mix": [],        # 사이즈 혼재 (요약)
        "icon_size_mix": [],
        "disabled_empty": [],
        "btn_icon_text_no_align": [],  # icon + text 인데 left/right 누락
    }
    btn_sizes = Counter()
    icon_sizes = Counter()

    for (line, attrs, inner) in btns:
        # 반응 검사
        click_m = RE_AT_CLICK.search(attrs)
        has_to = bool(RE_TO_PROP.search(attrs))
        has_href = bool(RE_HREF.search(attrs))
        is_submit = bool(RE_TYPE_SUB.search(attrs))
        if not click_m and not has_to and not has_href and not is_submit:
            # disabled / readonly 상수 인 버튼은 의도일 수 있어 plain text 50자 이하만 후보로
            text_only = re.sub(r"<[^>]+>", "", inner).strip()
            issues["no_handler"].append({
                "line": line,
                "snippet": _short_attr(attrs),
                "text": text_only[:60]
            })

        if click_m:
            expr = click_m.group(1)
            root = expression_root_id(expr)
            if root and root not in ids:
                # 일부 유효 식별자 (this/$xxx) 제외
                if not (root.startswith("$") or root in ("this",)):
                    issues["broken_handler"].append({
                        "line": line,
                        "expr": expr[:60],
                        "missing": root
                    })

        # 사이즈
        sz = RE_SIZE_BTN.search(attrs)
        btn_sizes[sz.group(1) if sz else "(none)"] += 1

        # disabled 빈 바인딩
        d_m = RE_DISABLED.search(attrs)
        if d_m and not d_m.group(1).strip():
            issues["disabled_empty"].append({"line": line, "snippet": _short_attr(attrs)})

        # icon + text 정렬
        has_icon_inside = "<v-icon" in inner.lower()
        text_only = re.sub(r"<[^>]+>", "", inner).strip()
        if has_icon_inside and text_only:
            # 내부 v-icon 에 left/right 있는지
            inner_icons = list(RE_VICON.finditer(inner))
            for im in inner_icons:
                iattrs = im.group(1) or ""
                if not RE_VICON_LEFT_RIGHT.search(iattrs):
                    issues["btn_icon_text_no_align"].append({
                        "line": line,
                        "snippet": _short_attr(iattrs)
                    })
                    break

    # 버튼 사이즈 혼재 — 종류 ≥ 2 이면 issue
    if len([k for k, v in btn_sizes.items() if v > 0]) >= 2 and len(btns) >= 3:
        issues["btn_size_mix"].append({
            "summary": dict(btn_sizes),
            "btn_total": len(btns)
        })

    # icon 사이즈 mix
    for (line, attrs, _) in icons:
        sz = RE_SIZE_BTN.search(attrs)
        icon_sizes[sz.group(1) if sz else "(none)"] += 1
    if len([k for k, v in icon_sizes.items() if v > 0]) >= 2 and len(icons) >= 5:
        issues["icon_size_mix"].append({
            "summary": dict(icon_sizes),
            "icon_total": len(icons)
        })

    # 빈 issues 키는 제거
    issues = {k: v for k, v in issues.items() if v}
    return {
        "path": rel,
        "btn_count": len(btns),
        "icon_count": len(icons),
        "issues": issues
    }


def _short_attr(s):
    return re.sub(r"\s+", " ", s).strip()[:80]


def collect_files():
    files = []
    for d in SRC_DIRS:
        if not os.path.isdir(d):
            continue
        for root, _, names in os.walk(d):
            for n in names:
                if n.endswith(".vue"):
                    files.append(os.path.join(root, n))
    return sorted(files)


def render_md(reports):
    lines = []
    lines.append("# Vue UI 정적 분석 리포트\n")
    lines.append(f"- 스캔 파일 수: **{len(reports)}**")
    total_btn = sum(r["btn_count"] for r in reports)
    total_icon = sum(r["icon_count"] for r in reports)
    lines.append(f"- 총 v-btn: **{total_btn}** / v-icon: **{total_icon}**")
    issue_files = [r for r in reports if r["issues"]]
    lines.append(f"- 이슈 파일: **{len(issue_files)}**\n")

    # 카테고리별 카운트
    cat_count = defaultdict(int)
    for r in reports:
        for k, v in r["issues"].items():
            cat_count[k] += len(v)
    if cat_count:
        lines.append("## 카테고리별 합계\n")
        labels = {
            "no_handler":            "1. 반응 없는 버튼",
            "broken_handler":        "2. methods 에 없는 핸들러",
            "btn_size_mix":          "3. v-btn 사이즈 혼재 파일",
            "icon_size_mix":         "4. v-icon 사이즈 혼재 파일",
            "disabled_empty":        "5. disabled 빈 바인딩",
            "btn_icon_text_no_align": "6. icon+text 인데 left/right 누락"
        }
        for k, v in sorted(cat_count.items()):
            lines.append(f"- {labels.get(k, k)}: **{v}** 건")
        lines.append("")

    if issue_files:
        lines.append("## 파일별 상세\n")
        for r in issue_files:
            lines.append(f"### `{r['path']}` (v-btn {r['btn_count']} / v-icon {r['icon_count']})\n")
            for cat, items in r["issues"].items():
                label = {
                    "no_handler":            "반응 없는 버튼",
                    "broken_handler":        "핸들러 깨짐",
                    "btn_size_mix":          "v-btn 사이즈 혼재",
                    "icon_size_mix":         "v-icon 사이즈 혼재",
                    "disabled_empty":        "disabled 빈 바인딩",
                    "btn_icon_text_no_align": "icon+text 정렬 누락"
                }.get(cat, cat)
                lines.append(f"**{label}** ({len(items)}건)")
                for it in items[:10]:  # 최대 10건만
                    if "line" in it:
                        if "missing" in it:
                            lines.append(f"  - L{it['line']}: `@click=\"{it['expr']}\"` → **`{it['missing']}` 미정의**")
                        elif "expr" in it:
                            lines.append(f"  - L{it['line']}: `{it['expr']}`")
                        elif "text" in it:
                            text = it.get("text", "").strip() or "(빈 텍스트)"
                            lines.append(f"  - L{it['line']}: \"{text}\" — `{it['snippet']}`")
                        else:
                            lines.append(f"  - L{it['line']}: `{it.get('snippet', '')}`")
                    else:
                        lines.append(f"  - {it}")
                if len(items) > 10:
                    lines.append(f"  - ... 외 {len(items)-10}건")
                lines.append("")
            lines.append("")
    return "\n".join(lines)


def main():
    out_path = None
    if "--out" in sys.argv:
        i = sys.argv.index("--out")
        out_path = sys.argv[i + 1]

    files = collect_files()
    print(f"[scan] {len(files)} files in {SRC_DIRS}")
    reports = []
    for p in files:
        r = lint_file(p)
        if r is not None:
            reports.append(r)
    md = render_md(reports)
    if out_path:
        with open(out_path, "w", encoding="utf-8") as f:
            f.write(md)
        print(f"[out] {out_path}")
    else:
        print(md)


if __name__ == "__main__":
    main()
