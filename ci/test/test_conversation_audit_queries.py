"""在真实 SQLite 上验证审计批量查询的 cutoff、顺序与跨聊天隔离。"""

import re
import sqlite3
from pathlib import Path


def test_export_queries_preserve_cutoff_order_and_unique_payloads():
    source = (Path(__file__).resolve().parents[2] /
              "app/src/main/java/com/ai/assistance/operit/data/dao/ConversationAuditDao.kt").read_text(encoding="utf-8")
    queries = dict((name, sql) for sql, name in re.findall(
        r'@Query\("""((?:(?!""").)*)"""\)\s*suspend fun (get\w+ThroughSequence)\(', source, re.S))
    with sqlite3.connect(":memory:") as connection:
        connection.executescript("""
            CREATE TABLE conversation_audit_events(eventId TEXT, chatId TEXT, sequenceNumber INTEGER);
            CREATE TABLE conversation_audit_event_payloads(eventId TEXT, payloadSha256 TEXT, ordinal INTEGER);
            CREATE TABLE conversation_audit_payloads(payloadSha256 TEXT, mediaType TEXT);
            INSERT INTO conversation_audit_events VALUES
                ('a3','a',3), ('b1','b',1), ('a2','a',2), ('a1','a',1);
            INSERT INTO conversation_audit_event_payloads VALUES
                ('a3','later',0), ('b1','other',0), ('a2','shared',0),
                ('a1','second',1), ('a1','shared',0);
            INSERT INTO conversation_audit_payloads VALUES
                ('shared','text/plain'), ('second','text/plain'),
                ('later','text/plain'), ('other','text/plain');
        """)
        args = {"chatId": "a", "throughSequenceInclusive": 2}
        refs = connection.execute(queries["getEventPayloadsThroughSequence"], args).fetchall()
        assert refs == [('a1', 'shared', 0), ('a1', 'second', 1), ('a2', 'shared', 0)]
        payloads = connection.execute(queries["getPayloadsThroughSequence"], args).fetchall()
        assert sorted(payloads) == [('second', 'text/plain'), ('shared', 'text/plain')]
        args["throughSequenceInclusive"] = 0
        for query in queries.values():
            assert connection.execute(query, args).fetchall() == []
