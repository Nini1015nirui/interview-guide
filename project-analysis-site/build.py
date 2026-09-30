#!/usr/bin/env python3
"""Assemble the InterviewGuide analysis site from content fragments and validate it.

Usage:
  python3 build.py                        # rebuild *.html next to this script from src/
  python3 build.py --artifact-out DIR     # also write a skeleton-less index.html for Claude Artifacts
"""
import argparse
import html
import re
import sys
from html.parser import HTMLParser
from pathlib import Path

HERE = Path(__file__).resolve().parent
SRC = HERE / "src"
OUT = HERE
ARTIFACT_DIR = None

SITE_NAME = "InterviewGuide 项目拆解"
BASELINE = "b2d1ca0"

# (slug, nav label, card title, meta description, group)
PAGES = [
    ("index", "总览", "项目总览",
     "InterviewGuide 智能面试平台的源码级拆解：定位、功能地图、三种交互方式与最值得讲的技术点。", "解析"),
    ("architecture", "架构", "整体架构与技术选型",
     "分层与包结构、统一响应与异常、请求生命周期、16 张表的数据模型、选型取舍、部署与配置。", "解析"),
    ("ai-foundation", "AI 底座", "AI 工程底座",
     "多模型注册表、结构化输出重试、Prompt 模板与注入防御、Skill 驱动出题与统一评估引擎。", "解析"),
    ("reliability", "可靠性", "异步、一致性与限流",
     "Redis Stream 异步模板、题库生成状态机、幂等创建、缓存、多维 Lua 限流与事务边界。", "解析"),
    ("modules", "业务模块", "业务模块逐个拆解",
     "简历、文字面试、RAG 知识库、题库面试、面试日程与系统设置的真实链路与问题。", "解析"),
    ("voice", "语音面试", "实时语音面试链路",
     "从麦克风到扬声器的完整时序：识别、合成、回声防护、会话生命周期与已知问题。", "解析"),
    ("frontend", "前端", "前端工程实现",
     "统一请求层、自研 SSE 客户端、幂等路由、轮询约定、音频采集与播放、测试与构建。", "解析"),
    ("quality", "质量与问题", "测试、CI 与代码审查",
     "测试策略、TDD 证据、CI 与提交规范，以及 18 条按严重程度排序的代码审查发现。", "解析"),
    ("interview-qa", "面试问答", "面试高频问答",
     "50 道按主题分组的面试追问与参考回答，支持自测模式。", "面试"),
    ("resume", "简历与讲述", "简历写法与讲述脚本",
     "简历条目示例、1/3/10 分钟讲述脚本、STAR 故事与被质疑时的应对。", "面试"),
    ("vibe-coding", "Claude Code 复刻", "用 Claude Code 从零构建",
     "仓库里的 AI 协作证据、上下文工程、标准开发回合、分阶段复刻路线与提示词、真实踩坑。", "AI"),
]

GROUPS = [("解析", "项目解析"), ("面试", "面试准备"), ("AI", "AI 编码")]

FONT_HREF = ("https://fonts.googleapis.com/css2?family=Noto+Serif+SC:wght@600;700&display=swap")

THEME_INIT = ("<script>(function(){try{var t=localStorage.getItem('ig-analysis-theme');"
              "if(t==='dark'||t==='light'){document.documentElement.setAttribute('data-theme',t);}}"
              "catch(e){}})();</script>")


def page_title(slug, label):
    return SITE_NAME if slug == "index" else f"{label} · {SITE_NAME}"


def nav_html(active):
    parts = []
    for i, (gkey, glabel) in enumerate(GROUPS):
        if i:
            parts.append('      <span class="nav-sep" aria-hidden="true"></span>')
        links = []
        for slug, label, _t, _d, group in PAGES:
            if group != gkey:
                continue
            cur = ' aria-current="page"' if slug == active else ""
            links.append(f'<a href="{slug}.html"{cur}>{html.escape(label)}</a>')
        parts.append(f'      <div class="nav-group"><span class="nav-group-label">{glabel}</span>'
                     + "".join(links) + "</div>")
    return "\n".join(parts)


