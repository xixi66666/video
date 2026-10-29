import json
import time
import pytest
from pathlib import Path
from fastapi.testclient import TestClient
from app.main import create_app

ROOT = Path(__file__).resolve().parents[2]
TOKEN = "isolated-fixture-test-token"


def request(run="run_fixture_0001"):
    nodes = json.loads((ROOT / "specs/nodes.json").read_text(encoding="utf-8"))["nodes"]
    return {"id": run, "projectId": "test", "mode": "FIXTURE", "node": next(n for n in nodes if n["id"] == "N08"),
        "input": {"brief": "测试创作想法", "dependencies": {}}, "models": {}, "inputFingerprint": "test",
        "executionMode": "GENERATE_TEXT", "limits": {"maxModelCalls": 30, "maxTotalTokens": 10000,
        "maxOutputTokens": 1200, "maxSeconds": 60, "maxReworks": 1}}


def wait(client, run, status):
    for _ in range(200):
        body = client.get(f"/runs/{run}").json()
        if body["status"] in status:
            return body
        time.sleep(.05)
    raise AssertionError(body)


def test_real_delegation_review_rework_and_resume(tmp_path):
    app = create_app(tmp_path, TOKEN)
    with TestClient(app, headers={"X-Creator-Token": TOKEN}) as c:
        body = request()
        response = c.post("/runs", json=body)
        assert response.status_code == 202, response.text
        run = wait(c, body["id"], {"WAITING_ACCEPTANCE", "FAILED", "UNKNOWN"})
        assert run["status"] == "WAITING_ACCEPTANCE", run
        events = c.get(f"/runs/{body['id']}/events").json()
        assert any(e["kind"] == "TOOL_STARTED" and e["data"]["tool"] == "task" for e in events)
        assert any(e["role"] == "TOPIC_RESEARCH" and e["kind"] == "AGENT_STARTED" for e in events)
        assert [e["data"]["verdict"] for e in events if e["kind"] == "REVIEW_FINISHED"] == ["REVISE", "PASS"]
        assert len([a for a in run["artifacts"] if a["kind"] == "CANDIDATE"]) == 2
        assert c.post("/runs", json=body).status_code == 202  # 幂等重投，不重复执行
        before = len(events)
        assert len(c.get(f"/runs/{body['id']}/events").json()) == before
        assert c.post(f"/runs/{body['id']}/decision", json={"decision": "ACCEPT"}).status_code == 200
        assert wait(c, body["id"], {"ACCEPTED", "FAILED"})["status"] == "ACCEPTED"
        assert c.post(f"/runs/{body['id']}/decision", json={"decision": "ACCEPT"}).status_code == 200


def test_auth_and_live_disabled(tmp_path, monkeypatch):
    monkeypatch.setenv("CREATOR_ENABLE_LIVE_MODELS", "false")
    with TestClient(create_app(tmp_path, TOKEN)) as c:
        assert c.get("/health").status_code == 401
        c.headers["X-Creator-Token"] = TOKEN
        body = request()
        body["mode"] = "LIVE"
        assert c.post("/runs", json=body).status_code == 409
        body["mode"] = "FIXTURE"
        body["limits"]["maxModelCalls"] = 999
        assert c.post("/runs", json=body).status_code == 400


def test_partial_skill_read_cannot_satisfy_delegation(tmp_path, monkeypatch):
    from app.models import FixtureModel
    original = FixtureModel._generate

    def generate(self, messages, stop=None, run_manager=None, **kwargs):
        result = original(self, messages, stop=stop, run_manager=run_manager, **kwargs)
        calls = result.generations[0].message.tool_calls
        if self.role == "SUPERVISOR" and calls and calls[0]["name"] == "read_file" and calls[0]["args"]["file_path"] == self.skill_file:
            calls[0]["args"]["offset"] = 1
        return result

    monkeypatch.setattr(FixtureModel, "_generate", generate)
    body = request("run_partial_skill_read")
    with TestClient(create_app(tmp_path, TOKEN), headers={"X-Creator-Token": TOKEN}) as c:
        assert c.post("/runs", json=body).status_code == 202
        run = wait(c, body["id"], {"WAITING_ACCEPTANCE", "FAILED"})
        assert run["status"] == "FAILED"
        reads = [e["data"] for e in c.get(f"/runs/{body['id']}/events").json() if e["kind"] == "TOOL_FINISHED" and e["data"].get("fileTarget") == "ROLE_SKILL"]
        assert reads and reads[0]["success"] and not reads[0]["complete"]


