# CodeLlamaAI 基础架构

当前插件先落一个最小可扩展分层，避免后续把 UI、网络调用、IDE 集成全部耦合在一起。

## 分层

- `settings`
  - `CodeLlamaAISettings`
  - 负责持久化插件配置，例如 Ollama 地址、当前模型、系统提示词。

- `ollama`
  - `OllamaGateway`
  - `OllamaModelInfo`
  - 负责和本地 Ollama 通讯，隔离 HTTP 请求与 LangChain4j 调用细节。

- `chat`
  - `CodeLlamaAIChatService`
  - `ChatResult`
  - 负责项目级编排，提供异步刷新模型、发送消息的统一入口。

- `toolwindow`
  - `CodeLlamaAiToolWindowFactory`
  - `CodeLlamaAiToolWindowPanel`
  - 负责 IDEA 工具窗口 UI，与服务层交互，不直接持有网络实现。

## 当前请求链路

1. 用户在 Tool Window 中输入 Ollama 地址、模型、系统提示词和问题。
2. UI 先把配置保存到 `CodeLlamaAISettings`。
3. `CodeLlamaAIChatService` 在后台线程调用 `OllamaGateway`。
4. `OllamaGateway` 负责：
   - 调 `/api/tags` 获取本地模型列表；
   - 使用 `OllamaChatModel` 发起对话请求。
5. UI 在 EDT 上回填结果和状态。

## 这个骨架的价值

- 后续要接编辑器上下文、文件选择、代码解释、补全入口时，可以继续放在 `chat` 层做编排。
- 如果后面要替换 LangChain4j、接流式响应、做多模型路由，主要改 `ollama` 层。
- 如果以后要增加 Settings 页面、Action、Editor Popup，不需要把现有 Tool Window 推倒重来。

## 下一阶段建议

- 增加正式的 `Configurable` 设置页，而不是只在 Tool Window 配置。
- 把聊天请求改成流式输出，避免长回答期间界面无反馈。
- 引入 `ContextCollector`，从当前文件、选区、光标位置提取上下文。
- 引入 `PromptBuilder`，统一系统提示词、用户问题和代码上下文拼装。
- 给 `ollama` 层补单元测试，对模型列表解析和错误处理做回归保护。
