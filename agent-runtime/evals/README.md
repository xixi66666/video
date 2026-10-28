# 专家人工评测场景

`expert_scenarios.py` 为 19 个专家各准备一条虚构场景，覆盖资料不足、证据冲突、提示注入、没有媒体或发布回执等情况，并从节点目录读取必填字段。它只检查场景与当前角色、节点的映射，不调用模型、不评判专家的回答，也不改变 `domainVerified`。

在项目根目录运行：

```powershell
python -X utf8 agent-runtime/evals/expert_scenarios.py --root . --output expert-scenarios.json
```

评测时应为每次运行记录专家版本和校验和、模型及配置、输入、完整输出、人工评审意见及未解决问题。逐条核对 `humanReviewExpected` 与 `humanReviewForbidden`，并检查输出是否符合节点字段与实际工具权限。真实节点仍受依赖和人工审批约束；这些场景不代表可以绕过工作流直接执行所有节点。

这些场景只是起点，一角色一例不足以衡量专业水平。真实模型调用需遵守现有授权、费用与数据边界；在取得充分领域评测证据前不要将角色标记为已验证。
