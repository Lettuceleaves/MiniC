// Host-only benchmark observer. Never linked into MiniC products or measured workloads.
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <shellapi.h>
#include <cstdio>
#include <string>
#include <thread>
#include <atomic>
#include <algorithm>

struct Handle {
    HANDLE value;
    explicit Handle(HANDLE v=nullptr):value(v){}
    ~Handle(){reset();}
    Handle(const Handle&)=delete;Handle& operator=(const Handle&)=delete;
    void reset(HANDLE v=nullptr){if(value&&value!=INVALID_HANDLE_VALUE)CloseHandle(value);value=v;}
    bool valid()const{return value&&value!=INVALID_HANDLE_VALUE;}
};
struct Capture {
    HANDLE pipe;
    HANDLE file;
    unsigned long long limit;
    std::atomic<bool> exceeded{false};
    std::atomic<DWORD> error{0};
    void drain(){
        char buffer[8192];DWORD count=0;unsigned long long kept=0;
        while(ReadFile(pipe,buffer,sizeof(buffer),&count,nullptr)&&count){
            DWORD accepted=(DWORD)std::min<unsigned long long>(count,limit-kept),written=0;
            if(accepted&&(!WriteFile(file,buffer,accepted,&written,nullptr)||written!=accepted)){error=GetLastError();if(!error)error=ERROR_WRITE_FAULT;}
            kept+=accepted;if(accepted<count)exceeded=true;
        }
        DWORD code=GetLastError();if(code!=ERROR_BROKEN_PIPE&&code!=ERROR_SUCCESS)error=code;
    }
};
struct Result {
    const char* status="tool_error";
    DWORD exitCode=0,error=0;
    unsigned long long wall=0,user=0,kernel=0,peak=0;
};
static unsigned long long ticks(FILETIME value){return ((unsigned long long)value.dwHighDateTime<<32)|value.dwLowDateTime;}
static unsigned long long nanos(LONGLONG value,LONGLONG frequency){return (value/frequency)*1000000000ULL+(value%frequency)*1000000000ULL/frequency;}
static std::wstring option(int argc,wchar_t** argv,const wchar_t* name){
    std::wstring prefix=std::wstring(L"--")+name+L"=";
    std::wstring value;bool seen=false;
    for(int i=1;i<argc;++i)if(std::wstring(argv[i]).compare(0,prefix.size(),prefix)==0){if(seen)return L"";seen=true;value=argv[i]+prefix.size();}
    return value;
}
static bool number(const std::wstring& text,unsigned long long& result){
    if(text.empty())return false;result=0;
    for(wchar_t c:text){if(c<L'0'||c>L'9'||result>3600000000ULL)return false;result=result*10+c-L'0';}
    return true;
}
static Result execute(const std::wstring& exe,const std::wstring& input,const std::wstring& output,const std::wstring& errors,
                      unsigned long long timeout,unsigned long long cap){
    Result r;Handle job(CreateJobObjectW(nullptr,nullptr));
    if(!job.valid()){r.error=GetLastError();return r;}
    JOBOBJECT_EXTENDED_LIMIT_INFORMATION limits={};limits.BasicLimitInformation.LimitFlags=JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
    if(!SetInformationJobObject(job.value,JobObjectExtendedLimitInformation,&limits,sizeof(limits))){r.error=GetLastError();return r;}
    SECURITY_ATTRIBUTES inherited={sizeof(SECURITY_ATTRIBUTES),nullptr,TRUE};
    Handle in(CreateFileW(input.c_str(),GENERIC_READ,FILE_SHARE_READ,&inherited,OPEN_EXISTING,FILE_ATTRIBUTE_NORMAL,nullptr));
    Handle outFile(CreateFileW(output.c_str(),GENERIC_WRITE,FILE_SHARE_READ,nullptr,CREATE_NEW,FILE_ATTRIBUTE_NORMAL,nullptr));
    Handle errFile(CreateFileW(errors.c_str(),GENERIC_WRITE,FILE_SHARE_READ,nullptr,CREATE_NEW,FILE_ATTRIBUTE_NORMAL,nullptr));
    if(!in.valid()||!outFile.valid()||!errFile.valid()){r.error=GetLastError();return r;}
    HANDLE oraw=nullptr,owraw=nullptr,eraw=nullptr,ewraw=nullptr;
    if(!CreatePipe(&oraw,&owraw,&inherited,0)){r.error=GetLastError();return r;}
    Handle outRead(oraw),outWrite(owraw);
    if(!CreatePipe(&eraw,&ewraw,&inherited,0)){r.error=GetLastError();return r;}
    Handle errRead(eraw),errWrite(ewraw);
    if(!SetHandleInformation(outRead.value,HANDLE_FLAG_INHERIT,0)||!SetHandleInformation(errRead.value,HANDLE_FLAG_INHERIT,0)){r.error=GetLastError();return r;}
    STARTUPINFOW startup={};startup.cb=sizeof(startup);startup.dwFlags=STARTF_USESTDHANDLES|STARTF_USESHOWWINDOW;
    startup.wShowWindow=SW_HIDE;startup.hStdInput=in.value;startup.hStdOutput=outWrite.value;startup.hStdError=errWrite.value;
    PROCESS_INFORMATION process={};std::wstring command=L"\""+exe+L"\"";
    if(!CreateProcessW(exe.c_str(),&command[0],nullptr,nullptr,TRUE,CREATE_SUSPENDED|CREATE_NO_WINDOW,nullptr,nullptr,&startup,&process)){r.error=GetLastError();return r;}
    Handle child(process.hProcess),thread(process.hThread);
    outWrite.reset();errWrite.reset();
    if(!AssignProcessToJobObject(job.value,child.value)){r.error=GetLastError();TerminateProcess(child.value,125);WaitForSingleObject(child.value,2000);return r;}
    Capture out{outRead.value,outFile.value,cap},err{errRead.value,errFile.value,cap};
    std::thread outThread(&Capture::drain,&out),errThread(&Capture::drain,&err);
    LARGE_INTEGER frequency,start,end;QueryPerformanceFrequency(&frequency);QueryPerformanceCounter(&start);
    if(ResumeThread(thread.value)==(DWORD)-1){r.error=GetLastError();TerminateJobObject(job.value,125);}
    else {
        r.status="completed";
        for(;;){
            DWORD state=WaitForSingleObject(child.value,10);QueryPerformanceCounter(&end);
            if(out.exceeded||err.exceeded){r.status="output_limit";break;}
            if(out.error||err.error){r.status="tool_error";r.error=out.error?out.error.load():err.error.load();break;}
            if(state==WAIT_OBJECT_0)break;
            if(state==WAIT_FAILED){r.status="tool_error";r.error=GetLastError();break;}
            if(nanos(end.QuadPart-start.QuadPart,frequency.QuadPart)>=timeout*1000000ULL){r.status="timeout";break;}
        }
    }
    QueryPerformanceCounter(&end);r.wall=nanos(end.QuadPart-start.QuadPart,frequency.QuadPart);
    // Kill-on-close and explicit termination also close pipes held by descendants.
    if(!TerminateJobObject(job.value,124)){r.status="tool_error";r.error=GetLastError();}
    if(WaitForSingleObject(child.value,5000)!=WAIT_OBJECT_0){r.status="tool_error";r.error=ERROR_TIMEOUT;}
    FILETIME creation,exit,kernel,user;
    if(!GetExitCodeProcess(child.value,&r.exitCode)||!GetProcessTimes(child.value,&creation,&exit,&kernel,&user)){
        r.status="tool_error";r.error=GetLastError();
    } else {r.user=ticks(user)*100;r.kernel=ticks(kernel)*100;}
    JOBOBJECT_EXTENDED_LIMIT_INFORMATION observed={};
    if(!QueryInformationJobObject(job.value,JobObjectExtendedLimitInformation,&observed,sizeof(observed),nullptr)){
        r.status="tool_error";r.error=GetLastError();
    }else r.peak=(unsigned long long)observed.PeakProcessMemoryUsed;
    job.reset();outThread.join();errThread.join();
    if(out.exceeded||err.exceeded)r.status="output_limit";
    if(out.error||err.error){r.status="tool_error";r.error=out.error?out.error.load():err.error.load();}
    return r;
}
int WINAPI wWinMain(HINSTANCE,HINSTANCE,LPWSTR,int){
    int argc=0;wchar_t** argv=CommandLineToArgvW(GetCommandLineW(),&argc);if(!argv)return 125;
    auto report=option(argc,argv,L"report"),exe=option(argc,argv,L"exe"),input=option(argc,argv,L"stdin"),output=option(argc,argv,L"stdout"),errors=option(argc,argv,L"stderr");
    unsigned long long timeout=0,cap=0;Result result;
    if(report.empty()||exe.empty()||input.empty()||output.empty()||errors.empty()||!number(option(argc,argv,L"timeout-ms"),timeout)
       ||!number(option(argc,argv,L"output-limit"),cap)||!timeout||timeout>3600000||!cap||cap>67108864)result.error=ERROR_INVALID_PARAMETER;
    else result=execute(exe,input,output,errors,timeout,cap);
    FILE* file=_wfopen(report.c_str(),L"wb");if(!file){LocalFree(argv);return 125;}
    std::fprintf(file,"schemaVersion=1\nstatus=%s\nexitCode=%lu\nwin32Error=%lu\nwallNanos=%llu\nuserCpuNanos=%llu\nkernelCpuNanos=%llu\npeakCommitBytes=%llu\n",
        result.status,(unsigned long)result.exitCode,(unsigned long)result.error,result.wall,result.user,result.kernel,result.peak);
    bool ok=std::fclose(file)==0;LocalFree(argv);return ok?0:125;
}
