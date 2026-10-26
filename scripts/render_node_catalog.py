# -*- coding: utf-8 -*-
"""根据节点注册表生成说明；只处理文档，不执行业务。"""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STAGES = {'FOUNDATION':'创作基础 N02—N07','IDEATION':'想法与选题 N08—N15',
          'PREPRODUCTION':'资料、脚本与分镜 N16—N24','PRODUCTION':'素材制作 N25—N30',
          'POSTPRODUCTION':'剪辑后期 N31—N37','DELIVERY':'包装与发布 N38—N44',
          'REVIEW':'反馈与复盘 N45—N48','LIBRARY':'资产沉淀 N49','BUSINESS':'经营判断 N50'}


def render():
    data = json.loads((ROOT / 'specs/nodes.json').read_text(encoding='utf-8'))
    agents = json.loads((ROOT / 'specs/agents.json').read_text(encoding='utf-8'))
    names = {a['id']:a['name'] for a in agents['agents']}
    rows = ['# 02 全部 49 个活动节点实现目录', '',
            '> 由 specs/nodes.json 生成。修改后运行 python scripts/render_node_catalog.py。', '',
            '全部为拟实现规格，implementationStatus 均为 DESIGNED。A 是条件明确时独立执行，B 是人机协作，C 是人工主导；分类不等于审批状态。', '',
            '## 公共节点合同', '',
            '- 输入绑定具体账号档案、项目、镜头、音轨或平台版本，不能只有自由文本。',
            '- 执行记录输入与模型参数快照、尝试、文件、费用、检查与来源。',
            '- 推荐能力用于选模；手工导入不要求对应模型已经接入。',
            '- 输出用 artifactType 和 payload 必需字段约束；下列文字规定字段语义与检查。',
            '- 同阶段可组装标记 DRAFT 的草稿，正式生产和发布受阶段确认限制。',
            '- 不适用须说明及替代，未接入须手工交付或阻塞，不能伪造执行成功。',
            '- 状态、拍板、局部返工和聚合见 [工作流文档](03-workflow-and-agents.md)。', '',
            '## 覆盖索引', '', '| 编号 | 节点 | 分工 | 范围 | 角色 |', '| --- | --- | --- | --- | --- |']
    for n in data['nodes']:
        rows.append('| {id} | {name} | {responsibility} | {scope} | {agent} |'.format(agent=names[n['agentRole']], **n))
    last = None
    for n in data['nodes']:
        if n['stage'] != last:
            rows.extend(['','## ' + STAGES[n['stage']],''])
            last = n['stage']
        deps = '、'.join(n['dependsOn']) or '无；从用户输入开始'
        caps = '、'.join(n['recommendedCapabilities']) or '不强制使用模型，程序或工具即可'
        fields = '、'.join('`'+f+'`' for f in n['output']['requiredFields'])
        rows.extend(['### {id} {name}'.format(**n), '',
                     '- **分工与范围**：{responsibility}；{scope}；角色：{agent}；执行方式：{executorKind}。'.format(agent=names[n['agentRole']], **n),
                     '- **适用条件**：'+n['applicability']+'。',
                     '- **依赖**：'+deps+'。',
                     '- **输入**：'+n['inputDescription']+'。',
                     '- **输出类型**：`'+n['output']['artifactType']+'`；必需 payload 字段：'+fields+'。',
                     '- **Agent/工具工作**：按该角色职责组织输入、制作候选或确定操作结果，记录证据和缺项；只执行适用范围。',
                     '- **推荐模型能力**：'+caps+'。',
                     '- **人工职责**：'+n['humanAction']+'。',
                     '- **验收**：'+n['acceptanceCriteria']+'。',
                     '- **返工**：'+n['reworkAction']+'。',
                     '- **未接入时**：'+n['manualFallback'], ''])
    rows.extend(['## 完整性与开发用法','',
                 '活动目录为 N02—N50，共 49 个节点。N01 已退役，历史数据归档保留。动态预演、风格设定、难镜样片、逐镜验收和技术检查作为子任务扩展，不另起编号替换原节点。','',
                 '节点基本信息由注册表驱动；具体编辑器按 artifactType 分派。Schema、执行器和 UI 有版本，模型无权自由修改节点定义。',''])
    return '\n'.join(rows)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    target = ROOT / 'docs/02-node-catalog.md'
    content = render()
    if args.check:
        if not target.exists() or target.read_text(encoding='utf-8') != content:
            print('FAIL: catalog missing or out of date')
            return 1
        print('PASS: catalog matches registry (49 active nodes)')
        return 0
    target.write_text(content, encoding='utf-8')
    print('Generated docs/02-node-catalog.md')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
