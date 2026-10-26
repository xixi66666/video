# 06 数据模型与接口合同

所有表、字段和接口是开发规格；当前没有数据库迁移或 API 服务。ID 建议使用应用生成的稳定字符串；日期 UTC；中文使用 utf8mb4；金额使用 DECIMAL，禁止浮点账目。

## 1. 实体与关键字段

| 实体/建议表 | 关键字段与约束 |
| --- | --- |
| creator_profile / profile_revision | owner_user_id、平台账号引用、定位/栏目/规范产物版本；历史档案不可变 |
| idea | raw_content_ref、来源、状态、context_revision、greenlight_decision_ref |
| project / project_iteration | title、template_version、profile_revision_id、automation_mode、timezone、budget_policy_id、状态、lock_version |
| workflow_definition / node_definition | version、node_id、scope、input/output schema、depends_on、policy；已发布版本不可变 |
| context_binding | iteration_id、scope_key、artifact_revision_id、source_scope；跨账号/灵感上下文引用 |
| node_instance | iteration_id、node_id、scope_key、state、input_hash、output_revision_id、blocked_reasons、lock_version |
| shot / audio_track / platform_variant | 项目迭代、稳定镜头/音轨 ID、时间信息、平台与账号、当前采用引用 |
| asset / file_object | storage_key、sha256、size、media_type、metadata、原件/代理标记；文件引用不能指向任意本机路径 |
| artifact / artifact_revision | type、revision、scope_key、payload、schema_version、status、provenance、created_by |
| artifact_dependency | upstream_revision_id、downstream_revision_id、usage_segment、影响类型；反向查询有索引 |
| asset_selection | shot_or_track_id、candidate_revision_id、selected_by、选用原因；选用不覆盖文件 |
| source_record / rights_record | URL/文档引用、作者、日期、引用位置、授权证据、使用范围、有效期和审查状态 |
| task / task_attempt | idempotency_key、executor、snapshot_id、state、external_job_id、lease_owner、lease_expires_at、错误分类 |
| task_event / outbox_event | task_id、event_id、event_type、payload、时间、投递状态；可重放、去重 |
| approval_request / approval_decision | gate_id、scope_key、snapshot_hash、版本集合、目标、授权边界、actor、decision、失效原因 |
| provider / credential_reference | 协议、地址、认证引用、状态；真实凭据与普通业务字段隔离 |
| model / model_capability / capability_test | remote_model_id、声明与实测能力、限制、适配器版本、测试日期与证据 |
| model_preset / model_binding | preset_revision、scope_type、scope_id、capability_id、model_id、参数覆盖 |
| run_snapshot | 解析后的提供方、模型、适配器、参数、价格、policy、输入版本；不包含明文密钥 |
| tool_registration / tool_template / environment | 状态 DOCUMENTED/CONFIGURED/VERIFIED/ENABLED、远端环境、模板版本、声明与验证能力 |
| budget_account / budget_reservation | limit、spent、reserved、currency、expires_at、task_id；事务更新 |
| usage_record / income_record / time_record | 原始数量与单位、价格版本、费用来源、收入、工时；估计和真实值分开 |
| review_issue | artifact_revision_id、时间点/区间、类别、严重程度、状态、解决版本 |
| release_package / publish_record | 平台、账号、确定版本清单、manifest_hash、授权引用、提交/审核/可见状态、作品链接 |
| comment_import / metric_snapshot | source、platform、account、remote_id、时间窗、口径、原始文件引用、缺失状态 |
| retrospective / business_period | hypotheses、改进决定、经营周期与决策产物引用 |
| audit_log | actor、action、resource、前后版本摘要、request_id；脱敏，不保存隐藏推理和密钥 |

## 2. 必须的唯一约束和索引

