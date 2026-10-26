# 15 本次实现验收记录

日期：2026-10-26。环境：Windows、Node.js 24、Java 21、Maven 3.9.9、Microsoft Edge 无头浏览器。

## 已通过

| 检查 | 结果与覆盖 |
| --- | --- |
| 前端领域测试 | 9 项通过：50 节点、阶段约束、四级模型继承、选择性失效、无虚假执行、跳过限制、新项目初态、未采用草稿禁止审批、认证信息拦截 |
| TypeScript / 生产构建 | `npm run build` 通过；同步完整目录，按路由分包 |
| 浏览器端到端 | 6 项通过：本地新建与刷新、模型配置、灵感与手机布局、后端创建与保存、素材原件上传回读、服务端认证参数拦截 |
| 后端集成测试 | 3 项通过：持久化与并发冲突、依赖与阶段约束、来源检查、目录与健康状态 |
| 后端打包 | 可执行 Spring Boot JAR 构建通过 |
| 文档与目录 | 规格结构校验、节点文档同步检查通过 |
| 独立目录启停 | 启动、精确停止两个记录进程、再次启动均通过；启动时自动同步节点目录 |

浏览器测试使用独立上下文和独立测试数据库，未导入真实账号、素材或密钥。桌面 1440px、手机 390px 截图经过检查，见 `screenshots/`。

## 复现

先启动本机前后端。在 `frontend` 中运行 `npm test`、`npm run build`、`npx playwright test --config playwright.config.ts`；浏览器测试要求安装 Microsoft Edge。后端运行 `mvn test`。

浏览器中的后端测试会新增“后端联调验收作品”和测试文件，复现时请使用测试数据目录。可手工启动后端并传入 `--spring.datasource.url=jdbc:h2:file:./data/verify;MODE=MySQL;DB_CLOSE_ON_EXIT=FALSE` 和 `--workbench.files-dir=./data/verify-files`。

## 实际边界

本次验证默认 H2 文件数据库；MySQL 配置未联调。没有调用真实模型、ComfyUI、视频渲染或平台发布，也没有验证这些外部能力。0.1 是支持全部节点的管理与人工协作版本，后续接入范围见 [运行与实现状态](14-running-and-implementation.md)。
