# MiniC 文本样式系统

本文档说明 MiniC Workbench 的文本样式抽象，供后续开发、主题扩展和 UI 接入使用。

## 背景

过去源码编辑器的语法高亮直接在 `MiniCCodeEditor` 中把 token kind 映射到 CSS class，例如 `INT -> token-keyword`。这种写法只能服务代码高亮，其他文本如诊断、面板标题、AST/IR/Assembly 文本、图形标签很难复用统一的颜色、字体、加粗和斜体切换能力。

新的文本样式系统把“文本是什么”和“它现在处于什么状态”抽象出来，由统一 resolver 转换成 JavaFX/RichTextFX 可用的 style class。

## 核心概念

### Role

`MiniCTextStyleRole` 表示文本的语义角色，也就是这段文字“是什么”。

常用角色：

| Role | 说明 | 默认字体 |
|------|------|----------|
| `BODY` | 普通 UI 正文 | UI 字体 |
| `BODY_MONO` | 等宽正文 | 等宽字体 |
| `SECONDARY` | 次要文字 | UI 字体 |
| `MUTED` / `MUTED_ALT` | 弱化文字 | UI 字体 |
| `LABEL` | 标签文字 | UI 字体 |
| `PANEL_TITLE` | 面板标题 | UI 字体 |
| `SECTION_LABEL` | 分区标题 | UI 字体 |
| `LINE_NUMBER` | 行号 | 等宽字体 |
| `DIAGNOSTIC_DETAIL` | 诊断详情 | UI 字体 |
| `GRAPH_LABEL` | 图形标签 | 等宽字体 |
| `CODE_PLAIN` | 代码普通文本 | 等宽字体 |
| `CODE_KEYWORD` | 代码关键字 | 等宽字体 |
| `CODE_IDENTIFIER` | 代码标识符 | 等宽字体 |
| `CODE_STRING` | 字符串/字符字面量 | 等宽字体 |
| `CODE_LITERAL` | 数字/布尔/null 字面量 | 等宽字体 |
| `CODE_OPERATOR` | 操作符和其他 token | 等宽字体 |
| `CODE_TYPE` | 类型或类型标签 | 等宽字体 |
| `CODE_COMMENT` | 代码、IR 或汇编注释 | 等宽字体 |

每个 role 会生成稳定 class：

```text
MiniCTextStyleRole.CODE_KEYWORD -> mc-text-code-keyword
MiniCTextStyleRole.LINE_NUMBER  -> mc-text-line-number
```

### State

`MiniCTextStyleState` 表示可叠加的临时状态，也就是这段文字“现在怎么样”。

常用状态：

| State | 说明 |
|-------|------|
| `ACTIVE` | 当前活跃文本 |
| `SELECTED` | 选中状态 |
| `FOCUSED` | 焦点状态 |
| `HOT` | 高亮/热点状态 |
| `DIAGNOSTIC` | 诊断相关文本 |
| `DEBUG_EXECUTION` | 当前 debug 执行范围 |

状态也会生成稳定 class：

```text
MiniCTextStyleState.DEBUG_EXECUTION -> mc-text-state-debug-execution
```

### Resolver

`MiniCTextStyleResolver` 将 role + states 转换为 style class 集合。默认入口是 `MiniCTextStyles`。

```java
Collection<String> classes = MiniCTextStyles.classes(
        MiniCTextStyleRole.CODE_KEYWORD,
        MiniCTextStyleState.DEBUG_EXECUTION
);
```

返回结果会包含新的统一 class，也会包含必要的旧 class alias，方便平滑迁移。

## 使用方式

### 给 Label / Text / Node 添加样式

新 UI 文本优先使用 `MiniCTextStyles.apply(...)`。

```java
Label title = new Label("当前状态");
MiniCTextStyles.apply(title, MiniCTextStyleRole.SECTION_LABEL);

Text graphLabel = new Text("node #1");
MiniCTextStyles.apply(graphLabel, MiniCTextStyleRole.GRAPH_LABEL, MiniCTextStyleState.ACTIVE);
```