def test_truncated_skill_content_is_not_complete():
    from deepagents.middleware.filesystem import READ_FILE_TRUNCATION_MSG
    from langchain_core.messages import ToolMessage
    from app.engine import complete_profile_read
    profile = {"skill": "第一行\n第二行\n"}
    path = "/skills/test-skill/SKILL.md"
    result = ToolMessage(content="部分内容\n" + READ_FILE_TRUNCATION_MSG.format(file_path=path), tool_call_id="test", status="success")
    assert not complete_profile_read(profile, "ROLE_SKILL", {"file_path": path}, result)

    # 窗口范围不完整，或 backend 在声称完整的窗口中截断正文，均不可视为已读。
    for content in ("@@ lines 1-1 of 2 | next offset 1 @@\n第一行",
                    "@@ lines 1-2 of 2 @@\n第一行\n[backend truncated]"):
        result = ToolMessage(content=content, tool_call_id="test", status="success")
        assert not complete_profile_read(profile, "ROLE_SKILL", {"file_path": path}, result)
    result = ToolMessage(content="@@ lines 1-2 of 2 @@\n第一行\n第二行", tool_call_id="test", status="success")
    assert complete_profile_read(profile, "ROLE_SKILL", {"file_path": path}, result)


def test_cancel_is_idempotent_after_response_loss(tmp_path):
    app = create_app(tmp_path, TOKEN)
    with TestClient(app, headers={"X-Creator-Token": TOKEN}) as c:
        run_id = "run_cancel_idempotent"
        app.state.store.create(run_id, {})
        assert c.post(f"/runs/{run_id}/cancel").json()["status"] == "CANCELLED"
        assert c.post(f"/runs/{run_id}/cancel").json()["status"] == "CANCELLED"
        assert [e["kind"] for e in c.get(f"/runs/{run_id}/events").json()] == ["CANCELLED"]


def test_reviewer_toolset_converges_after_reading_required_materials(tmp_path, monkeypatch):
    from app.models import FixtureModel
    from langchain_core.messages import AIMessage, ToolMessage
    from langchain_core.outputs import ChatGeneration, ChatResult

    original = FixtureModel._generate
    inspected = []

    def try_repeat(self, messages, stop=None, run_manager=None, **kwargs):
        read_count = sum(isinstance(m, ToolMessage) and m.name == "read_file" for m in messages)
        if self.role == "REVIEWER" and read_count >= 2:
            inspected.append(list(self.names))
            # 模拟会继续读取的模型：只有 read_file 从可用工具移除后才会提交评审。
            if "read_file" in self.names:
                msg = AIMessage(content="", tool_calls=[{"name": "read_file", "args": {"file_path": "/knowledge/REVIEWER.md"}, "id": "repeat"}])
                return ChatResult(generations=[ChatGeneration(message=msg)])
        return original(self, messages, stop=stop, run_manager=run_manager, **kwargs)

    monkeypatch.setattr(FixtureModel, "_generate", try_repeat)
    body = request("run_reviewer_converge")
    body["fixtureRework"] = False
    with TestClient(create_app(tmp_path, TOKEN), headers={"X-Creator-Token": TOKEN}) as c:
        assert c.post("/runs", json=body).status_code == 202
        run = wait(c, body["id"], {"WAITING_ACCEPTANCE", "FAILED", "UNKNOWN"})
        assert run["status"] == "WAITING_ACCEPTANCE", run
        assert inspected and all("read_file" not in names and "submit_review" in names for names in inspected)
        reads = [e["data"] for e in c.get(f"/runs/{body['id']}/events").json()
                 if e["role"] == "REVIEWER" and e["kind"] == "TOOL_FINISHED" and e["data"]["tool"] == "read_file"]
        assert [(e["fileTarget"], e["success"]) for e in reads] == [("ROLE_SKILL", True), ("ROLE_KNOWLEDGE", True)]


def test_failed_file_read_does_not_satisfy_required_read():
    from types import SimpleNamespace
    from langchain_core.messages import ToolMessage
    from app.engine import Guard
    from app.profiles import load_profiles, skill_path

    profile = load_profiles()["REVIEWER"]
    execution = SimpleNamespace(payload={"profiles": {"REVIEWER": profile}}, reads=set(),
                                emit=lambda *args, **kwargs: None, check=lambda: None)
    guard = Guard(execution, "REVIEWER")
    path = skill_path(profile)
    req = SimpleNamespace(tool_call={"name": "read_file", "args": {"file_path": path}})
    guard.wrap_tool_call(req, lambda request: ToolMessage(content="unavailable", tool_call_id="1", status="error"))
    assert ("REVIEWER", path) not in execution.reads


def test_live_requires_explicit_model_allowlist_without_network(tmp_path, monkeypatch):
    monkeypatch.setenv("CREATOR_ENABLE_LIVE_MODELS", "true")
    monkeypatch.delenv("CREATOR_APPROVED_AGENT_MODELS", raising=False)
    with TestClient(create_app(tmp_path, TOKEN), headers={"X-Creator-Token": TOKEN}) as c:
        body = request("run_unauthorized_live")
        body["mode"] = "LIVE"
        assert c.post("/runs", json=body).status_code == 409


