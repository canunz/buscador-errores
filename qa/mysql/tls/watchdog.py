"""Independent bounded guardian. Never unlocks accounts or reads app credentials."""
import argparse
import ctypes
import os
from pathlib import Path
import sys
import time

sys.path.insert(0,str(Path(__file__).resolve().parent))
from tls_support import ROOT,sql,close_window,emit,require


def due(now,start,parent_alive,disarmed):
    return not disarmed and (not parent_alive or now-start>=180)


def main(folder,parent):
    folder=folder.resolve();require(folder.is_relative_to((ROOT/'qa/runtime/tls-runs').resolve()),'INVALID_GUARD_FOLDER')
    require(parent>0 and parent!=os.getpid(),'INVALID_PARENT')
    kernel=ctypes.WinDLL('kernel32',use_last_error=True)
    kernel.OpenProcess.argtypes=[ctypes.c_uint32,ctypes.c_bool,ctypes.c_uint32];kernel.OpenProcess.restype=ctypes.c_void_p
    kernel.WaitForSingleObject.argtypes=[ctypes.c_void_p,ctypes.c_uint32];kernel.CloseHandle.argtypes=[ctypes.c_void_p]
    handle=kernel.OpenProcess(0x00100000,False,parent)
    require(bool(handle),'PARENT_NOT_OBSERVABLE')
    start=time.monotonic()
    try:
        require(sql("SELECT COUNT(*) FROM mysql.user WHERE User='dimabug_qa_app' AND Host='172.21.0.3' AND account_locked='Y';")=='1','GUARD_ACCOUNT_NOT_LOCKED')
        emit(folder/'ready.json',{'estado':'LISTO','deadline_seconds':180})
        while True:
            disarmed=(folder/'disarm').exists()
            alive=kernel.WaitForSingleObject(handle,0)==258
            if disarmed or due(time.monotonic(),start,alive,False):
                closed=close_window()
                emit(folder/'guard-final.json',{'estado':'CIERRE_VERIFICADO','closed':closed,'trigger':'disarm' if disarmed else 'timeout_or_parent_exit'})
                return
            time.sleep(0.25)
    finally:kernel.CloseHandle(handle)


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--dir',required=True);parser.add_argument('--parent',required=True,type=int);args=parser.parse_args()
    try:main(Path(args.dir),args.parent)
    except Exception:
        # No traceback: root diagnostics might include provider details.
        emit(Path(args.dir)/'guard-final.json',{'estado':'CIERRE_NO_VERIFICADO','closed':False});raise SystemExit(1)
