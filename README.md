# CodeLlamaAI

CodeLlamaAI 是一个基于 IntelliJ IDEA 插件的本地 AI 编程助手项目，目标是接入本地 Ollama 模型，在 IDE 内提供代码问答与辅助能力。

当前项目还处于基础架构建设阶段，已经具备最小可扩展骨架，包括插件入口、Tool Window、配置持久化、Ollama 模型列表读取和基础对话链路。

## 当前能力

- 在 IDEA 中注册 `CodeLlamaAI` 工具窗口
- 配置本地 Ollama 地址
- 读取本地已安装模型列表
- 选择模型并发送简单对话请求
- 保存基础配置，例如 `baseUrl`、模型名、系统提示词

## 项目结构

```text
src/main/java/com/xiaorui/codellamaai
├── chat         # 项目级聊天编排
├── ollama       # Ollama 接入与模型查询
├── settings     # 插件持久化配置
├── toolwindow   # IDEA 工具窗口 UI
└── CodeLlamaAiStartup.java
```

补充架构说明见 [docs/architecture.md](docs/architecture.md)。

## 开发环境

- JDK 21
- IntelliJ IDEA 2025.2.x
- Gradle Wrapper
- 本地 Ollama 服务

默认 Ollama 地址：

```text
http://localhost:11434
```

## 本地开发

编译：

```powershell
$env:GRADLE_USER_HOME="$PWD\.gradle-home"
.\gradlew.bat compileJava testClasses
```

启动插件调试实例：

```powershell
$env:GRADLE_USER_HOME="$PWD\.gradle-home"
.\gradlew.bat runIde
```

如果本地网络较慢，首次执行时 Gradle 会下载依赖和 IntelliJ Platform 相关组件。

## 测试

项目里目前有一个面向本地 Ollama 的基础联通性测试：

```powershell
$env:GRADLE_USER_HOME="$PWD\.gradle-home"
.\gradlew.bat test
```

测试依赖本地 Ollama 服务可访问，并且本机至少存在一个可用模型。

可选环境变量：

- `OLLAMA_BASE_URL`
- `OLLAMA_MODEL`

## 当前限制

- 还没有正式的 Settings 页面，当前配置主要放在 Tool Window 中
- 还不支持流式输出
- 还没有接入编辑器上下文、选区上下文和代码操作能力
- 还没有形成完整的命令、Action、补全、代码解释等功能

## 后续规划

- 增加正式的插件设置页
- 支持流式聊天响应
- 接入当前文件、选区、光标位置等 IDE 上下文
- 设计统一的 PromptBuilder 和上下文收集器
- 扩展为代码解释、重构建议、生成与修改辅助

## 说明

这是一个持续迭代中的插件项目，README 会随着架构和功能完善逐步更新。