def test_checkpoint_survives_restart_and_usage_limit(tmp_path):
    body = request("run_restart_0001")
    with TestClient(create_app(tmp_path, TOKEN), headers={"X-Creator-Token": TOKEN}) as c:
        c.post("/runs", json=body)
        assert wait(c, body["id"], {"WAITING_ACCEPTANCE", "FAILED"})["status"] == "WAITING_ACCEPTANCE"
    with TestClient(create_app(tmp_path, TOKEN), headers={"X-Creator-Token": TOKEN}) as c:
        assert c.post(f"/runs/{body['id']}/decision", json={"decision": "REJECT"}).status_code == 200
        assert wait(c, body["id"], {"REJECTED", "FAILED"})["status"] == "REJECTED"
        limited = request("run_limited_0001")
        limited["limits"]["maxModelCalls"] = 3
        c.post("/runs", json=limited)
        result = wait(c, limited["id"], {"FAILED", "WAITING_ACCEPTANCE"})
        assert result["status"] == "FAILED"
        events = c.get(f"/runs/{limited['id']}/events").json()
        assert len([e for e in events if e["kind"] == "MODEL_STARTED"]) == 3


def test_legacy_token_limits_do_not_stop_execution_and_usage_is_recorded(tmp_path):
    """旧快照中的 token 字段不再限流，但 usage 事件仍可审计。"""
    body = request("run_without_token_limit")
    body["limits"].update(maxTotalTokens=1, maxOutputTokens=1)
    body["fixtureRework"] = False
    with TestClient(create_app(tmp_path, TOKEN), headers={"X-Creator-Token": TOKEN}) as client:
        assert client.post("/runs", json=body).status_code == 202
        run = wait(client, body["id"], {"WAITING_ACCEPTANCE", "FAILED"})
        assert run["status"] == "WAITING_ACCEPTANCE", run
        events = client.get(f"/runs/{body['id']}/events").json()
        assert sum(e["data"].get("tokens", 0) for e in events if e["kind"] == "MODEL_FINISHED") > 1


NODES = json.loads((ROOT / "specs/nodes.json").read_text(encoding="utf-8"))["nodes"]


@pytest.mark.parametrize("node", NODES, ids=lambda n: n["id"])
def test_all_node_contracts_and_independent_role_packages(tmp_path, node):
    """49 节点框架合同验收，输入为隔离夹具，不等同于跨节点业务或领域验收。"""
    body = request("run_contract_" + node["id"])
    body["node"], body["fixtureRework"] = node, False
    with TestClient(create_app(tmp_path, TOKEN), headers={"X-Creator-Token": TOKEN}) as c:
        assert c.post("/runs", json=body).status_code == 202
        run = wait(c, body["id"], {"WAITING_ACCEPTANCE", "FAILED"})
        assert run["status"] == "WAITING_ACCEPTANCE", run
        candidate = next(a["content"] for a in run["artifacts"] if a["kind"] == "CANDIDATE")
        assert set(node["output"]["requiredFields"]) <= candidate["fields"].keys()
        assert candidate["fixture"] is True


def test_analyst_can_call_registered_statistics_tool(tmp_path, monkeypatch):
    """验证真实委派链路注册并执行离线计算，而不是仅测试独立函数。"""
    from langchain_core.messages import AIMessage, ToolMessage
    from langchain_core.outputs import ChatGeneration, ChatResult
    from app.models import FixtureModel

    original = FixtureModel._generate

    def generate(self, messages, stop=None, run_manager=None, **kwargs):
        used = [m.name for m in messages if isinstance(m, ToolMessage)]
        if self.role == "ANALYST" and "read_inputs" in used and "calculate_proportion_interval" not in used:
            message = AIMessage(content="", tool_calls=[{"name": "calculate_proportion_interval",
                "args": {"successes": 50, "trials": 100}, "id": "fixture-statistics"}])
            message.usage_metadata = {"input_tokens": 10, "output_tokens": 10, "total_tokens": 20}
            return ChatResult(generations=[ChatGeneration(message=message)])
        return original(self, messages, stop=stop, run_manager=run_manager, **kwargs)

    monkeypatch.setattr(FixtureModel, "_generate", generate)
    body = request("run_statistics_tool")
    body["node"] = next(node for node in NODES if node["agentRole"] == "ANALYST")
    body["fixtureRework"] = False
    with TestClient(create_app(tmp_path, TOKEN), headers={"X-Creator-Token": TOKEN}) as c:
        assert c.post("/runs", json=body).status_code == 202
        run = wait(c, body["id"], {"WAITING_ACCEPTANCE", "FAILED"})
        assert run["status"] == "WAITING_ACCEPTANCE", run
        events = c.get(f"/runs/{body['id']}/events").json()
        assert any(e["role"] == "ANALYST" and e["kind"] == "TOOL_FINISHED"
                   and e["data"]["tool"] == "calculate_proportion_interval" for e in events)
