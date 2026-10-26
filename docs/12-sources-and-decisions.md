# 12 官方资料、设计决定与待核实项

核对日期：2026-10-26。第三方资料会变化，实施时以目标版本和账号实际能力再验证。以下链接用于支撑工具定位与协议边界，不代表已完成接入。

## 1. 官方资料

| 来源 | 支撑内容 |
| --- | --- |
| [ComfyUI Server Routes](https://docs.comfy.org/development/comfyui-server/comms_routes) | 提交、队列、历史、文件与 WebSocket 的自托管接口 |
| [Comfy 官方介绍](https://support.comfy.org/articles/9806306061-about-comfy) | 节点式生成工作流；本地、云与外部能力的区别 |
| [OpenAI Chat Completions](https://developers.openai.com/api/reference/chat-completions/overview) | 该协议的消息式接口；不能拿同一返回解析套所有协议 |
| [OpenAI Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs) | Schema 约束、JSON 模式、工具调用的区别；内容仍需校验 |
| [MyBatis Spring Boot Starter](https://github.com/mybatis/spring-boot-starter) | Spring Boot/Starter/Java 兼容组合；不能使用主分支代替发布版本 |
| [FFmpeg Filters](https://ffmpeg.org/ffmpeg-filters.html) | 声音、静音、黑场和媒体处理能力 |
| [Remotion 渲染](https://www.remotion.dev/docs/render) | 模板化程序渲染与输出；接入时另核使用许可 |
| [OBS Sources](https://obsproject.com/kb/sources-guide) | 窗口、摄像头、声音等采集源 |
| [Playwright 视频](https://playwright.dev/docs/videos) | 浏览器操作录制能力；不是任意平台发布 API |
| [DaVinci Resolve](https://www.blackmagicdesign.com/products/davinciresolve) | 人工剪辑、画面和声音后期出口 |
| [OpenTimelineIO](https://opentimelineio.readthedocs.io/en/latest/) | 时间线交换数据，不内嵌媒体，不保证所有编辑器完全兼容 |
| [Subtitle Edit](https://subtitleedit.github.io/subtitleedit/overview.html) | 字幕文本、时间和波形校对 |
| [LangGraph](https://docs.langchain.com/oss/python/langgraph/overview) | 有状态的 Agent 编排与人工介入；可选运行时 |
| [n8n](https://docs.n8n.io/) | 外部服务和业务自动化；不强制与其他引擎同时部署 |

## 2. 已作出的实现决定

| 决定 | 理由 | 后果 |
| --- | --- | --- |
| 文档先行 | 用户明确只要实现文档 | 不创建或运行应用 |
| 保留原始 50 节点 | 全局管理完整创作链 | 条件跳过有记录，不能删掉未自动化节点 |
| 四级模型覆盖 | 用户拥有多个模型 | 按能力绑定，快照记录实际选择 |
| 模型/工具/Agent 分开 | 三者职责和生命周期不同 | 便于替换，避免一个模型字段控制全部能力 |
| 模块化单体起步 | 独立副业规模，优先可维护 | 不把 19 职责拆成微服务 |
| 六个阶段拍板 | 保留方向、质量和对外承诺判断 | 自动执行止于明确边界 |
| 文件与版本依赖 | 多候选与局部返工是主要难点 | 采用关系不覆盖原件，历史可追溯 |
| 异机执行接入后置 | 工具不在当前电脑部署 | 当前只写合同，工具默认 DOCUMENTED |
| 人工交付为一等路径 | 接口与权限未必可用 | 未接入不阻断管理，不伪造调用结果 |
| 状态与预算由程序维护 | 模型可能误判或输出不规范 | Agent 无权直接改批准、费用或发布状态 |

## 3. 后续实施必须核实

用户模型清单与协议；原生/兼容能力和参数映射；目标机器硬件；ComfyUI 版本与节点；模型与素材使用许可；真实平台 API 权限；价格与汇率；实际声音/画面规范；身份认证与部署访问方式。

其他协议适配器在本次仅作枚举与边界设计；没有提供未经核实的供应商端点或声称已测试兼容。实施相应协议时补充官方来源与契约测试。

## 4. 文档内部优先级

用户后续明确要求 > PRODUCT.md 已确认要求 > 本次节点编号与规格 > 具体页面/技术建议。节点规范来源是 specs/nodes.json，修改后重新生成目录；工作流/模型/API 的契约修改要同步相关示例、校验与验收。
