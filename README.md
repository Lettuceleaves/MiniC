# MiniC

## 项目介绍

MiniC 是一个面向编译原理与程序运行机制学习的 C 语言子集可视化工作台。它把代码编辑、编译过程观察、运行调试和数据结构视图整合在 JavaFX Local Visual Workbench 中，帮助学习者从源代码出发，逐步理解程序如何被分析、转换、生成和执行。

编译可视化流水线贯穿预处理、词法分析、语法分析、语义分析、IR lowering、汇编生成、Windows x64 指令编码、COFF 对象文件写入、PE32+ 链接和程序运行。各阶段通过结构化状态与单步控制连接源码、token、AST、作用域与符号、IR、汇编及最终可执行产物。

可视化 Debugger 以 IR Interpreter 为执行核心，在初始化时对 IR 插入源码行与函数调用 trap。调试上下文记录调用栈、栈内存、堆内存和输入输出，并支持在已有执行历史中正向或反向查看。JavaFX Workbench 直接使用编译会话和进程内调试 API 展示这些状态。

## 依赖版本

| 依赖 | 版本 |
|------|------|
| Java Toolchain | 21 |
| JavaFX | 21.0.2 |
| OpenJFX Gradle Plugin | 0.1.0 |
| RichTextFX | 0.11.7 |
| JUnit Jupiter / Platform Console | 5.11.4 / 1.11.4 |
| Gradle Wrapper | 8.7 |
