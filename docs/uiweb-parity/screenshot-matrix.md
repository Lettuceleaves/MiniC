# UIWeb 与 UILocal 截图验收矩阵

本文档定义 `uiweb` 复刻 `uilocal` 的截图和布局验收范围。截图验收不能替代功能测试；它只负责证明主要页面、阶段视图、调试子页、设置、介绍和底部面板都被真实渲染，并且有可对比的 JavaFX 参考图。

## 生成方式

```powershell
cd E:\projects\MiniC\uiweb
npm run verify:screenshots
```

该命令会按顺序执行：

1. 通过 `uiweb/tools/capture-uilocal-screenshots.ps1` 调用 Gradle 任务 `captureUiLocalScreenshots`，生成 JavaFX 参考图。
2. 启动 UIAPI HTTP 服务和 Vite，使用 Playwright 生成 UIWeb 截图。
3. 校验每个状态都有 JavaFX 与 UIWeb 两张 PNG，且尺寸、文件大小、核心布局指标满足要求。
4. 生成报告：`uiweb-render-check/parity-report/index.html`。

JavaFX 参考图使用 `MiniCWorkbenchShell#createRoot()` 生成 root/Scene 内容区截图，不包含 Windows 标题栏和系统窗口边框。UIWeb 截图使用浏览器 viewport 内容区，因此二者比较区域一致。

## 视口

- `desktop-1920x1080`
- `desktop-1366x768`
- `mobile-390x844`

## 状态矩阵

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

## 自动失败条件

- 任一状态缺 JavaFX 参考图或 UIWeb 截图。
- 任一 PNG 小于 8 KB，视为空图或截断图。
- 任一 PNG 尺寸不等于声明视口。
- UIWeb 缺少必需 DOM：activity bar、status bar、侧栏、编辑器、行号、断点、debug 子页、pipeline 可视区域、设置页、介绍页或底部面板。
- UIWeb 核心布局指标超差：activity bar 宽度、status bar 高度、debug view selector 宽度、editor gutter 宽度。
- Pipeline/debug 页面只渲染空壳，没有真实文本或 DTO 内容。

## 人工复核

自动截图通过后，人工复核只能记录观察，不能豁免失败项。必须逐视口对照报告中的 JavaFX 与 UIWeb 两列，重点检查右侧 inspector、pipeline 每个阶段、debugger 子页、源码滚动/断点/当前行、设置、介绍和底部面板。