def pager_html(idx):
    items = []
    if idx > 0:
        slug, _l, title, _d, _g = PAGES[idx - 1]
        items.append(f'<a class="prev" href="{slug}.html"><span class="dir">上一页</span>'
                     f'<span class="ttl">{html.escape(title)}</span></a>')
    if idx < len(PAGES) - 1:
        slug, _l, title, _d, _g = PAGES[idx + 1]
        items.append(f'<a class="next" href="{slug}.html"><span class="dir">下一页</span>'
                     f'<span class="ttl">{html.escape(title)}</span></a>')
    return ('    <nav class="pager" aria-label="上一页与下一页">\n      '
            + "\n      ".join(items) + "\n    </nav>")


def body_html(slug, idx, content):
    return f"""<a class="skip" href="#main">跳到正文</a>
<header class="topbar">
  <div class="topbar-inner">
    <a class="brand" href="index.html"><span class="brand-mark">IG</span><span class="brand-name">{SITE_NAME}</span></a>
    <button type="button" class="icon-btn nav-toggle" data-nav-toggle aria-controls="sitenav" aria-expanded="false">页面</button>
    <nav class="sitenav" id="sitenav" aria-label="站点导航">
{nav_html(slug)}
    </nav>
    <button type="button" class="icon-btn" data-theme-toggle aria-label="切换主题">深色</button>
  </div>
</header>
<div class="shell">
  <aside class="toc" aria-label="本页目录">
    <details class="toc-box"><summary>本页目录</summary><ol class="toc-list" id="toc-list"></ol></details>
  </aside>
  <main class="content" id="main">
{content.rstrip()}
{pager_html(idx)}
  </main>
</div>
<footer class="sitefoot">
  <div class="sitefoot-inner">
    <span>{SITE_NAME} · 基于 commit {BASELINE}（2026-08-14）的源码阅读整理</span>
    <span>原项目采用 AGPL-3.0 协议 · 本站内容用于学习与面试准备</span>
  </div>
</footer>
<script src="assets/site.js"></script>"""


def full_page(slug, label, desc, body, font_nonblocking=True):
    if font_nonblocking:
        font = (f'<link rel="stylesheet" href="{FONT_HREF}" media="print" onload="this.media=\'all\'">\n'
                f'<noscript><link rel="stylesheet" href="{FONT_HREF}"></noscript>')
    else:
        font = f'<link rel="stylesheet" href="{FONT_HREF}">'
    return f"""<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>{html.escape(page_title(slug, label))}</title>
<meta name="description" content="{html.escape(desc)}">
<meta name="color-scheme" content="light dark">
{THEME_INIT}
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
{font}
<link rel="stylesheet" href="assets/site.css">
</head>
<body>
{body}
</body>
</html>
"""


def artifact_main(slug, label, desc, body):
    """The artifact host wraps the main page in its own skeleton: no doctype/html/head/body here."""
    return f"""<title>{html.escape(page_title(slug, label))}</title>
<meta name="description" content="{html.escape(desc)}">
{THEME_INIT}
<link rel="stylesheet" href="{FONT_HREF}">
<link rel="stylesheet" href="assets/site.css">
{body}
"""


# ---------------------------------------------------------------- validation
VOID = {"area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param",
        "source", "track", "wbr"}


