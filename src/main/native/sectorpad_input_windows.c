/* SectorPad Windows input adapter. No game hooks, patches, replacement libraries or data access. */
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdint.h>
#include <stdlib.h>
#include "jni.h"

#define JNI_FN(name) Java_sectorpad_bridge_WindowsInputOutput_##name
#define EXTRA_INFO ((ULONG_PTR)0x53504354UL)
#define UNUSED(value) ((void)(value))

typedef struct Observer {
    HANDLE thread;
    HANDLE ready;
    DWORD thread_id;
    HHOOK keyboard_hook;
    HHOOK mouse_hook;
    volatile LONG alive;
    volatile LONG observing;
    volatile LONG closing;
    volatile LONG keys[256];
    volatile LONG buttons[3];
    __declspec(align(8)) volatile LONG64 key_releases[256];
    __declspec(align(8)) volatile LONG64 button_releases[3];
    SRWLOCK seed_lock;
} Observer;

/* LL callbacks are delivered on the installing thread, with no calls into the JVM. */
static __declspec(thread) Observer *thread_observer;
static Observer *context(jlong handle) { return (Observer *)(intptr_t)handle; }
static BOOL focused_process(void) {
    DWORD process=0;
    HWND foreground=GetForegroundWindow();
    if(foreground==NULL) return FALSE;
    GetWindowThreadProcessId(foreground,&process);
    return process==GetCurrentProcessId();
}
static BOOL records(Observer *owner) {return owner!=NULL && owner->observing && !owner->closing && focused_process();}

static LRESULT CALLBACK keyboard_event(int code,WPARAM message,LPARAM data) {
    Observer *owner=thread_observer;
    const KBDLLHOOKSTRUCT *event=(const KBDLLHOOKSTRUCT *)data;
    if(code>=0 && records(owner) && (event->flags & LLKHF_INJECTED)==0) {
        int key=event->vkCode==VK_PAUSE ? 197 : (int)(event->scanCode & 0x7fUL) | ((event->flags & LLKHF_EXTENDED) ? 0x80 : 0);
        if(key>0 && key<256) {
            AcquireSRWLockExclusive(&owner->seed_lock);
            if(records(owner)) {
                BOOL down=(event->flags & LLKHF_UP)==0;
                InterlockedExchange(&owner->keys[key],down ? 1 : 0);
                if(!down) InterlockedIncrement64(&owner->key_releases[key]);
            }
            ReleaseSRWLockExclusive(&owner->seed_lock);
        }
    }
    return CallNextHookEx(owner==NULL ? NULL : owner->keyboard_hook,code,message,data);
}
static LRESULT CALLBACK sectorpad_mouse_callback(int code,WPARAM message,LPARAM data) {
    Observer *owner=thread_observer;
    const MSLLHOOKSTRUCT *event=(const MSLLHOOKSTRUCT *)data;
    if(code>=0 && records(owner) && (event->flags & LLMHF_INJECTED)==0) {
        int button=-1; BOOL down=FALSE;
        switch(message) {
            case WM_LBUTTONDOWN:button=0;down=TRUE;break; case WM_LBUTTONUP:button=0;break;
            case WM_RBUTTONDOWN:button=1;down=TRUE;break; case WM_RBUTTONUP:button=1;break;
            case WM_MBUTTONDOWN:button=2;down=TRUE;break; case WM_MBUTTONUP:button=2;break;
            default:break;
        }
        if(button>=0) {
            AcquireSRWLockExclusive(&owner->seed_lock);
            if(records(owner)) {
                InterlockedExchange(&owner->buttons[button],down ? 1 : 0);
                if(!down) InterlockedIncrement64(&owner->button_releases[button]);
            }
            ReleaseSRWLockExclusive(&owner->seed_lock);
        }
    }
    return CallNextHookEx(owner==NULL ? NULL : owner->mouse_hook,code,message,data);
}
static DWORD WINAPI observer_main(LPVOID argument) {
    Observer *owner=(Observer *)argument;
    MSG message;
    HMODULE module=NULL;
    thread_observer=owner;
    PeekMessageW(&message,NULL,0,0,PM_NOREMOVE);
    GetModuleHandleExW(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS | GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
        (LPCWSTR)(uintptr_t)&keyboard_event,&module);
    owner->keyboard_hook=SetWindowsHookExW(WH_KEYBOARD_LL,keyboard_event,module,0);
    owner->mouse_hook=SetWindowsHookExW(WH_MOUSE_LL,sectorpad_mouse_callback,module,0);
    InterlockedExchange(&owner->alive,owner->keyboard_hook!=NULL && owner->mouse_hook!=NULL);
    SetEvent(owner->ready);
    while(owner->alive && !owner->closing) {
        BOOL result=GetMessageW(&message,NULL,0,0);
        if(result<=0) break;
        TranslateMessage(&message); DispatchMessageW(&message);
    }
    InterlockedExchange(&owner->observing,0);
    InterlockedExchange(&owner->alive,0);
    if(owner->keyboard_hook!=NULL) UnhookWindowsHookEx(owner->keyboard_hook);
    if(owner->mouse_hook!=NULL) UnhookWindowsHookEx(owner->mouse_hook);
    owner->keyboard_hook=NULL;owner->mouse_hook=NULL;thread_observer=NULL;
    return 0;
}
static void close_observer(Observer *owner) {
    if(owner==NULL) return;
    InterlockedExchange(&owner->observing,0);InterlockedExchange(&owner->closing,1);
    if(owner->thread!=NULL) {
        PostThreadMessageW(owner->thread_id,WM_QUIT,0,0);
        /* Do not free memory still reachable by an OS callback if shutdown stalls. */
        if(WaitForSingleObject(owner->thread,2000)!=WAIT_OBJECT_0) return;
        CloseHandle(owner->thread);
    }
    if(owner->ready!=NULL) CloseHandle(owner->ready);
    SecureZeroMemory(owner,sizeof(*owner));free(owner);
}
static jint submit(Observer *owner,INPUT *events,UINT count,BOOL needs_focus) {
    UINT sent; DWORD error;
    if(owner==NULL || (needs_focus && (!owner->alive || !owner->observing || !focused_process()))) return ERROR_ACCESS_DENIED;
    SetLastError(ERROR_SUCCESS);sent=SendInput(count,events,sizeof(INPUT));
    if(sent==count)return 0;
    error=GetLastError();return (jint)(error==0 ? ERROR_WRITE_FAULT : error);
}

