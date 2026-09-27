# MiniC 多平台兼容设计

## 当前状态

MiniC 应用层使用 Java 21。当前唯一完成原生产物闭环的目标是 `windows-x86_64`：

```text
source -> frontend -> IR -> assembly view -> MachineModule
       -> x64 encoder -> COFF object -> PE32+ linker -> executable
```

Windows 后端完全由项目内 Java 代码实现，不启动外部汇编器或链接器，也不读取宿主机 SDK、静态库或工具链配置。汇编文本是教学和调试视图，不是二进制生成输入之外的外部工具协议。

## 分层目标

| 层级 | 能力 |
|------|------|
| L0 | Java 前端、IR、Debugger 和 UI 可在支持 Java 21 的 host 上运行 |
| L1 | 可以选择目标平台并生成目标汇编/机器模块，不要求在 host 上执行 |
| L2 | 项目内编码器和对象 writer 能生成目标对象文件 |
| L3 | 项目内 linker 能生成并在对应系统执行原生程序 |

当前状态：

- Windows x86-64：L3。
- Linux x86-64：未实现。
- macOS x86-64：未实现。
- macOS arm64：未实现。

## 后端边界

平台后端必须共享 IR，但不得共享平台相关文本模板、调用约定或二进制格式实现。推荐结构：

```text
minic.compiler.codegen.machine   结构化机器模块
minic.compiler.codegen.x64       x86-64 指令编码
minic.compiler.coff              Windows COFF 对象读写
minic.compiler.pe                PE32+ 链接
minic.compiler.runtime           目标运行时
```

新增平台时应增加独立实现：

- Linux x86-64：SysV ABI、ELF object、ELF linker 和 Linux runtime。
- macOS x86-64：Darwin ABI、Mach-O object、Mach-O linker。
- macOS arm64：AArch64 指令选择、编码器和 Mach-O linker。

不得通过替换汇编字符串把 Windows 后端伪装成其他平台后端。

## Windows 原生产物

Windows 后端负责：

- Windows x64 参数、返回值、shadow space 和栈对齐；
- x86-64 opcode、REX、ModRM、SIB、立即数和相对位移；
- COFF sections、symbols、string table 和 relocations；
- PE32+ headers、section layout、entry point、imports 和 IAT；
- MiniC 最小运行时以及 `printf`；
- 通过 `KERNEL32.dll` 使用稳定的 Windows 用户态 API。

链接器当前只承诺消费 MiniC 自己生成的 COFF，不承诺兼容第三方 C/C++ 对象、调试数据库、资源文件、COMDAT 或静态库。

## CLI 和流水线

CLI 默认使用目标平台的内置二进制流水线：

```text
minic compile <source.mc> --out-dir <dir> [--emit-asm]
minic compile-run <source.mc> --out-dir <dir> [--emit-asm]
```

产物包括：

```text
<name>.asm   教学展示
<name>.obj   项目内生成的 COFF
<name>.exe   项目内链接的 PE32+
```

非 Windows host 可以生成 Windows 产物，但不能直接执行 `.exe`。`compile-run` 必须在 host 与产物格式不兼容时给出明确诊断。

## 发布要求

Windows 发布包只需要：

- MiniC 应用及 Java runtime；
- 配置、文档和样例；
- UIWeb 静态资源。

发布包不得携带本机编译器、汇编器、链接器、SDK 库或 C 运行时 redistributable。打包验收必须扫描这些额外目录和旧环境变量。

## 测试矩阵

| 验证 | Windows | Linux | macOS |
|------|---------|-------|-------|
| Java 编译与单元测试 | 必跑 | 必跑 | 必跑 |
| Windows COFF/PE 结构测试 | 必跑 | 必跑 | 必跑 |
| Windows EXE 实际执行 | 必跑 | 可选交叉执行 | 可选交叉执行 |
| 当前 host UI smoke | 必跑 | 必跑 | 必跑 |

Windows 端到端测试至少覆盖整数、长整数、浮点、数组、结构体、指针、函数指针、四个以上参数、控制流、stdout、退出码和自带格式化输出。

## 后续顺序

1. 完成 Windows unwind metadata、base relocation 和 ASLR 验收。
2. 抽取与格式无关的 object/link layout 公共模型。
3. 实现 Linux x86-64 ELF 后端。
4. 实现 macOS x86-64 Mach-O 后端。
5. 独立实现 macOS arm64 后端。
