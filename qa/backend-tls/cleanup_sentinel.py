"""Independent second guardian for a FUTURE authorized window; default is inert."""
import argparse
import ctypes
import json
import os
from pathlib import Path
import sys
import time
sys.path.insert(0,str(Path(__file__).resolve().parent))
from close_qa_account import close,close_report

ROOT=Path(__file__).resolve().parents[2]

def should_close(elapsed,parent_alive,requested):return requested or not parent_alive or elapsed>=180

def retry_close(action,wait=time.sleep,observer=None):
    for attempt in range(3):
        try:
            result=action()
            if observer:observer(attempt+1,result)
            if (result.get('closed') is True if isinstance(result,dict) else result is True):return True
        except Exception:
            if observer:observer(attempt+1,{'closed':False,'failure_code':'CONTROL_OR_TRANSPORT_ERROR'})
        if attempt<2:wait(1)
    return False

def emit(folder,name,document):
    file=folder/name;temp=folder/(name+'.tmp')
    temp.write_text(json.dumps(document)+'\n',encoding='utf-8');temp.replace(file)

def run(folder,parent,close_action=close):
    folder=folder.resolve(strict=True)
    if not folder.is_relative_to((ROOT/'qa/runtime/backend-runs').resolve()) or parent<=0 or parent==os.getpid():raise RuntimeError('QA_SENTINEL_ARGUMENTS')
    kernel=ctypes.WinDLL('kernel32',use_last_error=True)
    kernel.OpenProcess.argtypes=[ctypes.c_uint32,ctypes.c_bool,ctypes.c_uint32];kernel.OpenProcess.restype=ctypes.c_void_p
    kernel.WaitForSingleObject.argtypes=[ctypes.c_void_p,ctypes.c_uint32];kernel.CloseHandle.argtypes=[ctypes.c_void_p]
    handle=kernel.OpenProcess(0x00100000,False,parent)
    if not handle:raise RuntimeError('QA_SENTINEL_PARENT_PERMISSION_REQUIRED')
    start=time.monotonic()
    try:
        emit(folder,'sentinel-ready.json',{'estado':'LISTO','deadline_seconds':180})
        while not should_close(time.monotonic()-start,kernel.WaitForSingleObject(handle,0)==258,(folder/'close.request').exists()):time.sleep(.2)
        attempts=[]
        def record(number,result):
            allowed=('phase','lock_attempted','lock_acknowledged','account_locked_verified','app_sessions_zero_verified','failure_code','elapsed_ms','closed')
            item={'attempt':number}
            if isinstance(result,dict):item.update({k:result[k] for k in allowed if k in result})
            else:item['closed']=result is True
            attempts.append(item)
        closed=retry_close(close_action,observer=record)
        emit(folder,'sentinel-final.json',{'estado':'CIERRE_VERIFICADO' if closed else 'CIERRE_NO_VERIFICADO',
             'actor':'sentinel','run_id':folder.name,'utc':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),
             'attempts':attempts,'manual_recovery_required':not closed})
        return closed
    finally:kernel.CloseHandle(handle)

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--run-authorized-window',action='store_true');p.add_argument('--dir');p.add_argument('--parent',type=int);a=p.parse_args()
    if not a.run_authorized_window:print('{"estado":"QA_SENTINEL_PREPARED_NO_SQL"}')
    else:
        try:raise SystemExit(0 if run(Path(a.dir),a.parent,close_action=lambda:close_report(actor='sentinel',run_id=Path(a.dir).name)) else 1)
        except Exception:
            # Parent will stop Java and attempt its own close. Do not hide failure.
            try:emit(Path(a.dir),'sentinel-final.json',{'estado':'CIERRE_NO_VERIFICADO','actor':'sentinel',
                'run_id':Path(a.dir).name,'utc':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),
                'failure_code':'SENTINEL_CONTROL_ERROR','database_state':'DESCONOCIDO','manual_recovery_required':True})
            except Exception:pass
            raise SystemExit(1)
