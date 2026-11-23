"""Windows lifecycle bridge. Process handles prevent PID-reuse ambiguity."""
import ctypes
import json
from pathlib import Path
import subprocess
import sys
import time
sys.path.insert(0,str(Path(__file__).resolve().parent))
from fixture_tools import ROOT, require


class Lifecycle:
    def __init__(self, folder, parent, sentinel, uuid):
        self.folder=Path(folder).resolve(strict=True)
        require(self.folder.is_relative_to(ROOT/'qa/runtime/backend-runs'),'REPAIR_SUPERVISION_PATH')
        self.uuid=uuid
        self.deadline=time.monotonic()+140
        self.kernel=ctypes.WinDLL('kernel32',use_last_error=True)
        self.kernel.OpenProcess.argtypes=[ctypes.c_uint32,ctypes.c_bool,ctypes.c_uint32]
        self.kernel.OpenProcess.restype=ctypes.c_void_p
        self.kernel.WaitForSingleObject.argtypes=[ctypes.c_void_p,ctypes.c_uint32]
        self.kernel.CloseHandle.argtypes=[ctypes.c_void_p]
        self.handles=[]
        try:
            for pid in (parent,sentinel):
                handle=self.kernel.OpenProcess(0x00100000,False,pid)
                require(bool(handle),'REPAIR_GUARD_HANDLE')
                self.handles.append(handle)
        except Exception:
            for handle in self.handles:self.kernel.CloseHandle(handle)
            raise

    def assert_private_directory(self,directory):
        from tls_support import clean_env
        import shutil
        result=subprocess.run([shutil.which('pwsh'),'-NoProfile','-NonInteractive','-File',
            str(ROOT/'qa/fixtures/repair_acl.ps1'),'-Directory',str(directory)],
            capture_output=True,text=True,encoding='utf-8',env=clean_env(),timeout=10)
        require(result.returncode==0 and result.stdout.strip()=='PRIVATE_ACL_OK','REPAIR_ACL')

    def assert_apps_stopped(self):
        import socket
        for port in (19090,14200):
            with socket.socket() as probe:
                probe.bind(('127.0.0.1',port))

    def arm_both_watchdogs(self):
        require(json.loads((self.folder/'principal-ready.json').read_text(encoding='utf-8'))['ready'] is True,'REPAIR_PRINCIPAL')
        require(json.loads((self.folder/'sentinel-ready.json').read_text(encoding='utf-8'))['estado']=='LISTO','REPAIR_SENTINEL')
        self.check()

    def check(self):
        require(len(self.handles)==2,'REPAIR_GUARD_HANDLES')
        require(time.monotonic()<self.deadline and not (self.folder/'close.request').exists(),'REPAIR_DEADLINE')
        require(all(self.kernel.WaitForSingleObject(handle,0)==258 for handle in self.handles),'REPAIR_GUARD_EXIT')

    def inspect_metadata(self,session,metadata):
        from run_privilege_test import snapshot
        current,_=snapshot(set(__import__('fixture_tools').load()['table_order']),query=session.query)
        require(current==metadata,'REPAIR_METADATA_CHANGED')

    def close_and_confirm_locked_no_sessions(self):
        from repair_cleanup import close_maintenance
        try:return close_maintenance(self.uuid)
        finally:
            for handle in self.handles:self.kernel.CloseHandle(handle)
            self.handles=[]
