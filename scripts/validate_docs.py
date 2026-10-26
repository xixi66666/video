# -*- coding: utf-8 -*-
"""离线文档与设计规格检查；只用标准库，不调用 API。"""
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
ERRORS = []


def check(condition, message):
    if not condition:
        ERRORS.append(message)


def load(relative):
    try:
        return json.loads((ROOT / relative).read_text(encoding='utf-8'))
    except (OSError, ValueError) as exc:
        ERRORS.append('{}: {}'.format(relative, exc))
        return {}


def acyclic(graph):
    visiting, done = set(), set()
    def visit(key):
        if key in visiting:
            return False
        if key in done:
            return True
        visiting.add(key)
        if any(not visit(dep) for dep in graph.get(key, []) if dep in graph):
            return False
        visiting.remove(key)
        done.add(key)
        return True
    return all(visit(key) for key in graph)


def main():
    for folder in ['specs','examples']:
        for p in (ROOT/folder).rglob('*.json'):
            load(str(p.relative_to(ROOT)))
    registry = load('specs/nodes.json')
    nodes = registry.get('nodes', [])
    ids = [n.get('id') for n in nodes]
    expected = ['N{:02d}'.format(i) for i in range(2,51)]
    check(ids == expected and registry.get('nodeCount') == 49, 'exactly active N02..N50 required')
    check(len(set(n.get('name') for n in nodes)) == 49, '49 unique names required')
    node_map = {n.get('id'):n for n in nodes}
    agents = load('specs/agents.json').get('agents', [])
    agent_ids = {a.get('id') for a in agents}
    caps = load('specs/capabilities.json').get('capabilities', [])
    cap_ids = {c.get('id') for c in caps}
    check(len(agent_ids) == len(agents) == 19, '19 unique agent roles required')
    check(len(cap_ids) == len(caps) == 25, '25 unique capability groups required')
    required = ['id','ordinal','name','stage','responsibility','scope','dependsOn','agentRole',
                'recommendedCapabilities','executorKind','applicability','inputDescription',
                'output','acceptanceCriteria','humanAction','reworkAction','manualFallback','implementationStatus']
    for n in nodes:
        key = n.get('id','?')
        check(all(k in n for k in required), key + ': incomplete contract')
        check(n.get('agentRole') in agent_ids, key + ': unknown agent')
        check(set(n.get('recommendedCapabilities',[])) <= cap_ids, key + ': unknown capability')
        check(n.get('responsibility') in ['A','B','C'], key + ': bad responsibility')
        check(set(n.get('dependsOn',[])) <= set(ids), key + ': unknown dependency')
        check(n.get('implementationStatus') == 'DESIGNED', key + ': false implementation status')
        check(bool(n.get('output',{}).get('requiredFields')), key + ': missing output fields')
    graph = {n['id']:list(n.get('dependsOn',[])) for n in nodes}
    check(acyclic(graph), 'node dependency cycle')
    gates = load('specs/gates.json').get('gates',[])
    check([g.get('id') for g in gates] == ['G01','G02','G03','G04','G05','G06'], 'six ordered gates required')
    for g in gates:
        refs = g.get('after',[]) + g.get('watches',[]) + g.get('blocks',[])
        check(set(refs) <= set(ids), 'unknown gate node')
        check(g.get('actor') == 'HUMAN' and g.get('versionBound') is True, 'gate must bind human and version')
        graph[g['id']] = g.get('after',[])
        for key in g.get('blocks',[]):
            graph.setdefault(key,[]).append(g['id'])
    check(acyclic(graph), 'gate dependency cycle')
    check(any(g.get('id') == 'G05' and 'N43' in g.get('blocks',[]) for g in gates), 'publish must require G05')
    templates = load('specs/workflow-templates.json').get('templates',[])
    check(len(templates) == 3, 'three production templates required')
    for t in templates:
        check(t.get('nodeIds') == expected, t.get('id','?') + ': all nodes must remain visible')
        check(set(t.get('overrides',{})) <= set(ids), 'unknown template node')

    config = load('examples/model-config.example.json')
    providers = {p.get('id'):p for p in config.get('providers',[])}
    models = {m.get('id'):m for m in config.get('models',[])}
    presets = {p.get('id'):p for p in config.get('presets',[])}
    for p in providers.values():
        check(p.get('enabled') is False, 'sample provider must be disabled')
        check('.invalid' in p.get('baseUrl',''), 'sample endpoint must be non-operational')
        check(p.get('credentialRef','').startswith('secret://'), 'credentialRef required')
    for m in models.values():
        check(m.get('providerId') in providers, 'unknown provider')
        check(set(m.get('capabilities',[])) <= cap_ids, 'unknown model capability')
        check(not m.get('verifiedCapabilities'), 'sample capabilities are not tested')
        check(m.get('enabled') is False, 'sample model must be disabled')
    for p in presets.values():
        check(p.get('modelId') in models, 'unknown preset model')
    routing = config.get('routing',{})
    def verify_binding(cap, binding):
        check(cap in cap_ids, 'unknown binding capability')
        check(binding.get('modelId') in models, 'unknown binding model')
        if binding.get('presetId'):
            preset = presets.get(binding['presetId'],{})
            check(preset.get('modelId') == binding.get('modelId'), 'binding preset/model mismatch')
    for cap, binding in routing.get('defaults',{}).items():
        verify_binding(cap,binding)
    for agent, bindings in routing.get('agentDefaults',{}).items():
        check(agent in agent_ids, 'unknown role binding')
        for cap, binding in bindings.items():
            verify_binding(cap,binding)
    fallbacks = routing.get('fallbacks',{})
    for key, targets in fallbacks.items():
        check(key in models and all(x in models for x in targets), 'unknown fallback model')
    check(acyclic(fallbacks), 'fallback cycle')
    check(config.get('execution',{}).get('networkEnabled') is False, 'sample network must remain off')
    project = load('examples/project.example.json')
    check(project.get('workflowTemplateId') in {t['id'] for t in templates}, 'unknown template')
    for key, bindings in project.get('nodeOverrides',{}).items():
        check(key in node_map, 'unknown override node')
        for cap, binding in bindings.items():
            check(cap in cap_ids and binding.get('modelId') in models, 'invalid override binding')
            if binding.get('presetId'):
                check(binding['presetId'] in presets, 'unknown override preset')
    sample = load('examples/node-result.example.json')
    definition = node_map.get(sample.get('nodeId'),{}).get('output',{})
    artifact = sample.get('artifact',{})
    check(artifact.get('type') == definition.get('artifactType'), 'output artifact type mismatch')
    check(set(definition.get('requiredFields',[])) <= set(artifact.get('payload',{})), 'required output field missing')
    failure = load('examples/node-failure.example.json')
    check(failure.get('status') == 'FAILED' and 'artifact' not in failure, 'failure must not fabricate successful artifact')
    check(bool(failure.get('error',{}).get('code')), 'failure requires error code')
    tool = load('examples/comfyui-registration.example.json')
    check(tool.get('implementationStatus') == 'DOCUMENTED' and tool.get('enabled') is False, 'tool must remain unimplemented')
    check(not tool.get('capabilitiesVerified'), 'tool sample cannot claim verification')

    # 仅检查 Schema 文档基本完整性；并未执行完整 JSON Schema 标准验证。
    schemas = list((ROOT/'specs/schemas').glob('*.json'))
    check(len(schemas) >= 3, 'three design schemas required')
    for p in schemas:
        schema = load(str(p.relative_to(ROOT)))
        check('$schema' in schema and 'type' in schema, 'incomplete schema: '+p.name)
    result_schema = load('specs/schemas/node-result.schema.json')
    check('artifact' not in result_schema.get('required',[]), 'failure envelope cannot require artifact')
    check(len(result_schema.get('allOf',[])) == 51, 'success/failure and active-node result mapping required')
    for md in list(ROOT.glob('*.md'))+list((ROOT/'docs').rglob('*.md')):
        text = md.read_text(encoding='utf-8')
        check('\ufffd' not in text, 'replacement character: '+md.name)
        for target in re.findall(r'\]\(([^)]+)\)',text):
            target = target.strip('<>').split('#')[0]
            if target and '://' not in target:
                check((md.parent/target).exists(), 'broken link: '+md.name+' -> '+target)
    catalog = ROOT/'docs/02-node-catalog.md'
    if catalog.exists():
        check(re.findall(r'^### (N\d{2}) ',catalog.read_text(encoding='utf-8'),re.MULTILINE) == expected, 'catalog must list 49 active nodes once')
    else:
        check(False,'catalog missing')
    if ERRORS:
        for error in ERRORS:
            print('FAIL: '+error)
        return 1
    print('PASS: 49 active nodes, 19 roles, 25 capability groups, 6 gates, 3 complete templates')
    print('PASS: dependency DAGs, references, offline examples, JSON and local links')
    print('NOTE: no application or external integration tests were performed')
    return 0


if __name__ == '__main__':
    sys.exit(main())
