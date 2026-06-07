# UIWeb 严格一致性最终验收

本文档定义 `uilocal`、`uiapi`、`uiweb` 当前阶段的最终验收标准。目标不是“页面能打开”，而是保证 UIWeb 没有 mock、占位、降级路径，并且所有运行时能力都通过 UIAPI 接入。

## 必跑命令

```powershell
cd <repo-root>
.\gradlew.bat test

cd <repo-root>\uiweb
npm run verify:strict-final

cd <repo-root>
git diff --check
```

`npm run verify:strict-final` 必须顺序执行：

- `verify:mirror`
- `verify:mirror-signatures`
- `verify:placeholders`
- `verify:no-runtime-downgrade`
- `verify:adapter-completeness`
- `verify:editor-scroll`
- `verify:snapshot-parity`
- `verify:runtime-workflows`
- `verify:screenshots`
- `typecheck`
- `build`

## 全目录降级扫描

`verify:no-runtime-downgrade` 扫描 `uiweb` 全目录中的源码和工具代码，排除 `node_modules`、`dist`、`.vite`、`coverage`、`package-lock.json` 与截图输出目录。

硬失败内容：

- `UIWeb 尚未连接`
- `noApiResult`
- `mock`
- `stub`
- `dummy`
- `TODO`
- `placeholder` 占位路径
- `@ts-ignore`
- `@ts-expect-error`
- `as any`
- 本地 compiler/parser/semantic/debugger emulation 类
- 本地实时分析实现入口 `analyzeNow`

仅允许：

- JavaFX 原 CSS 类名 `activity-placeholder*`，因为它是 1:1 样式复刻，不代表空页面。
- `MiniCWorkbenchShell` mirror metadata 中来自 Java 源的 `placeholder/placeholderPage` 字段和方法签名。
- 扫描器自身和 runtime workflow 工具中用于定义禁词的字符串。

## 截图验收

`verify:screenshots` 必须生成 JavaFX 参考图和 UIWeb 截图。每个状态、每个视口都必须有两张 PNG，且 PNG 尺寸等于声明视口、文件大小大于 8 KB。

该命令会按顺序执行：

- 通过 `uiweb/tools/capture-uilocal-screenshots.ps1` 调用 Gradle 任务 `captureUiLocalScreenshots`，生成 JavaFX 参考图。
- 启动 UIAPI HTTP 服务和 Vite，使用 Playwright 生成 UIWeb 截图。
- 校验每个状态都有 JavaFX 与 UIWeb 两张 PNG，且尺寸、文件大小、核心布局指标满足要求。
- 生成报告：`uiweb-render-check/parity-report/index.html`。

JavaFX 参考图使用 `MiniCWorkbenchShell#createRoot()` 生成 root/Scene 内容区截图，不包含 Windows 标题栏和系统窗口边框。UIWeb 截图使用浏览器 viewport 内容区，因此二者比较区域一致。

### 截图视口

- `desktop-1920x1080`
- `desktop-1366x768`
- `mobile-390x844`

### 截图状态矩阵

- `pipeline-before-start`：编译页启动前，含侧栏、源码编辑器、右侧 inspector。
- `source-before-start`：源码页启动前，含行号和源码内容。
- `source-long-scroll-breakpoint`：长源码滚动后，含高亮、行号、断点。
- `pipeline-after-start`：点击开始后的编译页，含阶段状态和右侧详情。
- `pipeline-stage-source`：source 阶段。
- `pipeline-stage-preprocess`：preprocess 阶段。
- `pipeline-stage-lexer`：lexer 阶段，必须显示真实 token 行。
- `pipeline-stage-parser`：parser/AST 阶段，必须显示 AST 图区域。
- `pipeline-stage-semantic`：semantic 阶段，必须显示语义作用域。
- `pipeline-stage-ir`：IR 阶段。
- `pipeline-stage-codegen`：codegen/ASM 阶段。
- `pipeline-stage-toolchain`：toolchain 阶段。
- `pipeline-stage-execution`：execution 阶段。
- `debug-before-start`：Debugger 启动前。
- `debug-metadata`：Debugger 元数据页。
- `debug-source`：Debugger 源码区，含断点和当前执行行。
- `debug-data-structure`：Debugger 数据结构页。
- `debug-visual-diagram`：Debugger 数据结构图区域。
- `debug-ast`：Debugger AST 页。
- `debug-ir`：Debugger IR 页。
- `debug-asm`：Debugger ASM 页。
- `settings`：设置页。
- `info`：介绍页，含代码块高亮。
- `bottom-panel-collapsed`：底部面板收起。
- `bottom-panel-expanded`：底部面板展开。

### 截图自动失败条件

- 任一状态缺 JavaFX 参考图或 UIWeb 截图。
- 任一 PNG 小于 8 KB，视为空图或截断图。
- 任一 PNG 尺寸不等于声明视口。
- UIWeb 缺少必需 DOM：activity bar、status bar、侧栏、编辑器、行号、断点、debug 子页、pipeline 可视区域、设置页、介绍页或底部面板。
- UIWeb 核心布局指标超差：activity bar 宽度、status bar 高度、debug view selector 宽度、editor gutter 宽度。
- Pipeline/debug 页面只渲染空壳，没有真实文本或 DTO 内容。

自动截图通过后，人工复核只能记录观察，不能豁免失败项。必须逐视口对照报告中的 JavaFX 与 UIWeb 两列，重点检查右侧 inspector、pipeline 每个阶段、debugger 子页、源码滚动/断点/当前行、设置、介绍和底部面板。

## 不允许的通过方式

- 不允许通过静态 fixture 伪造 UIAPI DTO。
- 不允许把失败 API 请求吞掉后返回空对象。
- 不允许保留只显示空壳的 pipeline/debugger 页面。
- 不允许用本地 TS lexer/parser/debugger 替代 UIAPI。
- 不允许用人工检查豁免自动化失败。

## 通过记录

每次最终验收需要记录：

- `.\gradlew.bat test` 结果。
- `npm run verify:strict-final` 结果。
- `git diff --check` 结果。
- 截图报告路径：`uiweb-render-check/parity-report/index.html`。
- 当前提交范围。
