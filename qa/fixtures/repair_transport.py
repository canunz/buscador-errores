"""Persistent local mysql CLI session; no subprocess starts at import time.

Password is resolved exclusively inside the QA container using its existing
secret mount. SQL travels on stdin; neither credentials nor SQL go in argv.
No reconnect, retry, temporary SQL file, shell interpolation or stderr logging.
"""
import queue
import re
import secrets
import subprocess
import threading


class MaintenanceConnection:
    def __init__(self, popen=None, docker_path=None, environment=None):
        if popen is None:
            from tls_support import clean_env, NAME
            import shutil
            docker_path = shutil.which('docker')
            environment = clean_env()
        else:
            NAME = 'dimabug-qa-mvp-13318-mysql'
        self.closed = False
        self.rows = []
        self.rowcount = -1
        self.lines = queue.Queue(maxsize=10000)
        command = ('MYSQL_HISTFILE=/dev/null MYSQL_PWD="$(cat /run/secrets/mysql_root_password)" '
                   'exec mysql --default-character-set=utf8mb4 --protocol=socket --user=root '
                   '--database=dima_bug_qa_mvp --batch --raw --skip-column-names '
                   '--unbuffered --skip-reconnect --connect-timeout=5')
        self.process = (popen or subprocess.Popen)(
            [docker_path, '--context', 'desktop-linux', 'exec', '-i', NAME, 'sh', '-c', command],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
            text=True, encoding='utf-8', errors='strict', bufsize=1, env=environment,
            creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
        self.reader = threading.Thread(target=self._read, daemon=True)
        self.reader.start()
        try:
            # Session limits only. Idle/server locks remain bounded after a host interruption.
            self.execute('SET SESSION wait_timeout=15')
            self.execute('SET SESSION innodb_lock_wait_timeout=5')
            self.execute('SET SESSION max_execution_time=5000')
        except Exception:
            self.close()
            raise RuntimeError('REPAIR_TRANSPORT_INITIALIZATION_FAILED') from None

    def _read(self):
        try:
            for line in self.process.stdout:
                self.lines.put(line.rstrip('\r\n'), timeout=1)
        except Exception:
            pass
        finally:
            try:self.lines.put(None, timeout=1)
            except queue.Full:pass

    @staticmethod
    def bind(statement, parameters):
        if statement.count('%s') != len(parameters):raise RuntimeError('REPAIR_PARAMETERS')
        for value in parameters:
            if isinstance(value, int) and not isinstance(value, bool):literal = str(value)
            elif isinstance(value, str) and re.fullmatch('[0-9A-F]+', value):literal = "'"+value+"'"
            else:raise RuntimeError('REPAIR_PARAMETER_TYPE')
            statement = statement.replace('%s', literal, 1)
        return statement

    def cursor(self):return self

    def execute(self, statement, parameters=()):
        if self.closed:raise RuntimeError('REPAIR_CONNECTION_CLOSED')
        statement = self.bind(statement, parameters)
        marker = '__QA_FRAME_' + secrets.token_hex(24) + '__'
        self.rows = []
        self.rowcount = -1
        try:
            self.process.stdin.write(statement.rstrip(';') + ';\nSELECT CONCAT(\'' + marker + "',ROW_COUNT());\n")
            self.process.stdin.flush()
            # mysql --skip-reconnect exits on an SQL error (no --force).
            while True:
                line = self.lines.get(timeout=8)
                if line is None:raise RuntimeError('REPAIR_DISCONNECTED')
                if line.startswith(marker):
                    count = line[len(marker):]
                    if not re.fullmatch('-?[0-9]+', count):raise RuntimeError('REPAIR_FRAME')
                    self.rowcount = int(count)
                    return
                self.rows.append(tuple(line.split('\t')))
        except Exception:
            # Never print driver/provider errors or try to re-send the statement.
            self.close()
            raise RuntimeError('REPAIR_TRANSPORT_UNKNOWN') from None

    def fetchall(self):return self.rows
    def commit(self):self.execute('COMMIT')
    def rollback(self):self.execute('ROLLBACK')

    def close(self):
        if self.closed:return
        self.closed = True
        try:self.process.stdin.close()
        except Exception:pass
        try:self.process.wait(timeout=2)
        except Exception:
            try:self.process.kill();self.process.wait(timeout=2)
            except Exception:pass
        try:self.process.stdout.close()
        except Exception:pass


def factory():
    # Lazy import: no Docker, SQL or credential access merely importing this module.
    from tls_support import ROOT
    return MaintenanceConnection()