JNIEXPORT jint JNICALL JNI_FN(nativeAbiVersion)(JNIEnv *env,jclass type) {UNUSED(env);UNUSED(type);return 1;}
JNIEXPORT jlong JNICALL JNI_FN(nativeOpen)(JNIEnv *env,jclass type) {
    Observer *owner=(Observer *)calloc(1,sizeof(Observer));UNUSED(env);UNUSED(type);
    if(owner==NULL)return 0;
    InitializeSRWLock(&owner->seed_lock);
    owner->ready=CreateEventW(NULL,TRUE,FALSE,NULL);
    if(owner->ready==NULL){free(owner);return 0;}
    owner->thread=CreateThread(NULL,0,observer_main,owner,0,&owner->thread_id);
    if(owner->thread==NULL || WaitForSingleObject(owner->ready,2000)!=WAIT_OBJECT_0 || !owner->alive) {close_observer(owner);return 0;}
    return (jlong)(intptr_t)owner;
}
JNIEXPORT jboolean JNICALL JNI_FN(nativeAlive)(JNIEnv *env,jclass type,jlong handle) {
    Observer *owner=context(handle);UNUSED(env);UNUSED(type);return owner!=NULL && owner->alive ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT jboolean JNICALL JNI_FN(nativeObserve)(JNIEnv *env,jclass type,jlong handle,jboolean active,jintArray mapping) {
    Observer *owner=context(handle);jint virtual_keys[256]={0};int index;static const int mouse_keys[3]={VK_LBUTTON,VK_RBUTTON,VK_MBUTTON};UNUSED(type);
    if(owner==NULL || !owner->alive)return JNI_FALSE;
    if(active) {
        if(mapping==NULL || (*env)->GetArrayLength(env,mapping)!=256 || !focused_process())return JNI_FALSE;
        (*env)->GetIntArrayRegion(env,mapping,0,256,virtual_keys);
        if((*env)->ExceptionCheck(env))return JNI_FALSE;
    }
    AcquireSRWLockExclusive(&owner->seed_lock);
    for(index=0;index<256;index++) InterlockedExchange(&owner->keys[index],active && virtual_keys[index]>=0 && (GetAsyncKeyState(virtual_keys[index]) & 0x8000) ? 1 : 0);
    for(index=0;index<3;index++) InterlockedExchange(&owner->buttons[index],active && (GetAsyncKeyState(mouse_keys[index]) & 0x8000) ? 1 : 0);
    InterlockedExchange(&owner->observing,active ? 1 : 0);
    ReleaseSRWLockExclusive(&owner->seed_lock);
    return JNI_TRUE;
}
JNIEXPORT jboolean JNICALL JNI_FN(nativeKeyHeld)(JNIEnv *env,jclass type,jlong handle,jint key) {
    Observer *owner=context(handle);UNUSED(env);UNUSED(type);return owner!=NULL && key>0 && key<256 && owner->keys[key] ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT jboolean JNICALL JNI_FN(nativeButtonHeld)(JNIEnv *env,jclass type,jlong handle,jint button) {
    Observer *owner=context(handle);UNUSED(env);UNUSED(type);return owner!=NULL && button>=0 && button<3 && owner->buttons[button] ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT jlong JNICALL JNI_FN(nativeKeyRelease)(JNIEnv *env,jclass type,jlong handle,jint key) {
    Observer *owner=context(handle);UNUSED(env);UNUSED(type);return owner!=NULL && key>0 && key<256 ? InterlockedCompareExchange64(&owner->key_releases[key],0,0) : 0;
}
JNIEXPORT jlong JNICALL JNI_FN(nativeButtonRelease)(JNIEnv *env,jclass type,jlong handle,jint button) {
    Observer *owner=context(handle);UNUSED(env);UNUSED(type);return owner!=NULL && button>=0 && button<3 ? InterlockedCompareExchange64(&owner->button_releases[button],0,0) : 0;
}
JNIEXPORT jint JNICALL JNI_FN(nativeKey)(JNIEnv *env,jclass type,jlong handle,jint key,jboolean down) {
    INPUT event={0};UNUSED(env);UNUSED(type);event.type=INPUT_KEYBOARD;event.ki.dwExtraInfo=EXTRA_INFO;
    if(key<=0 || key>=256)return ERROR_INVALID_PARAMETER;
    if(key==197){event.ki.wVk=VK_PAUSE;event.ki.dwFlags=down ? 0 : KEYEVENTF_KEYUP;}
    else {event.ki.wScan=(WORD)(key & 0x7f);event.ki.dwFlags=KEYEVENTF_SCANCODE | (key>=128 ? KEYEVENTF_EXTENDEDKEY : 0) | (down ? 0 : KEYEVENTF_KEYUP);}
    return submit(context(handle),&event,1,down ? TRUE : FALSE);
}
JNIEXPORT jint JNICALL JNI_FN(nativeMouse)(JNIEnv *env,jclass type,jlong handle,jint button,jboolean down) {
    static const DWORD pressed[3]={MOUSEEVENTF_LEFTDOWN,MOUSEEVENTF_RIGHTDOWN,MOUSEEVENTF_MIDDLEDOWN};
    static const DWORD released[3]={MOUSEEVENTF_LEFTUP,MOUSEEVENTF_RIGHTUP,MOUSEEVENTF_MIDDLEUP};
    INPUT event={0};UNUSED(env);UNUSED(type);if(button<0 || button>=3)return ERROR_INVALID_PARAMETER;
    event.type=INPUT_MOUSE;event.mi.dwFlags=down ? pressed[button] : released[button];event.mi.dwExtraInfo=EXTRA_INFO;
    return submit(context(handle),&event,1,down ? TRUE : FALSE);
}
JNIEXPORT jint JNICALL JNI_FN(nativeWheel)(JNIEnv *env,jclass type,jlong handle,jint notches) {
    INPUT event={0};UNUSED(env);UNUSED(type);if(notches<-12 || notches>12)return ERROR_INVALID_PARAMETER;
    event.type=INPUT_MOUSE;event.mi.dwFlags=MOUSEEVENTF_WHEEL;event.mi.mouseData=(DWORD)(notches*WHEEL_DELTA);event.mi.dwExtraInfo=EXTRA_INFO;
    return submit(context(handle),&event,1,TRUE);
}
JNIEXPORT jint JNICALL JNI_FN(nativeUnicode)(JNIEnv *env,jclass type,jlong handle,jchar character) {
    INPUT events[2]={{0},{0}};UNUSED(env);UNUSED(type);
    events[0].type=INPUT_KEYBOARD;events[0].ki.wScan=character;events[0].ki.dwFlags=KEYEVENTF_UNICODE;events[0].ki.dwExtraInfo=EXTRA_INFO;
    events[1]=events[0];events[1].ki.dwFlags|=KEYEVENTF_KEYUP;
    return submit(context(handle),events,2,TRUE);
}
JNIEXPORT void JNICALL JNI_FN(nativeClose)(JNIEnv *env,jclass type,jlong handle) {UNUSED(env);UNUSED(type);close_observer(context(handle));}
