#include <jni.h>
#include <string>
#include <cstring>
#include <cstdlib>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <netdb.h>
#include <sys/socket.h>
#include <sys/select.h>
#include <sys/time.h>
#include <arpa/inet.h>
#include <netinet/in.h>

// ============================================================================
// Scanner Pro native scan core: everything that doesn't need TLS runs here in
// compiled ARM code - TCP connect/port scan, TCP ping, DNS resolve, and plain
// HTTP/1.1 probing. TLS-based probes (SNI/TLS/cert/WS) stay on Java SSLSocket
// (the NDK ships no SSL library).
// ============================================================================

// connect with timeout; returns 0 on success
static int tcpConnect(const char* ip, int port, int timeoutMs) {
    int fd = socket(AF_INET, SOCK_STREAM, 0);
    if (fd < 0) return -1;
    fcntl(fd, F_SETFL, O_NONBLOCK);

    sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons((unsigned short)port);
    inet_pton(AF_INET, ip, &addr.sin_addr);

    int r = connect(fd, (sockaddr*)&addr, sizeof(addr));
    if (r < 0 && errno == EINPROGRESS) {
        fd_set wfds;
        FD_ZERO(&wfds);
        FD_SET(fd, &wfds);
        timeval tv;
        tv.tv_sec = timeoutMs / 1000;
        tv.tv_usec = (timeoutMs % 1000) * 1000;
        r = select(fd + 1, NULL, &wfds, NULL, &tv);
        if (r > 0) {
            int err = 0;
            socklen_t len = sizeof(err);
            getsockopt(fd, SOL_SOCKET, SO_ERROR, &err, &len);
            r = (err == 0) ? 0 : -1;
        } else {
            r = -1;
        }
    }
    close(fd);
    return r;
}

static bool resolveHost(const char* host, char* outIp, size_t outLen) {
    addrinfo hints;
    memset(&hints, 0, sizeof(hints));
    hints.ai_family = AF_INET;
    hints.ai_socktype = SOCK_STREAM;
    addrinfo* res = NULL;
    if (getaddrinfo(host, NULL, &hints, &res) != 0 || res == NULL) return false;
    sockaddr_in* a = (sockaddr_in*)res->ai_addr;
    const char* p = inet_ntop(AF_INET, &a->sin_addr, outIp, outLen);
    freeaddrinfo(res);
    return p != NULL;
}

static long long nowMs() {
    timeval tv;
    gettimeofday(&tv, NULL);
    return (long long)tv.tv_sec * 1000 + tv.tv_usec / 1000;
}

extern "C" {

// DNS resolve -> dotted IPv4 ("" on failure)
JNIEXPORT jstring JNICALL
Java_myscanne_com_NativeScan_resolve(JNIEnv* env, jclass, jstring jhost) {
    const char* host = env->GetStringUTFChars(jhost, 0);
    char ip[64] = {0};
    bool ok = resolveHost(host, ip, sizeof(ip));
    env->ReleaseStringUTFChars(jhost, host);
    return env->NewStringUTF(ok ? ip : "");
}

// TCP connect check (port scan primitive)
JNIEXPORT jboolean JNICALL
Java_myscanne_com_NativeScan_tcpOpen(JNIEnv* env, jclass,
                                     jstring jhost, jint port, jint timeoutMs) {
    const char* host = env->GetStringUTFChars(jhost, 0);
    char ip[64] = {0};
    bool ok = resolveHost(host, ip, sizeof(ip));
    if (!ok) { // maybe already an IP literal
        strncpy(ip, host, sizeof(ip) - 1);
        struct sockaddr_in sin;
        memset(&sin, 0, sizeof(sin));
        ok = inet_pton(AF_INET, ip, &(sin.sin_addr)) == 1;
        if (!ok) ok = tcpConnect(ip, port, timeoutMs) == 0; // last-resort try
    }
    env->ReleaseStringUTFChars(jhost, host);
    if (!ok) return JNI_FALSE;
    return tcpConnect(ip, port, timeoutMs) == 0 ? JNI_TRUE : JNI_FALSE;
}

// TCP ping on port 443. out = {avgMs, minMs, maxMs, received}
JNIEXPORT jint JNICALL
Java_myscanne_com_NativeScan_tcpPing(JNIEnv* env, jclass,
                                     jstring jhost, jint port, jint count,
                                     jint timeoutMs, jlongArray out) {
    const char* host = env->GetStringUTFChars(jhost, 0);
    char ip[64] = {0};
    if (!resolveHost(host, ip, sizeof(ip))) strncpy(ip, host, sizeof(ip) - 1);
    env->ReleaseStringUTFChars(jhost, host);

    long long mn = 1LL << 62, mx = 0, sum = 0;
    int recv = 0;
    for (int i = 0; i < count; i++) {
        long long t0 = nowMs();
        if (tcpConnect(ip, port, timeoutMs) == 0) {
            long long ms = nowMs() - t0;
            if (ms < mn) mn = ms;
            if (ms > mx) mx = ms;
            sum += ms;
            recv++;
        }
        usleep(200 * 1000);
    }
    jlong buf[4];
    buf[0] = recv > 0 ? sum / recv : 0;
    buf[1] = recv > 0 ? mn : 0;
    buf[2] = mx;
    buf[3] = recv;
    env->SetLongArrayRegion(out, 0, 4, buf);
    return recv;
}

// Plain HTTP/1.1 probe on port 80: sends HEAD, returns status code (0 = fail)
JNIEXPORT jint JNICALL
Java_myscanne_com_NativeScan_httpCode(JNIEnv* env, jclass,
                                      jstring jhost, jstring jpath,
                                      jint timeoutMs) {
    const char* host = env->GetStringUTFChars(jhost, 0);
    const char* path = env->GetStringUTFChars(jpath, 0);
    std::string sHost(host), sPath(path);
    env->ReleaseStringUTFChars(jhost, host);
    env->ReleaseStringUTFChars(jpath, path);

    char ip[64] = {0};
    if (!resolveHost(sHost.c_str(), ip, sizeof(ip))) return 0;

    int fd = socket(AF_INET, SOCK_STREAM, 0);
    if (fd < 0) return 0;
    timeval tv;
    tv.tv_sec = timeoutMs / 1000;
    tv.tv_usec = (timeoutMs % 1000) * 1000;
    setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
    setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv));

    sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons(80);
    inet_pton(AF_INET, ip, &addr.sin_addr);
    if (connect(fd, (sockaddr*)&addr, sizeof(addr)) != 0) { close(fd); return 0; }

    std::string req = "HEAD " + sPath + " HTTP/1.1\r\nHost: " + sHost +
                      "\r\nConnection: close\r\n\r\n";
    if (send(fd, req.c_str(), req.size(), 0) <= 0) { close(fd); return 0; }

    char buf[256];
    int n = (int)recv(fd, buf, sizeof(buf) - 1, 0);
    close(fd);
    if (n <= 0) return 0;
    buf[n] = 0;
    // "HTTP/1.1 200 OK"
    if (strncmp(buf, "HTTP/", 5) != 0) return 0;
    const char* sp = strchr(buf, ' ');
    if (!sp) return 0;
    return atoi(sp + 1);
}

} // extern "C"
