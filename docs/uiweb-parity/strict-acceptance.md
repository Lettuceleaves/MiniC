# UIWeb 严格一致性最终验收

本文档定义 `uilocal`、`uiapi`、`uiweb` 当前阶段的最终验收标准。目标不是“页面能打开”，而是保证 UIWeb 没有 mock、占位、降级路径，并且所有运行时能力都通过 UIAPI 接入。

## 必跑命令

```powershell
cd E:\projects\MiniC
.\gradlew.bat test

cd E:\projects\MiniC\uiweb
npm run verify:strict-final

cd E:\projects\MiniC
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

截图矩阵详见 [screenshot-matrix.md](screenshot-matrix.md)。

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
