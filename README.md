<p align="center">
  <img src="docs/craken-logo.png" alt="Craken" width="128">
</p>

# Craken

Craken 是一个面向编译原理与程序运行机制学习的 C 语言子集可视化工作台。它把代码编辑、编译过程观察、运行调试和数据结构视图整合在 JavaFX Local Visual Workbench 中：编译可视化流水线覆盖预处理、词法、语法、语义、IR、汇编、Windows x64 编码、COFF、PE 链接直到运行；可视化 Debugger 以 IR Interpreter 为执行核心，记录调用栈、栈／堆内存与输入输出，并支持在已有历史中正向或反向查看。

## 功能概览

- **编辑器**：实时语法纠错，随输入给出关键词、符号与头文件补全。
- **编译 Pipeline 展示台**：逐阶段、逐步冻结展示源码、token、AST、语义注解、IR、汇编与最终产物。
- **可视化容器**：宿主无关的快照模型与布局协议，内置数组、桶链、树与图布局，支持自定义页类型。
- **调试工作台**：半屏布局；按变量插桩捕获访问，在停止点展开数组、结构体与指针并高亮读写。
- **运行与用例**：一键编译运行当前缓冲区；用例标签预置输入与预期输出并自动判定。
- **内置终端**：JediTerm + ConPTY 的 PowerShell 会话。
- **STL 兼容层**：`lib/stl/*.mh` 提供算法题常用的容器与算法接口，范围见文档。

## 快速开始

需要 JDK 21；JavaFX 21.0.2 与 Gradle Wrapper 8.7 由构建解析。

```powershell
./craken.bat                                    # 开发模式启动 UI（gradlew runUi）
./gradlew test                                  # 单元与回归测试
./scripts/verify-visualization.ps1 -Stage C28   # 可视化累计验收（首次需准备 Graphviz，见文档）
```

## 依赖版本

| 依赖 | 版本 |
|------|------|
| Java Toolchain | 21 |
| JavaFX | 21.0.2 |
| OpenJFX Gradle Plugin | 0.1.0 |
| RichTextFX | 0.11.7 |
| JUnit Jupiter / Platform Console | 5.11.4 / 1.11.4 |
| Gradle Wrapper | 8.7 |
| Graphviz（Windows x64 完整运行时） | 16.1.0 |

## 文档

使用与开发指南：

- [使用说明](docs/使用说明.md)：内置终端、编辑器补全、运行与用例标签、Pipeline 展示台、调试工作台、通用弹窗。
- [开发者指南](docs/开发者指南.md)：可视化容器 API、Debugger 结构适配、构建与验收流程。
- [STL 兼容与范围](docs/STL兼容与范围.md)：`lib/stl` 的支持范围、本轮补齐项与已知限制。

设计文档：

- 可视化容器：[技术方案](docs/可视化容器技术方案.md)、[布局方案](docs/可视化布局方案.md)、[实施记录](docs/可视化容器实施记录.md)、[性能基准](docs/可视化性能基准.md)
- 图与样式：[图可视化算法选型](docs/图可视化算法选型.md)、[配色设计规范](docs/Craken-可视化组件配色设计规范-v1.md)
- 编辑器与调试器：[编辑器补全方案](docs/编辑器补全方案.md)、[编辑器实时纠错方案](docs/编辑器实时纠错方案.md)、[调试器可视化方案](docs/调试器可视化方案.md)
