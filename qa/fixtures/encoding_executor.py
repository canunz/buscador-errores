"""Transaction engine. No driver, credential loading, SQL execution or live CLI at import.

The connection factory and lifecycle controls are injected. Operational wiring must
be reviewed separately; existing empty-database/load preflights are not compatible.
"""
import copy
import hashlib
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from fixture_tools import assert_rows, canonical_rows, load, read_rows, require
from prepare_encoding_repair import prepare, simulate


def verify_candidate(root, candidate):
    """Offline manifest verification, including tracked/untracked source inventory."""
    sys.path.insert(0, str(root / 'qa'))
    import qa_manifest as manifest
    saved = json.loads((root / 'qa/candidate.json').read_text(encoding='utf-8'))
    require(saved['candidate_id'] == candidate, 'REPAIR_CANDIDATE')
    require(not manifest.changed(saved['files'], manifest.records()), 'REPAIR_MANIFEST')


class Session:
    """DB-API connection, established with charset=utf8mb4 by the factory.

    Factory must supply local QA identity/TLS checks BEFORE authentication and
    bounded connect/read/write timeouts. No automatic reconnect is permitted.
    """
    def __init__(self, connection):
        self.connection = connection
        self.cursor = connection.cursor()

    def execute(self, statement, parameters=()):
        self.cursor.execute(statement, parameters)
        return self.cursor.rowcount

    def query(self, statement):
        self.execute(statement)
        return '\n'.join('\t'.join(str(value) for value in row) for row in self.cursor.fetchall())

    def charset(self):
        require(self.query('SELECT CONCAT(@@character_set_client, CHAR(44), '
                           '@@character_set_connection, CHAR(44), @@character_set_results);')
                == 'utf8mb4,utf8mb4,utf8mb4', 'REPAIR_CHARSET')

    def close(self):
        try:
            self.cursor.close()
        finally:
            self.connection.close()


def row_fingerprints(rows):
    return {table: hashlib.sha256('\n'.join(canonical_rows(values)).encode('utf-8')).hexdigest()
            for table, values in rows.items()}


def run(factory, controls, plan):
    """One attempt; controls failures are fatal, all exceptions remain private.

    controls contract:
      candidate(): verify current manifest, pinned reviewed candidate and plan.
      preflight(): recovery-specific isolation/schema/grants/accounts/zero sessions;
                   return immutable identity/schema/grants baseline.
      backup(baseline): consistent utf8mb4 backup, readback/hash, exclusive run dir.
      arm(): both watchdogs ready + exclusive one-use persistent attempt marker.
      check(): watchdogs alive, deadline/isolation valid (raises on failure).
      inspect(session, baseline): independent instance/schema/grants validation.
      finish(): close maintenance, lock QA accounts, zero sessions; bool result.
    None of these may unlock accounts or call the old empty-database preflight.
    """
    result = {'transaction': 'NOT_STARTED', 'verification': 'NOT_RUN',
              'security': 'UNKNOWN', 'automatic_retry': False, 'updated_cells': 0}
    session = None
    started = False
    commit_attempted = False
    before = None
    try:
        require(plan == prepare()[1], 'REPAIR_PLAN_CHANGED')
        controls.candidate()
        baseline = copy.deepcopy(controls.preflight())
        controls.backup(baseline)
        controls.arm()
        controls.check()
        session = Session(factory())
        session.charset()
        controls.inspect(session, baseline)
        # REPEATABLE READ plus full PK-range locking prevents concurrent fixture
        # changes, including insertions into previously empty InnoDB tables.
        session.execute('SET TRANSACTION ISOLATION LEVEL REPEATABLE READ')
        started = True  # lost START acknowledgment still requires rollback
        session.execute('START TRANSACTION')
        source, target = load('mvp-qa-v1'), load('mvp-qa-v2')
        for table in source['table_order']:
            session.query('SELECT * FROM `dima_bug_qa_mvp`.`' + table + '` FOR UPDATE')
        before = read_rows(session.query, source)
        assert_rows(source, before)
        expected = simulate(before, plan)  # preserves actual BCrypt bytes
        for change in plan['changes']:
            controls.check()
            require(session.execute(change['statement'],
                    (change['replacement_utf8_hex'], change['id'],
                     change['expected_before_utf8_hex'])) == 1, 'REPAIR_ROWCOUNT')
        after = read_rows(session.query, target)
        assert_rows(target, after)  # includes fixture FK/logical validation
        require(row_fingerprints(after) == row_fingerprints(expected), 'REPAIR_EXTRA_CHANGE')
        controls.inspect(session, baseline)
        controls.check()
        commit_attempted = True
        result['transaction'] = 'UNKNOWN'
        session.connection.commit()  # exactly once; never retry on lost acknowledgment
        result['transaction'] = 'COMMITTED'
        result['verification'] = 'NOT_VERIFIED'
        result['updated_cells'] = 35
        session.close()
        session = None
        independent = Session(factory())
        session = independent
        independent.charset()
        controls.inspect(independent, baseline)
        actual = read_rows(independent.query, target)
        assert_rows(target, actual)
        require(row_fingerprints(actual) == row_fingerprints(expected), 'REPAIR_POST_COMMIT')
        result['verification'] = 'VERIFIED'
        result['data_sha256'] = row_fingerprints(actual)
        result['version'] = target['version']
        result['format'] = 'dimabug-qa-encoding-repair-result-v1'
    except Exception:
        result['failure'] = 'DETAILS_SUPPRESSED'
        if started and not commit_attempted:
            result['transaction'] = 'UNKNOWN'
            try:
                session.connection.rollback()
                result['transaction'] = 'ROLLED_BACK'
                result['verification'] = 'NOT_VERIFIED'
            except Exception:
                pass
            if result['transaction'] == 'ROLLED_BACK' and before is not None:
                try:
                    session.close()
                    session = None
                    session = Session(factory())
                    session.charset()
                    controls.inspect(session, baseline)
                    unchanged = read_rows(session.query, load('mvp-qa-v1'))
                    require(row_fingerprints(unchanged) == row_fingerprints(before), 'REPAIR_ROLLBACK_CONTENT')
                    result['verification'] = 'ROLLBACK_VERIFIED'
                except Exception:
                    pass
        # A COMMIT exception is always UNKNOWN; ROLLBACK cannot prove its outcome.
    finally:
        if session is not None:
            try:
                session.close()
            except Exception:
                result['connection_close'] = 'NOT_CONFIRMED'
        try:
            result['security'] = 'VERIFIED' if controls.finish() is True else 'UNKNOWN'
        except Exception:
            result['security'] = 'UNKNOWN'
    result['success'] = (result['transaction'] == 'COMMITTED' and
                         result['verification'] == 'VERIFIED' and result['security'] == 'VERIFIED')
    return result


if __name__ == '__main__':
    print('{"status":"OFFLINE_ENGINE_ONLY","live_execution_available":false}')
