# InterviewGuide 项目拆解站点

基于本仓库 commit `b2d1ca0` 源码整理的项目解析网站：架构、AI 底座、可靠性、业务模块、语音链路、前端、质量与审查清单，以及面试问答、简历写法和用 Claude Code 复刻项目的方法。纯静态页面，没有构建依赖。

## 目录

```
project-analysis-site/
├── index.html ... vibe-coding.html   # 生成好的 11 个页面，直接部署这些
├── assets/site.css                    # 设计令牌、明暗主题、组件与图表样式
├── assets/site.js                     # 主题切换、移动端导航、本页目录、问答自测、复制按钮
├── src/*.html                         # 每页的正文片段（编辑内容改这里）
└── build.py                           # 把片段拼成完整页面，并校验 id、站内链接与编号
```

## 本地预览

```bash
cd project-analysis-site
python3 -m http.server 8000
# 浏览器打开 http://localhost:8000/
```

直接双击 `index.html` 也能打开，所有链接都是相对路径。

## 放到自己的网站

1. 把 `*.html` 和 `assets/` 复制到网站的任意子目录，例如 `/interview-guide/`。`src/`、`build.py`、`README.md` 不需要部署。
2. 页面之间只用相对链接，放在哪个路径下都能工作。
3. 想让页头品牌、导航或页脚链接回你的主站，修改 `build.py` 顶部的 `SITE_NAME`、`body_html()` 里的页头和页脚，然后重新生成。

## 修改内容

```bash
# 编辑 src/ 下对应页面的正文片段后：
python3 build.py
```

脚本会重新生成全部页面，并检查：重复的 id、指向不存在页面或锚点的站内链接、复制按钮的目标、未闭合的标签、审查清单是否为 18 条、问答编号是否连续。检查失败时不会报 OK。

## 字体与访问速度

标题使用 Google Fonts 的 Noto Serif SC，以不阻塞渲染的方式加载；加载不到时回退到系统衬线字体（宋体、Songti SC 等），不影响阅读。如果网站主要面向中国大陆访问，可以在 `build.py` 的 `full_page()` 里去掉字体链接，或改为自托管字体。

## 说明

- 文中的类名、配置值和提交号均以 commit `b2d1ca0` 为准，项目后续变更后需要同步更新。
- 原项目采用 AGPL-3.0 协议；本站为源码阅读笔记，不包含原项目代码。
