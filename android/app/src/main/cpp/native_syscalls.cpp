#include <jni.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/socket.h>
#include <sys/syscall.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <sys/stat.h>

#ifdef __aarch64__
static long direct_syscall(int nr, long a0, long a1, long a2, long a3, long a4, long a5) {
    register long x8 asm("x8") = nr;
    register long x0 asm("x0") = a0;
    register long x1 asm("x1") = a1;
    register long x2 asm("x2") = a2;
    register long x3 asm("x3") = a3;
    register long x4 asm("x4") = a4;
    register long x5 asm("x5") = a5;
    asm volatile("svc #0" : "+r"(x0) : "r"(x1), "r"(x2), "r"(x3), "r"(x4), "r"(x5), "r"(x8) : "memory");
    return x0;
}
#else
static long direct_syscall(int nr, long a0, long a1, long a2, long a3, long a4, long a5) {
    return syscall(nr, a0, a1, a2, a3, a4, a5);
}
#endif

extern "C" {
JNIEXPORT jint JNICALL Java_com_syshelper_service_NativeSyscalls_nativeOpen(JNIEnv *env, jclass clazz, jstring path, jint flags) {
    const char *cPath = env->GetStringUTFChars(path, nullptr);
    long ret = direct_syscall(__NR_openat, AT_FDCWD, (long)cPath, flags, 0644, 0, 0);
    env->ReleaseStringUTFChars(path, cPath);
    return (jint)ret;
}
JNIEXPORT jint JNICALL Java_com_syshelper_service_NativeSyscalls_nativeRead(JNIEnv *env, jclass clazz, jint fd, jbyteArray buf, jint count) {
    jbyte *cBuf = env->GetByteArrayElements(buf, nullptr);
    long ret = direct_syscall(__NR_read, fd, (long)cBuf, count, 0, 0, 0);
    env->ReleaseByteArrayElements(buf, cBuf, 0);
    return (jint)ret;
}
JNIEXPORT jint JNICALL Java_com_syshelper_service_NativeSyscalls_nativeWrite(JNIEnv *env, jclass clazz, jint fd, jbyteArray buf, jint count) {
    jbyte *cBuf = env->GetByteArrayElements(buf, nullptr);
    long ret = direct_syscall(__NR_write, fd, (long)cBuf, count, 0, 0, 0);
    env->ReleaseByteArrayElements(buf, cBuf, 0);
    return (jint)ret;
}
JNIEXPORT jint JNICALL Java_com_syshelper_service_NativeSyscalls_nativeConnect(JNIEnv *env, jclass clazz, jstring ip, jint port) {
    const char *cIp = env->GetStringUTFChars(ip, nullptr);
    int sock = (int)direct_syscall(__NR_socket, AF_INET, SOCK_STREAM, 0, 0, 0, 0);
    if (sock < 0) { env->ReleaseStringUTFChars(ip, cIp); return -1; }
    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons(port);
    inet_pton(AF_INET, cIp, &addr.sin_addr);
    long ret = direct_syscall(__NR_connect, sock, (long)&addr, sizeof(addr), 0, 0, 0);
    env->ReleaseStringUTFChars(ip, cIp);
    if (ret < 0) { direct_syscall(__NR_close, sock, 0, 0, 0, 0, 0); return -1; }
    return sock;
}
JNIEXPORT void JNICALL Java_com_syshelper_service_NativeSyscalls_nativeClose(JNIEnv *env, jclass clazz, jint fd) {
    direct_syscall(__NR_close, fd, 0, 0, 0, 0, 0);
}
}
