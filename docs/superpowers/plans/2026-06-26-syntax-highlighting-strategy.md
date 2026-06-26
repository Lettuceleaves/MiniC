# Syntax Highlighting Strategy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve MiniC syntax highlighting across source code, IR, assembly, and explanatory text with finer semantic roles and shared Java/Web behavior.

**Architecture:** Extend the reusable text role system with semantic code roles, then teach the source mapper to use lexer context for identifiers while keeping the old `roleFor(kind)` API compatible. Reuse the same role names in JavaFX and UIWeb, and update line highlighters so IR/ASM/explanation output uses the same taxonomy.

**Tech Stack:** Java 21, JavaFX/RichTextFX, JUnit 5 + AssertJ, TypeScript/React UIWeb, Vite/tsc.

## Global Constraints

- Keep Java and UIWeb role names aligned.
- Preserve legacy token classes such as `token-keyword`, `token-string`, and `token-operator`.
- No production code changes before failing tests.
- Use existing theme fallback keys where possible, adding syntax keys only for reusable semantic roles.

---

### Task 1: Java Highlighting Contract Tests

**Files:**
- Modify: `src/test/java/minic/uilocal/MiniCEditorViewportTextRegressionTest.java`

**Interfaces:**
- Consumes: existing `MiniCSyntaxTextStyleMapper`, `MiniCSourceTextHighlighter`, `MiniCIrTextHighlighter`, `MiniCAssemblyTextHighlighter`, `MiniCExplanationTextHighlighter`.
- Produces: test expectations for `CODE_CONTROL`, `CODE_FUNCTION`, `CODE_VARIABLE`, `CODE_REGISTER`, `CODE_LABEL`, `CODE_DIRECTIVE`, and `CODE_PUNCTUATION`.

- [ ] **Step 1: Write failing tests**

```java
@Test
void classifiesSourceTokensWithContextAwareSemanticRoles() {
    MiniCSourceTextHighlighter highlighter = new MiniCSourceTextHighlighter();

    List<MiniCStyledTextSegment> segments = highlighter.highlight("""
            int main() {
                if (main() > 0) return sizeof(int);
            }
            """);

    assertThat(segments)
            .filteredOn(segment -> segment.text().equals("int"))
            .extracting(MiniCStyledTextSegment::role)
            .containsOnly(MiniCTextStyleRole.CODE_TYPE);
    assertThat(segments)
            .filteredOn(segment -> segment.text().equals("main"))
            .extracting(MiniCStyledTextSegment::role)
            .contains(MiniCTextStyleRole.CODE_FUNCTION);
    assertThat(segments)
            .filteredOn(segment -> segment.text().equals("if") || segment.text().equals("return"))
            .extracting(MiniCStyledTextSegment::role)
            .containsOnly(MiniCTextStyleRole.CODE_CONTROL);
    assertThat(segments)
            .filteredOn(segment -> segment.text().equals("(") || segment.text().equals(")"))
            .extracting(MiniCStyledTextSegment::role)
            .containsOnly(MiniCTextStyleRole.CODE_PUNCTUATION);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat --no-daemon test --tests minic.uilocal.MiniCEditorViewportTextRegressionTest`

Expected: FAIL because the new roles do not exist or are not returned yet.

- [ ] **Step 3: Add IR/ASM/explanation expectations**

