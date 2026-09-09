"""用源码中的 SQL 与迁移索引验证删除后的引用安全及查询成本，不依赖 Android 设备。"""

from pathlib import Path
import re
import sqlite3
import time
import unittest


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "app/src/main/java/com/ai/assistance/operit/data"


def dao_query(method):
    source = (DATA / "dao/ConversationAuditDao.kt").read_text(encoding="utf-8")
    for match in re.finditer(
        r'@Query\(\s*"""(.*?)"""\s*\)\s*suspend fun (\w+)\(', source, re.S
    ):
        if match[2] == method:
            return match[1]
    raise AssertionError(f"DAO query missing: {method}")


class ConversationAuditDeletionTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.addCleanup(self.db.close)
        self.db.execute("PRAGMA foreign_keys = ON")
        self.db.execute("CREATE TABLE chats (id TEXT PRIMARY KEY)")
        source = (DATA / "db/AppDatabase.kt").read_text(encoding="utf-8")
        # 直接使用正式迁移中的七张审计表和全部索引，避免手写简化模型掩盖级联/索引问题。
        tables = re.findall(
            r'"""\s*(CREATE TABLE IF NOT EXISTS `conversation_.*?)"""', source, re.S
        )
        self.assertEqual(7, len(tables))
        for sql in tables:
            self.db.execute(sql)
        for sql in re.findall(r'"(CREATE (?:UNIQUE )?INDEX[^"\n]*`conversation_[^"\n]*)"', source):
            self.db.execute(sql)
        self.columns = {}

    def insert(self, table, **values):
        if table not in self.columns:
            self.columns[table] = list(self.db.execute(f"PRAGMA table_info({table})"))
        fields = {}
        for _, name, kind, required, default, primary in self.columns[table]:
            if name in values:
                fields[name] = values[name]
            elif (required or primary) and default is None:
                fields[name] = 1 if kind == "INTEGER" else "fixture"
        names = ",".join(fields)
        placeholders = ",".join("?" for _ in fields)
        self.db.execute(f"INSERT INTO {table} ({names}) VALUES ({placeholders})", list(fields.values()))

    def chat(self, chat_id):
        self.insert("chats", id=chat_id)
        self.insert("conversation_audit_events", eventId=chat_id, chatId=chat_id)

    def payload(self, payload_hash):
        self.insert("conversation_audit_payloads", payloadSha256=payload_hash)

    def event_ref(self, chat_id, payload_hash, ordinal=0):
        self.insert("conversation_audit_event_payloads", eventId=chat_id,
                    payloadSha256=payload_hash, ordinal=ordinal)

    def revision(self, chat_id, payload_hash, number=0):
        self.insert("conversation_message_revisions", revisionId=f"{chat_id}-{number}",
                    chatId=chat_id, auditEventId=chat_id, contentPayloadSha256=payload_hash,
                    revisionNumber=number)

    def candidates(self, chat_id):
        return [row[0] for row in self.db.execute(dao_query("getPayloadHashesForChat"), {"chatId": chat_id})]

    def unreferenced(self, hashes):
        sql = dao_query("getUnreferencedPayloadsAmong").replace(
            ":payloadHashes", ",".join("?" for _ in hashes)
        )
        return {row[0] for row in self.db.execute(sql, hashes)}

    def test_single_deletion_preserves_event_and_revision_references_from_other_chats(self):
        self.chat("deleted")
        self.chat("kept")
        for index, key in enumerate(("exclusive", "event-shared", "revision-shared")):
            self.payload(key)
            self.event_ref("deleted", key, index)
        self.event_ref("kept", "event-shared")
        self.revision("kept", "revision-shared")
        self.payload("unrelated-orphan")
        with self.db:
            candidates = self.candidates("deleted")
            self.db.execute("DELETE FROM chats WHERE id = ?", ("deleted",))
        self.assertEqual({"exclusive"}, self.unreferenced(candidates))
        self.assertEqual({"exclusive", "unrelated-orphan"}, {
            row[0] for row in self.db.execute(dao_query("getUnreferencedPayloads"))
        })
        self.assertEqual([], self.candidates("deleted"))
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("DELETE FROM conversation_audit_payloads WHERE payloadSha256 = 'event-shared'")
        self.assertEqual([], list(self.db.execute("PRAGMA foreign_key_check")))

    def test_revision_only_candidates_and_duplicate_event_references_are_deduplicated(self):
        self.chat("deleted")
        self.payload("revision-only")
        self.payload("repeated")
        self.revision("deleted", "revision-only")
        self.revision("deleted", "repeated", 1)
        self.event_ref("deleted", "repeated")
        self.event_ref("deleted", "repeated", 1)
        self.assertEqual(["repeated", "revision-only"], self.candidates("deleted"))

    def test_group_collection_reclaims_shared_payload_only_after_last_chat_is_deleted(self):
        self.payload("shared")
        for chat_id in ("first", "second"):
            self.chat(chat_id)
            self.event_ref(chat_id, "shared")
        candidates = self.candidates("first")
        self.db.execute("DELETE FROM chats WHERE id = 'first'")
        self.assertEqual(set(), self.unreferenced(candidates))
        self.db.execute("DELETE FROM chats WHERE id = 'second'")
        self.assertEqual({"shared"}, self.unreferenced(candidates))

    def test_new_reference_between_deletion_and_collection_is_preserved(self):
        self.payload("reused")
        self.chat("deleted")
        self.chat("new-owner")
        self.event_ref("deleted", "reused")
        candidates = self.candidates("deleted")
        self.db.execute("DELETE FROM chats WHERE id = 'deleted'")
        self.event_ref("new-owner", "reused")
        self.assertEqual(set(), self.unreferenced(candidates))

    def test_rolled_back_deletion_does_not_make_live_payload_collectible(self):
        self.chat("kept")
        self.payload("kept-payload")
        self.event_ref("kept", "kept-payload")
        self.db.commit()
        candidates = self.candidates("kept")
        with self.assertRaises(RuntimeError), self.db:
            self.db.execute("DELETE FROM chats WHERE id = 'kept'")
            raise RuntimeError("rollback")
        self.assertEqual(set(), self.unreferenced(candidates))

    def test_empty_missing_and_duplicate_candidates(self):
        self.payload("orphan")
        self.assertEqual([], self.candidates("missing-chat"))
        self.assertEqual(set(), self.unreferenced([]))
        self.assertEqual(set(), self.unreferenced(["missing-payload"]))
        self.assertEqual({"orphan"}, self.unreferenced(["orphan", "orphan"]))

    def measured_query(self, sql, parameters=()):
        steps = 0

        def count():
            nonlocal steps
            steps += 1
            return 0

        self.db.set_progress_handler(count, 1)
        try:
            rows = list(self.db.execute(sql, parameters))
        finally:
            self.db.set_progress_handler(None, 0)
        started = time.perf_counter()
        for _ in range(5):
            list(self.db.execute(sql, parameters))
        elapsed_ms = (time.perf_counter() - started) * 1000 / 5
        return rows, steps, elapsed_ms

    def test_scoped_query_cost_is_independent_of_unrelated_history(self):
        self.payload("target")
        self.chat("target-chat")
        self.event_ref("target-chat", "target")
        candidate_sql = dao_query("getPayloadHashesForChat")
        _, small_candidate_steps, _ = self.measured_query(candidate_sql, {"chatId": "target-chat"})
        scoped = dao_query("getUnreferencedPayloadsAmong").replace(":payloadHashes", "?")
        _, small_steps, _ = self.measured_query(scoped, ("missing",))
        self.chat("unrelated")
        for index in range(10000):
            key = f"unrelated-{index}"
            self.payload(key)
            self.event_ref("unrelated", key, index)
        self.db.commit()
        candidates, candidate_steps, _ = self.measured_query(candidate_sql, {"chatId": "target-chat"})
        self.assertEqual([("target",)], candidates)
        self.assertLess(candidate_steps, small_candidate_steps * 2)
        self.db.execute("DELETE FROM chats WHERE id = 'target-chat'")
        old_query = """
            SELECT payload.* FROM conversation_audit_payloads AS payload
            LEFT JOIN conversation_audit_event_payloads AS event_ref
                ON event_ref.payloadSha256 = payload.payloadSha256
            LEFT JOIN conversation_message_revisions AS revision_ref
                ON revision_ref.contentPayloadSha256 = payload.payloadSha256
            WHERE event_ref.payloadSha256 IS NULL AND revision_ref.contentPayloadSha256 IS NULL
        """
        old_rows, old_steps, old_ms = self.measured_query(old_query)
        new_rows, new_steps, new_ms = self.measured_query(scoped, ("target",))
        self.assertEqual(old_rows, new_rows)
        _, missing_steps, _ = self.measured_query(scoped, ("missing",))
        self.assertLess(missing_steps, small_steps * 2)
        self.assertGreater(old_steps, new_steps * 100)
        self.assertEqual(old_rows, list(self.db.execute(dao_query("getUnreferencedPayloads"))))
        print(f"\nSQLite {sqlite3.sqlite_version}; 10000 unrelated payloads: "
              f"old={old_steps} VM steps/{old_ms:.3f}ms; "
              f"scoped={new_steps} VM steps/{new_ms:.3f}ms; "
              f"candidate snapshot={candidate_steps} VM steps")


if __name__ == "__main__":
    unittest.main()
