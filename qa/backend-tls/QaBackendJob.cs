using System;
using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text;
using System.Linq;

// Same-user Windows job. Child is assigned while SUSPENDED: no unguarded execution window.
public sealed class QaBackendJob : IDisposable {
    IntPtr job,process; public int ProcessId {get;private set;}
    [StructLayout(LayoutKind.Sequential)] struct Basic { public long PerProcessUserTimeLimit,PerJobUserTimeLimit;public uint LimitFlags;public UIntPtr MinimumWorkingSetSize,MaximumWorkingSetSize;public uint ActiveProcessLimit;public UIntPtr Affinity;public uint PriorityClass,SchedulingClass; }
    [StructLayout(LayoutKind.Sequential)] struct Counters {public ulong ReadOperationCount,WriteOperationCount,OtherOperationCount,ReadTransferCount,WriteTransferCount,OtherTransferCount;}
    [StructLayout(LayoutKind.Sequential)] struct Limits {public Basic BasicLimitInformation;public Counters IoInfo;public UIntPtr ProcessMemoryLimit,JobMemoryLimit,PeakProcessMemoryUsed,PeakJobMemoryUsed;}
    [StructLayout(LayoutKind.Sequential,CharSet=CharSet.Unicode)] struct Startup { public uint cb;public string reserved,desktop,title;public uint x,y,xSize,ySize,xChars,yChars,fill,flags;public ushort show,reserved2;public IntPtr reservedPtr,input,output,error; }
    [StructLayout(LayoutKind.Sequential)] struct Child {public IntPtr process,thread;public uint processId,threadId;}
    [DllImport("kernel32.dll",CharSet=CharSet.Unicode,SetLastError=true)] static extern IntPtr CreateJobObject(IntPtr attributes,string name);
    [DllImport("kernel32.dll",SetLastError=true)] static extern bool SetInformationJobObject(IntPtr job,int type,ref Limits info,uint length);
    [DllImport("kernel32.dll",CharSet=CharSet.Unicode,SetLastError=true)] static extern bool CreateProcess(string app,StringBuilder command,IntPtr pa,IntPtr ta,bool inherit,uint flags,IntPtr environment,string cwd,ref Startup startup,out Child child);
    [DllImport("kernel32.dll",SetLastError=true)] static extern bool AssignProcessToJobObject(IntPtr job,IntPtr process);
    [DllImport("kernel32.dll")] static extern uint ResumeThread(IntPtr thread);
    [DllImport("kernel32.dll")] static extern bool TerminateProcess(IntPtr process,uint exit);
    [DllImport("kernel32.dll")] static extern bool TerminateJobObject(IntPtr job,uint exit);
    [DllImport("kernel32.dll")] static extern uint WaitForSingleObject(IntPtr handle,uint milliseconds);
    [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr handle);
    public QaBackendJob() {
        job=CreateJobObject(IntPtr.Zero,null);
        if(job==IntPtr.Zero)throw new InvalidOperationException("QA_JOB_PERMISSION_REQUIRED");
        var info=new Limits();info.BasicLimitInformation.LimitFlags=0x2000; // KILL_ON_JOB_CLOSE
        if(!SetInformationJobObject(job,9,ref info,(uint)Marshal.SizeOf<Limits>())){Dispose();throw new InvalidOperationException("QA_JOB_LIMIT_PERMISSION_REQUIRED");}
    }
    static string Quote(string value) {
        var output=new StringBuilder("\"");int slashes=0;
        foreach(char c in value){if(c=='\\'){slashes++;continue;}if(c=='\"'){output.Append('\\',slashes*2+1).Append(c);}else{output.Append('\\',slashes).Append(c);}slashes=0;}
        return output.Append('\\',slashes*2).Append('"').ToString();
    }
    public void Start(ProcessStartInfo info) {
        if(job==IntPtr.Zero || process!=IntPtr.Zero || info.UseShellExecute)throw new InvalidOperationException("QA_JOB_STATE");
        if(info.Arguments.Length!=0)throw new InvalidOperationException("QA_ARGUMENT_LIST_REQUIRED");
        string command=Quote(info.FileName)+" "+String.Join(" ",info.ArgumentList.Select(Quote));
        string environment=String.Join("\0",info.Environment.OrderBy(x=>x.Key,StringComparer.OrdinalIgnoreCase).Select(x=>x.Key+"="+x.Value))+"\0\0";
        IntPtr block=Marshal.StringToHGlobalUni(environment);Child child=new Child();
        try {
            var startup=new Startup();startup.cb=(uint)Marshal.SizeOf<Startup>();
            if(!CreateProcess(info.FileName,new StringBuilder(command),IntPtr.Zero,IntPtr.Zero,false,0x08000404,block,info.WorkingDirectory,ref startup,out child))throw new InvalidOperationException("QA_CHILD_CREATE_FAILED");
            process=child.process;ProcessId=(int)child.processId;
            if(!AssignProcessToJobObject(job,process)){TerminateProcess(process,1);throw new InvalidOperationException("QA_JOB_ASSIGN_PERMISSION_REQUIRED");}
            if(ResumeThread(child.thread)==0xFFFFFFFF){TerminateJobObject(job,1);throw new InvalidOperationException("QA_CHILD_RESUME_FAILED");}
        } finally {
            if(child.thread!=IntPtr.Zero)CloseHandle(child.thread);
            // Native environment contains secrets; erase before release.
            for(int i=0;i<environment.Length*2;i++)Marshal.WriteByte(block,i,0);
            Marshal.FreeHGlobal(block);environment=null;
        }
    }
    public bool HasExited {get {return process!=IntPtr.Zero && WaitForSingleObject(process,0)==0;}}
    public bool Stop() {
        if(process==IntPtr.Zero)return true;
        if(!HasExited && !TerminateJobObject(job,1))return false;
        return WaitForSingleObject(process,5000)==0;
    }
    public void Dispose(){Stop();if(process!=IntPtr.Zero){CloseHandle(process);process=IntPtr.Zero;}if(job!=IntPtr.Zero){CloseHandle(job);job=IntPtr.Zero;}}
}