```java
@Test
void classifiesIrAssemblyAndExplanationWithDedicatedSemanticRoles() {
    assertThat(new MiniCIrTextHighlighter().highlight("entry: %1 = call @main, %arg"))
            .extracting(MiniCStyledTextSegment::role)
            .contains(MiniCTextStyleRole.CODE_LABEL, MiniCTextStyleRole.CODE_FUNCTION,
                    MiniCTextStyleRole.CODE_VARIABLE, MiniCTextStyleRole.CODE_PUNCTUATION);
    assertThat(new MiniCAssemblyTextHighlighter().highlight(".text main: mov rax, qword ptr [rbp-8] ; load"))
            .extracting(MiniCStyledTextSegment::role)
            .contains(MiniCTextStyleRole.CODE_DIRECTIVE, MiniCTextStyleRole.CODE_LABEL,
                    MiniCTextStyleRole.CODE_FUNCTION, MiniCTextStyleRole.CODE_REGISTER,
                    MiniCTextStyleRole.CODE_TYPE, MiniCTextStyleRole.CODE_COMMENT);
    assertThat(new MiniCExplanationTextHighlighter().highlight("调用 main(%1)，跳转到 .L1，寄存器 rax == 0。"))
            .extracting(MiniCStyledTextSegment::role)
            .contains(MiniCTextStyleRole.CODE_FUNCTION, MiniCTextStyleRole.CODE_VARIABLE,
                    MiniCTextStyleRole.CODE_LABEL, MiniCTextStyleRole.CODE_REGISTER,
                    MiniCTextStyleRole.CODE_OPERATOR);
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `.\gradlew.bat --no-daemon test --tests minic.uilocal.MiniCEditorViewportTextRegressionTest`

Expected: FAIL because highlighters still collapse these roles into identifier/type/operator.

### Task 2: Shared Role Set And Theme Fallbacks

**Files:**
- Modify: `src/main/java/minic/uilocal/text/MiniCTextStyleRole.java`
- Modify: `uiweb/src/text/MiniCTextStyleRole.ts`
- Modify: `config/themes/dark.json`
- Modify: `config/themes/light.json`
- Modify: `config/themes/sepia.json`
- Modify: `config/themes/solarized.json`
- Modify: `uiweb/src/resources/minic/themes/dark.json`
- Modify: `uiweb/src/resources/minic/themes/light.json`
- Modify: `uiweb/src/resources/minic/themes/sepia.json`
- Modify: `uiweb/src/resources/minic/themes/solarized.json`

**Interfaces:**
- Produces: new roles `CODE_CONTROL`, `CODE_FUNCTION`, `CODE_VARIABLE`, `CODE_REGISTER`, `CODE_LABEL`, `CODE_DIRECTIVE`, `CODE_PUNCTUATION`.

- [ ] **Step 1: Add enum and mirror roles**

Add Java enum constants after `CODE_KEYWORD` and before existing literal roles:

```java
CODE_CONTROL("code.control", "syntax.control", "mono", "normal", "normal"),
CODE_FUNCTION("code.function", "syntax.function", "mono", "normal", "normal"),
CODE_VARIABLE("code.variable", "syntax.variable", "mono", "normal", "normal"),
CODE_REGISTER("code.register", "syntax.register", "mono", "normal", "normal"),
CODE_LABEL("code.label", "syntax.label", "mono", "normal", "normal"),
CODE_DIRECTIVE("code.directive", "syntax.directive", "mono", "normal", "normal"),
CODE_PUNCTUATION("code.punctuation", "syntax.punctuation", "mono", "normal", "normal"),
```

Add matching TypeScript definitions in `MiniCTextStyleRole.ts`.

- [ ] **Step 2: Add syntax theme keys**

Each theme gets keys for `syntax.control`, `syntax.function`, `syntax.variable`, `syntax.register`, `syntax.label`, `syntax.directive`, `syntax.operator`, and `syntax.punctuation`. Existing roles continue to use `syntax.keyword`, `syntax.string`, `syntax.literal`, and `syntax.type`.

- [ ] **Step 3: Run Java test**

Run: `.\gradlew.bat --no-daemon test --tests minic.uilocal.MiniCEditorViewportTextRegressionTest`

Expected: still FAIL on mapper/highlighter behavior, but enum/theme references compile.

### Task 3: Source Token Context Strategy

**Files:**
- Modify: `src/main/java/minic/uilocal/text/MiniCSyntaxTextStyleMapper.java`
- Modify: `src/main/java/minic/uilocal/text/MiniCSourceTextHighlighter.java`
- Modify: `src/main/java/minic/uilocal/editor/MiniCCodeEditor.java`
- Modify: `src/main/java/minic/uilocal/panel/MiniCBottomPanel.java`
- Modify: `uiweb/src/text/MiniCSyntaxTextStyleMapper.ts`
- Modify: `uiweb/src/text/MiniCSourceTextHighlighter.ts`
- Modify: `uiweb/src/editor/MiniCCodeEditor.tsx`
- Modify: `uiweb/src/panel/MiniCBottomPanel.tsx`

**Interfaces:**
- Produces: `roleForToken(source, tokens, index)` and `styleClassesForToken(source, tokens, index, diagnostic)` in Java and Web.
- Consumes: sorted lexer tokens with `kind`, `text`, `startOffset`, and `endOffset`.

- [ ] **Step 1: Implement context API**

Rules:
- Type tokens `BOOL`, `CHAR`, `INT`, `LONG`, `FLOAT`, `DOUBLE`, `STRUCT` -> `CODE_TYPE`.
- Control tokens `RETURN`, `IF`, `ELSE`, `WHILE`, `DO`, `FOR`, `BREAK`, `CONTINUE`, `SWITCH`, `CASE`, `DEFAULT` -> `CODE_CONTROL`.
- `SIZEOF` -> `CODE_KEYWORD`.
- Identifier followed by `LEFT_PAREN` -> `CODE_FUNCTION`.
- Other identifier -> `CODE_VARIABLE`.
- Delimiters `LEFT_PAREN`, `RIGHT_PAREN`, `LEFT_BRACE`, `RIGHT_BRACE`, `LEFT_BRACKET`, `RIGHT_BRACKET`, `SEMICOLON`, `COMMA`, `DOT`, `ELLIPSIS`, `QUESTION`, `COLON` -> `CODE_PUNCTUATION`.
- Other operators -> `CODE_OPERATOR`.

- [ ] **Step 2: Wire Java editor and source snippets**

Replace per-token `styleClassesFor(kind, diagnostic)` calls with `styleClassesForToken(source, tokens, index, diagnostic)`.

- [ ] **Step 3: Wire Web editor and source snippets**

Preserve `EditorToken` text and call the TypeScript context API from render segmentation.

- [ ] **Step 4: Run source test**

Run: `.\gradlew.bat --no-daemon test --tests minic.uilocal.MiniCEditorViewportTextRegressionTest`

Expected: source role assertions pass; IR/ASM/explanation may still fail until Task 4.

### Task 4: IR, ASM, And Explanation Highlighters

**Files:**
- Modify: `src/main/java/minic/uilocal/text/MiniCLineTokenHighlighter.java`
- Modify: `src/main/java/minic/uilocal/text/MiniCIrTextHighlighter.java`
- Modify: `src/main/java/minic/uilocal/text/MiniCAssemblyTextHighlighter.java`
- Modify: `src/main/java/minic/uilocal/text/MiniCExplanationTextHighlighter.java`
- Modify: `uiweb/src/text/MiniCLineTokenHighlighter.ts`
- Modify: `uiweb/src/text/MiniCIrTextHighlighter.ts`
- Modify: `uiweb/src/text/MiniCAssemblyTextHighlighter.ts`
- Modify: `uiweb/src/text/MiniCExplanationTextHighlighter.ts`

**Interfaces:**
- Consumes: role constants from Task 2.
- Produces: finer line-token classification for IR, ASM, and explanatory prose.

- [ ] **Step 1: Improve token splitting**

Ensure `@main`, `%1`, `.L1`, `qword`, punctuation, and operators are distinct enough for role classification.

- [ ] **Step 2: Implement IR roles**

Rules:
- `function`, `block`, `declare` -> `CODE_DIRECTIVE`.
- IR operation names like `load`, `store`, `call`, `return`, `branch`, arithmetic/comparison names -> `CODE_KEYWORD`.
- labels followed by `:` or starting with `.`/`$` -> `CODE_LABEL`.
- `@name` after `call` or call target -> `CODE_FUNCTION`.
- `%...`, `&...`, regular variables -> `CODE_VARIABLE`.
- punctuation -> `CODE_PUNCTUATION`.

- [ ] **Step 3: Implement ASM roles**

Rules:
- section/directive names and declarations -> `CODE_DIRECTIVE`.
- mnemonics -> `CODE_FUNCTION`.
- registers -> `CODE_REGISTER`.
- labels -> `CODE_LABEL`.
- pointer width words -> `CODE_TYPE`.
- punctuation -> `CODE_PUNCTUATION`.
- `;` comments -> `CODE_COMMENT`.

- [ ] **Step 4: Implement explanation roles**

Reuse the same classification names for embedded source/IR/ASM fragments while preserving natural-language words as `BODY`.

- [ ] **Step 5: Run focused Java tests**

Run: `.\gradlew.bat --no-daemon test --tests minic.uilocal.MiniCEditorViewportTextRegressionTest`

Expected: PASS.

### Task 5: Web Typecheck And Regression Verification

**Files:**
- Modify only files already listed above.

**Interfaces:**
- Consumes: Java/Web mirrored role and mapper changes.
- Produces: verified build output.

- [ ] **Step 1: Run Java focused tests**

Run: `.\gradlew.bat --no-daemon test --tests minic.uilocal.MiniCEditorViewportTextRegressionTest --tests minic.uilocal.MiniCInfoViewRegressionTest --tests minic.uilocal.MiniCSettingsInteractionRegressionTest --tests minic.MiniCUtilityRegressionTest`

Expected: PASS.

- [ ] **Step 2: Run UIWeb typecheck**

Run: `npm --prefix uiweb run typecheck`

Expected: PASS.

- [ ] **Step 3: Inspect diff**

Run: `git diff --stat`

Expected: only highlighting roles, mappers, highlighters, themes, tests, and this plan document changed.