如果调用点还需要保留已有布局 class，可以继续添加布局 class，但文本视觉 class 应从 `MiniCTextStyles` 获取。

```java
Label value = new Label(text);
value.getStyleClass().add("debug-summary-value"); // 布局或容器专用 class
value.getStyleClass().addAll(MiniCTextStyles.classes(MiniCTextStyleRole.BODY_MONO));
```

### 给 RichTextFX StyleSpans 添加样式

源码编辑器使用 `Collection<String>` 作为 RichTextFX 的 span 样式，因此可以直接传入 resolver 结果。

```java
builder.add(
        MiniCTextStyles.classes(MiniCTextStyleRole.CODE_PLAIN),
        length
);
```

需要叠加状态时：

```java
ArrayList<String> styles = new ArrayList<>(baseStyles);
MiniCTextStyles.addStateClasses(styles, MiniCTextStyleState.DEBUG_EXECUTION);
builder.add(styles, length);
```

### 给 token 做语法高亮

源码 token 映射由 `MiniCSyntaxTextStyleMapper` 负责。调用方不应该再直接写 token kind 到 CSS class 的 switch。

```java
private final MiniCSyntaxTextStyleMapper syntaxTextStyleMapper = new MiniCSyntaxTextStyleMapper();

Collection<String> styles = syntaxTextStyleMapper.styleClassesFor(
        token.kind(),
        overlapsDiagnostic
);
```

当前默认映射：

| Token kind | Role |
|------------|------|
| `BOOL`, `CHAR`, `INT`, `LONG`, `FLOAT`, `DOUBLE`, `EXTERN`, `STRUCT`, `RETURN`, `IF`, `ELSE`, `WHILE`, `FOR`, `BREAK`, `CONTINUE` | `CODE_KEYWORD` |
| `STRING_LITERAL`, `CHAR_LITERAL` | `CODE_STRING` |
| `INTEGER_LITERAL`, `LONG_LITERAL`, `FLOAT_LITERAL`, `DOUBLE_LITERAL`, `BOOL_LITERAL`, `NULL_LITERAL` | `CODE_LITERAL` |
| `IDENTIFIER` | `CODE_IDENTIFIER` |
| 其他 token | `CODE_OPERATOR` |

## 主题配置

主题 JSON 仍然兼容现有 key，例如 `text.body`、`syntax.keyword`、`background.running`。文本样式系统会先查新的 `textStyle.*` 和 `textStyleState.*`，没有配置时回退到旧 key。

### Role 配置

格式：

```json
"textStyle.<role themeId>.color": "#569cd6",
"textStyle.<role themeId>.fontFamily": "mono",
"textStyle.<role themeId>.fontWeight": "bold",
"textStyle.<role themeId>.fontStyle": "italic"
```

示例：

```json
"fontFamily.ui": "\"Segoe UI\", \"Microsoft YaHei\", Arial, sans-serif",
"fontFamily.mono": "Consolas, \"Courier New\", monospace",

"textStyle.code.keyword.color": "#569cd6",
"textStyle.code.keyword.fontFamily": "mono",
"textStyle.code.keyword.fontWeight": "bold",
"textStyle.code.keyword.fontStyle": "normal",

"textStyle.diagnostic.detail.color": "#0b383c",
"textStyle.diagnostic.detail.fontFamily": "ui",
"textStyle.diagnostic.detail.fontWeight": "normal",
"textStyle.diagnostic.detail.fontStyle": "italic"
```

支持的 `fontFamily` 值：

| 值 | 说明 |
|----|------|
| `ui` | 内置 UI 字体栈 |
| `mono` | 内置等宽字体栈 |
| 任意 CSS 字体栈 | 直接作为 `-fx-font-family` 输出 |

### State 配置

格式：

```json
"textStyleState.<state themeId>.color": "#000000",
"textStyleState.<state themeId>.background": "rgba(255, 214, 64, 0.38)"
```

示例：

```json
"textStyleState.debug.execution.color": "#ffffff",
"textStyleState.debug.execution.background": "rgba(255, 214, 64, 0.38)"
```

CSS 生成器会同时输出 `-fx-fill` 和 `-fx-text-fill`，因此同一套样式可用于 JavaFX `Text`、`Label` 和 RichTextFX 文本。