- node_instance：unique(iteration_id, definition_version, node_id, scope_key)。
- artifact_revision：unique(artifact_id, revision)。
- model_binding：unique(scope_type, scope_id, capability_id)，节点 scope_id 包含具体实例，不只是 N22。
- task：unique(idempotency_key)；state + next_run_at；lease_expires_at。
- task_attempt：unique(task_id, attempt_no)；executor_environment_id + external_job_id。
- approval_decision：request_id + snapshot_hash；服务器只对当前快照接受一次有效决定。
- platform_variant：unique(iteration_id, platform, account_id, variant_revision)。
- metric_snapshot：platform + account_id + work_id + observed_at + metric_definition_version；不能将不同时间窗互相覆盖。
- file_object：sha256 用于重复发现；是否共用实际文件还需考虑 owner、权限和删除引用。

## 3. 事务与并发

节点推进、任务创建、预算预留和 outbox 同一事务；网络调用在事务外。提交人工决定需匹配 expected_revision/If-Match 和 snapshot_hash；过期返回 409。费用结算以 task_attempt 和 usage 原始凭证去重。任何取消、重试、发布都验证归属与当前状态。

## 4. API 约定

前缀 `/api/v1`；JSON UTF-8；后端认证和资源归属检查；敏感设置接口不能对匿名用户开放。响应统一为 `{code,message,data,requestId}`，分页 `{items,nextCursor,total?}`。长任务返回 HTTP 202 和 taskId，业务失败不能全部返回 HTTP 200。

修改接口支持 `If-Match` 或 expectedRevision；付费/上传/发布命令要求 `Idempotency-Key`。GET 不产生计费生成任务。以下是本产品拟实现接口，不是外部服务商接口。

| 方法与路径 | 参数/请求体 | 返回与关键行为 |
| --- | --- | --- |
| GET /node-definitions | version、stage | 50 节点目录与版本 |
| GET /workflow-templates | 无 | 模板、50 节点及条件覆盖 |
| POST /profiles | 定位基础信息 | profileId、revision |
| POST /ideas | text 或 uploadedAudioRef、来源 | ideaId；转写另建任务 |
| POST /ideas/{id}/greenlight | decision、budget、expectedRevision | G02 决策与 projectId；暂存不创建生产任务 |
| POST /projects | ideaId?、templateId、profileRevision、platforms、automationMode | projectId、iterationId、节点实例 |
| GET /projects/{id}/workspace | iterationId | 节点、待办、阻塞、费用和当前版本 |
| PATCH /projects/{id} | 允许字段、expectedRevision | 新项目版本，必要时生成影响预览 |
| POST /projects/{id}/automation/start | 范围、预算/任务数/时限 | 202，调度会话 ID |
| POST /projects/{id}/automation/pause | runningTaskPolicy | 停止新派发、已运行任务处理状态 |
| GET /nodes/{instanceId}/context | 无 | 输入绑定、产物、模型解析、确认信息 |
| POST /nodes/{instanceId}/plan | scope、dryRun=true | 不执行的任务计划、缺项与成本估算 |
| POST /nodes/{instanceId}/runs | inputRevisionHash、bindingRevision、模式 | 202 taskId；再次校验前提 |
| POST /nodes/{instanceId}/manual-results | 产物信封、文件引用、来源 | 新产物及检查结果；不伪造模型执行 |
| POST /nodes/{instanceId}/skip | reason、alternativeRefs、expectedRevision | 跳过记录；下游输入仍需校验 |
| POST /artifacts/{id}/revisions | payload、fileRefs、expectedRevision | 新版本，旧版本保留 |
| POST /artifacts/{id}/impact-preview | candidateRevision | 受影响节点、确认、费用；无执行 |
| POST /selections | scopeKey、candidateRevisionId | 采用记录和影响结果 |
| POST /uploads/init | 文件名、大小、类型、checksum? | 受限上传会话，不接受任意服务端路径 |
| POST /uploads/{id}/complete | checksum、parts? | 校验后的 fileRef |
| POST /approvals/{id}/decisions | snapshotHash、decision、target、expectedRevision | 通过/驳回/暂存；过期 409 |
| GET /providers / POST /providers | 配置草稿，credentialRef | 不返回密钥；保存不自动测试 |
| GET /models / POST /models | 能力过滤或模型档案 | 模型、验证状态与限制 |
| POST /models/{id}/tests | 测试能力、样例、budgetLimit | 202，可能付费，记录测试结果 |
| POST /bindings/resolve | 节点实例、capability、候选覆盖 | 模型、预设、参数及继承来源，无调用 |
| PUT /bindings/{scopeType}/{scopeId} | 每能力的模型/预设/覆盖 | 保存新绑定版本 |
| GET /tasks/{id} | 无 | 状态、尝试、外部凭证、费用和问题 |
| POST /tasks/{id}/cancel | reason | 请求已接收，不承诺远端已停 |
| POST /tasks/{id}/retry | 修订参数?、expectedRevision | 检查 UNKNOWN 对账和预算后创建尝试 |
| GET /events | projectId、Last-Event-ID | SSE；重连补事件 |
| GET /tools / POST /tools | 仅登记配置 | DOCUMENTED 等状态，不假装已联通 |
| POST /tools/{id}/tests | 后续连接器测试计划 | 未实现返回 501 CONNECTOR_NOT_IMPLEMENTED |
| POST /release-packages | platform、account、版本集合 | 不可变 manifest、hash |
| POST /release-packages/{id}/publish | approvalId、manifestHash、schedule? | 后续有连接器才 202；否则手工交付入口 |
| POST /publications/manual-import | 平台、链接、状态、检查时间和凭证 | 真实人工发布记录 |
| POST /feedback/import / POST /metrics/import | 文件引用、来源、字段映射、时间窗 | 导入任务、逐行错误和结果 |
| GET /projects/{id}/retrospective | 窗口、基准 | 复盘资料和假设，不自动认定因果 |
| POST /config-exports / POST /config-imports | 脱敏范围或包文件 | 预检查报告，地址/凭据重新映射 |

