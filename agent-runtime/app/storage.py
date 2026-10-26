"""运行与事件分离存储；业务产物采纳仍由 Java 工作区负责。"""
import json
import sqlite3
import threading
from datetime import datetime, timezone
from pathlib import Path


def now():
    return datetime.now(timezone.utc).isoformat()


class Store:
    def __init__(self, root: Path):
        root.mkdir(parents=True, exist_ok=True)
        self.lock = threading.RLock()
        self.db = sqlite3.connect(root / "runs.sqlite", check_same_thread=False)
        self.db.row_factory = sqlite3.Row
        self.db.executescript("""
        PRAGMA journal_mode=WAL;
        CREATE TABLE IF NOT EXISTS runs(id TEXT PRIMARY KEY, payload TEXT NOT NULL,
          status TEXT NOT NULL, created_at TEXT NOT NULL, updated_at TEXT NOT NULL,
          deleted_at TEXT, decision TEXT, error TEXT);
        CREATE TABLE IF NOT EXISTS events(seq INTEGER PRIMARY KEY AUTOINCREMENT,
          run_id TEXT NOT NULL, kind TEXT NOT NULL, role TEXT NOT NULL,
          data TEXT NOT NULL, at TEXT NOT NULL);
        CREATE INDEX IF NOT EXISTS ix_events_run ON events(run_id,seq);
        CREATE TABLE IF NOT EXISTS artifacts(run_id TEXT NOT NULL, version INTEGER NOT NULL,
          kind TEXT NOT NULL, content TEXT NOT NULL, at TEXT NOT NULL,
          PRIMARY KEY(run_id,version,kind));
        """)
        self.db.commit()

    def create(self, run_id, payload):
        with self.lock, self.db:
            old = self.db.execute("SELECT payload FROM runs WHERE id=?", (run_id,)).fetchone()
            encoded = json.dumps(payload, sort_keys=True, ensure_ascii=False)
            if old:
                if old[0] != encoded:
                    raise ValueError("幂等键已用于不同请求")
                return False
            self.db.execute("INSERT INTO runs(id,payload,status,created_at,updated_at) VALUES(?,?,?,?,?)",
                            (run_id, encoded, "QUEUED", now(), now()))
            return True

    def event(self, run_id, kind, role="SYSTEM", **data):
        with self.lock, self.db:
            self.db.execute("INSERT INTO events(run_id,kind,role,data,at) VALUES(?,?,?,?,?)",
                            (run_id, kind, role, json.dumps(data, ensure_ascii=False), now()))

    def status(self, run_id, status, error=None):
        with self.lock, self.db:
            # 取消是终态，迟到的模型结果不能重新激活任务。
            self.db.execute("UPDATE runs SET status=?,error=?,updated_at=? WHERE id=? AND status!='CANCELLED'",
                            (status, error, now(), run_id))

    def decision(self, run_id, value):
        with self.lock, self.db:
            self.db.execute("UPDATE runs SET decision=?,updated_at=? WHERE id=?", (value, now(), run_id))

    def artifact(self, run_id, version, kind, value):
        with self.lock, self.db:
            self.db.execute("INSERT INTO artifacts VALUES(?,?,?,?,?)",
                            (run_id, version, kind, json.dumps(value, ensure_ascii=False), now()))

    def get(self, run_id):
        with self.lock:
            row = self.db.execute("SELECT * FROM runs WHERE id=?", (run_id,)).fetchone()
            if not row:
                raise KeyError(run_id)
            result = dict(row)
            result["payload"] = json.loads(result["payload"])
            result["artifacts"] = [dict(r) | {"content": json.loads(r["content"])} for r in
                                   self.db.execute("SELECT * FROM artifacts WHERE run_id=? ORDER BY version,kind", (run_id,))]
            return result

    def events(self, run_id, after=0):
        with self.lock:
            return [dict(r) | {"data": json.loads(r["data"])} for r in self.db.execute(
                "SELECT * FROM events WHERE run_id=? AND seq>? ORDER BY seq LIMIT 500", (run_id, after))]

    def list(self):
        with self.lock:
            return [dict(r) for r in self.db.execute(
                "SELECT id,status,created_at,updated_at,deleted_at,error FROM runs ORDER BY created_at DESC LIMIT 200")]

    def recover(self):
        """不自动重试可能已经计费的调用。等待人工采纳的 checkpoint 可安全保留。"""
        for run in self.list():
            if run["status"] == "RUNNING":
                self.status(run["id"], "UNKNOWN", "进程中断：调用结果未知，请人工核对后另建运行。")
                self.event(run["id"], "RECOVERY_UNKNOWN")

    def delete(self, run_id, deleted):
        with self.lock, self.db:
            self.db.execute("UPDATE runs SET deleted_at=? WHERE id=?", (now() if deleted else None, run_id))