## 迁移规则

新增或修改文本 UI 时遵循以下规则：

1. 文本颜色、字体、加粗、斜体不要直接写在 CSS class 中，优先选择合适的 `MiniCTextStyleRole`。
2. 当前执行、诊断、选中、活跃等临时视觉效果使用 `MiniCTextStyleState` 叠加。
3. 布局、间距、边框、背景容器仍可保留在原 CSS class 中。
4. token kind 到文本角色的映射集中放在 `MiniCSyntaxTextStyleMapper`。
5. 不要在新代码中直接依赖 `token-keyword`、`token-string`、`debug-execution-range` 等旧 class；这些 class 只作为兼容 alias 保留。

迁移前：

```java
label.getStyleClass().add("assembly-text");
```

迁移后：

```java
label.getStyleClass().add("assembly-text"); // 保留布局 class
label.getStyleClass().addAll(MiniCTextStyles.classes(MiniCTextStyleRole.BODY_MONO));
```

迁移前：

```java
String tokenStyle = switch (kind) {
    case "INT", "RETURN" -> "token-keyword";
    default -> "token-operator";
};
```

迁移后：

```java
Collection<String> tokenStyles = syntaxTextStyleMapper.styleClassesFor(kind, diagnostic);
```

## 扩展一个新文本角色

当现有 role 不能准确表达新文本语义时，再新增 role。

步骤：

1. 在 `MiniCTextStyleRole` 中新增枚举值，指定 `themeId`、fallback color key、fallback font family、fallback weight/style。
2. 如果需要兼容旧 CSS class，在枚举值最后传入 legacy class。
3. 在调用点使用 `MiniCTextStyles.classes(...)` 或 `MiniCTextStyles.apply(...)`。
4. 如需主题覆盖，在 `config/themes/*.json` 中新增 `textStyle.<themeId>.*`。
5. 增加或更新测试，至少覆盖 resolver 输出和 CSS 生成结果。

示例：

```java
DEBUG_REGISTER("debug.register", "text.body", "mono", "bold", "normal")
```

对应主题覆盖：

```json
"textStyle.debug.register.color": "#9cdcfe",
"textStyle.debug.register.fontWeight": "bold"
```

## 测试建议

文本样式相关改动至少考虑以下测试：

| 场景 | 推荐测试 |
|------|----------|
| 新 role/state | `MiniCTextStyleResolverTest` |
| token 映射变化 | `MiniCSyntaxTextStyleMapperTest` |
| 主题 CSS 输出变化 | `ThemeCssGeneratorTest` |
| 编辑器高亮行为 | `MiniCSourceLoaderViewBreakpointTest` 或编辑器相关 UI 测试 |

常用验证命令：

```powershell
.\gradlew.bat --no-daemon test --tests minic.color.* --tests minic.ui.text.* --tests minic.ui.MiniCSourceLoaderViewBreakpointTest
```

全量验证：

```powershell
.\gradlew.bat --no-daemon test
```

## 当前接入状态

当前已接入源码编辑器的代码高亮、插入文本默认样式、诊断 token 状态和 debug 执行范围状态。

当前也已接入编译流程视图和 Debug 视图中的 IR/Assembly 行内高亮：

- IR 行高亮 `function`、`block`、`call`、`return`、`load`、`store` 等操作词。
- IR 临时值、局部名、函数名使用 identifier/type 相关 role。
- Assembly 行高亮 mnemonic、寄存器、directive/label、数字字面量和 `;` 注释。

后续建议逐步迁移：

- `MiniCVisualPane` 中 token、lexer、AST、semantic 等非 IR/Assembly 文本。
- `MiniCDebugPane` 中 metadata、visual label、变量树等非 IR/Assembly 文本。
- `MiniCInspectorView`、`MiniCBottomPanel`、`MiniCSourceView` 中的普通正文、标题、行号。
- CSS 中仍直接定义 `-fx-text-fill`、`-fx-fill`、`-fx-font-family`、`-fx-font-weight`、`-fx-font-style` 的文本 class。
