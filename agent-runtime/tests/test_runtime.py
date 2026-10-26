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