## 5. 请求与异步示例

```json
{
  "inputRevisionHash": "sha256-of-real-input-snapshot",
  "bindingRevision": 3,
  "mode": "EXECUTE",
  "budgetAuthorizationId": "budget-authorization-id"
}
```

```json
{
  "code": "ACCEPTED",
  "message": "已加入执行队列",
  "data": {"taskId": "task-id", "state": "QUEUED"},
  "requestId": "request-id"
}
```

输入哈希不匹配时不自动消费新版。返回 409 INPUT_REVISION_CHANGED 及差异摘要，请求方重新预览。服务端不能信任前端提交的估算费用或“已批准”布尔值。

执行结果的信封参见 `specs/schemas/node-result.schema.json`：SUCCEEDED 必须带对应节点类型的 artifact；FAILED/UNKNOWN 必须带 error，不强制伪造产物。成功示例与失败示例分别在 examples/node-result.example.json 和 examples/node-failure.example.json。接口拒绝尚未创建任务时直接返回 HTTP 错误，不需要先建立一次失败执行。

## 6. 错误分类

400 PARAMETER_INVALID；401 AUTH_REQUIRED；403 RESOURCE_FORBIDDEN；409 VERSION_CONFLICT/APPROVAL_EXPIRED/STATE_CONFLICT；413 INPUT_TOO_LARGE；422 CAPABILITY_MISMATCH/OUTPUT_SCHEMA_INVALID；429 RATE_LIMITED；501 CONNECTOR_NOT_IMPLEMENTED；502 PROVIDER_ERROR；503 EXECUTOR_UNAVAILABLE。

预算不足返回 409 BUDGET_EXCEEDED；供应商身份错误作为任务 PROVIDER_AUTH_ERROR，不混同为当前用户未登录。错误体包含 retryable、blockedReason、可读定位，原始供应商响应先脱敏。

## 7. 事件类型

NODE_STATE_CHANGED、TASK_PROGRESS、ARTIFACT_CREATED、REVIEW_REQUIRED、APPROVAL_EXPIRED、BUDGET_CHANGED、EXECUTOR_DISCONNECTED、PUBLICATION_STATUS_CHANGED。事件 ID 用于去重；节点总进度只基于可证实的子任务，不把一个生成节点的百分比当整条视频进度。