class Checker(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.stack = []
        self.errors = []
        self.ids = []
        self.hrefs = []
        self.copy_targets = []

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if "id" in a:
            self.ids.append(a["id"])
        if tag == "a" and "href" in a:
            self.hrefs.append(a["href"])
        if "data-copy" in a:
            self.copy_targets.append(a["data-copy"])
        if tag not in VOID:
            self.stack.append((tag, self.getpos()))

    def handle_startendtag(self, tag, attrs):
        a = dict(attrs)
        if "id" in a:
            self.ids.append(a["id"])
        # self-closing SVG elements (rect, path, line, ...) are fine

    def handle_endtag(self, tag):
        if tag in VOID:
            return
        if not self.stack:
            self.errors.append(f"unexpected </{tag}> at {self.getpos()}")
            return
        top, pos = self.stack[-1]
        if top == tag:
            self.stack.pop()
            return
        # tolerate implicitly closed <p>/<li> by searching down the stack
        names = [t for t, _ in self.stack]
        if tag in names:
            while self.stack and self.stack[-1][0] != tag:
                t, p = self.stack.pop()
                self.errors.append(f"<{t}> opened at {p} closed implicitly by </{tag}> at {self.getpos()}")
            self.stack.pop()
        else:
            self.errors.append(f"stray </{tag}> at {self.getpos()} (open: <{top}> at {pos})")


def check_fragment(name, text):
    c = Checker()
    c.feed(text)
    c.close()
    errs = list(c.errors)
    for t, p in c.stack:
        errs.append(f"unclosed <{t}> opened at {p}")
    dup = sorted({i for i in c.ids if c.ids.count(i) > 1})
    if dup:
        errs.append(f"duplicate ids: {dup}")
    for target in c.copy_targets:
        if target not in c.ids:
            errs.append(f"data-copy target missing: {target}")
    return c, errs


def main():
    global ARTIFACT_DIR
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--artifact-out", type=Path, default=None,
                        help="directory for the skeleton-less artifact index.html")
    args = parser.parse_args()
    ARTIFACT_DIR = args.artifact_out
    OUT.mkdir(parents=True, exist_ok=True)
    if ARTIFACT_DIR is not None:
        ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)
    fragments = {}
    for slug, *_ in PAGES:
        f = SRC / f"{slug}.html"
        if not f.exists():
            sys.exit(f"missing fragment: {f}")
        fragments[slug] = f.read_text(encoding="utf-8")

    all_errors = []
    page_ids = {}
    page_links = {}
    for idx, (slug, label, _title, desc, _g) in enumerate(PAGES):
        body = body_html(slug, idx, fragments[slug])
        checker, errs = check_fragment(slug, body)
        page_ids[slug] = set(checker.ids)
        page_links[slug] = checker.hrefs
        all_errors += [f"[{slug}] {e}" for e in errs]
        (OUT / f"{slug}.html").write_text(full_page(slug, label, desc, body), encoding="utf-8")
        if slug == "index" and ARTIFACT_DIR is not None:
            (ARTIFACT_DIR / "index.html").write_text(artifact_main(slug, label, desc, body),
                                                     encoding="utf-8")

    slugs = {s for s, *_ in PAGES}
    link_count = 0
    for slug, hrefs in page_links.items():
        for href in hrefs:
            if href.startswith(("http://", "https://", "mailto:")):
                continue
            link_count += 1
            m = re.fullmatch(r"(?:([a-z0-9-]+)\.html)?(?:#([A-Za-z0-9_.~-]+))?", href)
            if not m:
                all_errors.append(f"[{slug}] unparseable href: {href}")
                continue
            target, anchor = m.group(1) or slug, m.group(2)
            if target not in slugs:
                all_errors.append(f"[{slug}] link to unknown page: {href}")
            elif anchor and anchor not in page_ids[target]:
                all_errors.append(f"[{slug}] link to missing anchor: {href}")

    # the index promises 18 findings; make sure the quality page actually lists 18
    findings = re.findall(r"<td>Q(\d{2}) <span class=\"tag", fragments["quality"])
    if len(findings) != 18:
        all_errors.append(f"[quality] expected 18 findings, found {len(findings)}")
    qa = re.findall(r'<span class="q-no">Q(\d{2})</span>', fragments["interview-qa"])
    if len(qa) != 50 or qa != [f"{i:02d}" for i in range(1, 51)]:
        all_errors.append(f"[interview-qa] question numbering broken: {len(qa)} items")

    for slug in fragments:
        text = fragments[slug]
        if re.search(r"<(html|head|body)\b", text):
            all_errors.append(f"[{slug}] fragment must not contain html/head/body")

    print(f"pages: {len(PAGES)}, internal links checked: {link_count}")
    for slug, *_ in PAGES:
        size = (OUT / f"{slug}.html").stat().st_size
        print(f"  {slug:14s} {size/1024:7.1f} KB  ids={len(page_ids[slug])}")
    if all_errors:
        print("\nERRORS:")
        for e in all_errors:
            print("  " + e)
        sys.exit(1)
    print("OK")


if __name__ == "__main__":
    main()
