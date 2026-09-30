# InterviewGuide 项目拆解站点

基于本仓库 commit `b2d1ca0` 源码整理的项目解析网站：架构、AI 底座、可靠性、业务模块、语音链路、前端、质量与审查清单，以及面试问答、简历写法和用 Claude Code 复刻项目的方法。另有一组面向 Agent 应用开发岗的 **Agent 专题**（6 页）：用 Agent 的视角审计项目、拆解工具调用与记忆等核心机制、给出 8 个可复现的源码实验和 12 条专项发现、Agent 化改造方案、60 道 Agent 面试题和一页考前速记。纯静态页面，没有构建依赖。

## 目录

```
project-analysis-site/
├── index.html ... agent-cheatsheet.html  # 生成好的 17 个页面，直接部署这些
├── assets/site.css                       # 设计令牌、明暗主题、组件与图表样式
├── assets/site.js                        # 主题切换、页面导航面板、本页目录、问答自测、复制按钮
├── src/*.html                            # 每页的正文片段（编辑内容改这里）
├── build.py                              # 把片段拼成完整页面，并校验 id、站内链接与编号
└── lab/                                  # Agent 专题的最小实验工程（独立 Maven 项目，不部署）
```

## 本地预览

```bash
cd project-analysis-site
python3 -m http.server 8000
# 浏览器打开 http://localhost:8000/
```

直接双击 `index.html` 也能打开，所有链接都是相对路径。

## 运行源码实验

`lab/` 用与项目相同版本的 Spring AI 2.0.0 和 spring-ai-agent-utils 0.10.0，配合一个脚本化的假模型复现框架行为，不联网、不需要 API Key。需要 JDK 21+ 和 Maven：

```bash
cd project-analysis-site/lab
./run.sh                       # 默认读取仓库里的 app/src/main/resources
./run.sh /path/to/resources    # 也可以指向修改过的资源目录
```

输出对应站点 `agent-lab.html` 里的 E1–E8。`lab/src/main/java/lab/agent/` 下是改造方案里的代码草图（面试官工具集、面试官 Agent、循环保护、输出护栏、token 统计、评委一致性、检索融合、MCP 工具）。实验工程不属于 Gradle 构建，不影响项目 CI。

## 放到自己的网站

1. 把 `*.html` 和 `assets/` 复制到网站的任意子目录，例如 `/interview-guide/`。`src/`、`lab/`、`build.py`、`README.md` 不需要部署。
2. 页面之间只用相对链接，放在哪个路径下都能工作。
3. 想让页头品牌、导航或页脚链接回你的主站，修改 `build.py` 顶部的 `SITE_NAME`、`body_html()` 里的页头和页脚，然后重新生成。

## 修改内容

```bash
# 编辑 src/ 下对应页面的正文片段后：
python3 build.py
```

脚本会重新生成全部页面，并检查：重复的 id、指向不存在页面或锚点的站内链接、复制按钮的目标、未闭合的标签、通用审查清单是否为 18 条、通用问答编号 Q01–Q50 是否连续、Agent 专项发现是否为 A01–A12、Agent 题库编号 AQ01–AQ60 是否连续。检查失败时不会报 OK。

## 字体与访问速度

标题使用 Google Fonts 的 Noto Serif SC，以不阻塞渲染的方式加载；加载不到时回退到系统衬线字体（宋体、Songti SC 等），不影响阅读。如果网站主要面向中国大陆访问，可以在 `build.py` 的 `full_page()` 里去掉字体链接，或改为自托管字体。

## 说明

- 文中的类名、配置值和提交号均以 commit `b2d1ca0` 为准，项目后续变更后需要同步更新。
- Agent 专题中涉及框架行为的结论，对照了 Spring AI 2.0.0、2.0.0-M4 与 spring-ai-agent-utils 0.10.0 的源码，并用 `lab/` 中的实验验证。
- 原项目采用 AGPL-3.0 协议；本站为源码阅读笔记，不包含原项目代码，`lab/` 中的代码为本站编写。
