// ============================================================================
// fetch.cpp - fully-native online layer. NO Java-side fetch/parse/validate
// methods exist: HTTPS goes through JNI callbacks into HttpURLConnection
// (the NDK has no SSL), and keys.json parsing + validation happen right here
// in compiled ARM code. Also hosts the signature self-check (dex security).
// ============================================================================

#include <jni.h>
#include <cstdio>
#include <cstdlib>
#include "liccore.h"

static JavaVM* g_vm = NULL;
static jclass g_cls_URL = NULL;
static jclass g_cls_Http = NULL;
static jclass g_cls_IS = NULL;

// Called from nativelic.cpp's JNI_OnLoad.
void licFetchInit(JavaVM* vm) {
    g_vm = vm;
}

// ------------------------- HTTPS via JNI -------------------------

// Generic HTTPS request through JNI. Returns "CODE\nBODY" (BODY may be empty).
std::string licHttpReq(const char* method, const char* urlStr,
                       const char* auth, const char* body) {
    if (!g_vm) return "";
    JNIEnv* env = NULL;
    bool attached = false;
    if (g_vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        g_vm->AttachCurrentThread(&env, NULL);
        attached = true;
    }
    std::string result;
    do {
        // lazily cache class refs
        if (!g_cls_URL) {
            jclass c = env->FindClass("java/net/URL");
            if (!c || env->ExceptionCheck()) { env->ExceptionClear(); break; }
            g_cls_URL = (jclass)env->NewGlobalRef(c);
            env->DeleteLocalRef(c);
        }
        if (!g_cls_Http) {
            jclass c = env->FindClass("java/net/HttpURLConnection");
            if (!c || env->ExceptionCheck()) { env->ExceptionClear(); break; }
            g_cls_Http = (jclass)env->NewGlobalRef(c);
            env->DeleteLocalRef(c);
        }
        if (!g_cls_IS) {
            jclass c = env->FindClass("java/io/InputStream");
            if (!c || env->ExceptionCheck()) { env->ExceptionClear(); break; }
            g_cls_IS = (jclass)env->NewGlobalRef(c);
            env->DeleteLocalRef(c);
        }

        jmethodID urlInit = env->GetMethodID(g_cls_URL, "<init>", "(Ljava/lang/String;)V");
        if (!urlInit || env->ExceptionCheck()) { env->ExceptionClear(); break; }
        jstring jUrl = env->NewStringUTF(urlStr);
        jobject urlObj = env->NewObject(g_cls_URL, urlInit, jUrl);
        env->DeleteLocalRef(jUrl);
        if (!urlObj || env->ExceptionCheck()) { env->ExceptionClear(); break; }

        jmethodID openConn = env->GetMethodID(g_cls_URL, "openConnection", "()Ljava/net/URLConnection;");
        if (!openConn || env->ExceptionCheck()) { env->ExceptionClear(); break; }
        jobject conn = env->CallObjectMethod(urlObj, openConn);
        env->DeleteLocalRef(urlObj);
        if (!conn || env->ExceptionCheck()) { env->ExceptionClear(); break; }

        jmethodID m;
        m = env->GetMethodID(g_cls_Http, "setConnectTimeout", "(I)V");
        if (m && !env->ExceptionCheck()) env->CallVoidMethod(conn, m, (jint)15000);
        if (env->ExceptionCheck()) env->ExceptionClear();
        m = env->GetMethodID(g_cls_Http, "setReadTimeout", "(I)V");
        if (m && !env->ExceptionCheck()) env->CallVoidMethod(conn, m, (jint)15000);
        if (env->ExceptionCheck()) env->ExceptionClear();

        m = env->GetMethodID(g_cls_Http, "setRequestMethod", "(Ljava/lang/String;)V");
        if (m && !env->ExceptionCheck()) {
            jstring jm = env->NewStringUTF(method);
            env->CallVoidMethod(conn, m, jm);
            env->DeleteLocalRef(jm);
        }
        if (env->ExceptionCheck()) env->ExceptionClear();

        m = env->GetMethodID(g_cls_Http, "setRequestProperty",
                             "(Ljava/lang/String;Ljava/lang/String;)V");
        if (m && !env->ExceptionCheck()) {
            if (auth) {
                jstring k = env->NewStringUTF("Authorization");
                std::string av = std::string("token ") + auth;
                jstring v = env->NewStringUTF(av.c_str());
                env->CallVoidMethod(conn, m, k, v);
                env->DeleteLocalRef(k);
                env->DeleteLocalRef(v);
            }
            if (body) {
                jstring k = env->NewStringUTF("Content-Type");
                jstring v = env->NewStringUTF("application/json");
                env->CallVoidMethod(conn, m, k, v);
                env->DeleteLocalRef(k);
                env->DeleteLocalRef(v);
            }
        }
        if (env->ExceptionCheck()) env->ExceptionClear();

        if (body) {
            m = env->GetMethodID(g_cls_Http, "setDoOutput", "(Z)V");
            if (m && !env->ExceptionCheck()) env->CallVoidMethod(conn, m, JNI_TRUE);
            if (env->ExceptionCheck()) env->ExceptionClear();
            m = env->GetMethodID(g_cls_Http, "getOutputStream", "()Ljava/io/OutputStream;");
            jobject os = (m && !env->ExceptionCheck()) ? env->CallObjectMethod(conn, m) : NULL;
            if (env->ExceptionCheck()) env->ExceptionClear();
            if (os) {
                jclass clsOS = env->FindClass("java/io/OutputStream");
                jmethodID w = env->GetMethodID(clsOS, "write", "([B)V");
                jmethodID f = env->GetMethodID(clsOS, "flush", "()V");
                jmethodID cl = env->GetMethodID(clsOS, "close", "()V");
                size_t blen = strlen(body);
                jbyteArray bb = env->NewByteArray((jsize)blen);
                env->SetByteArrayRegion(bb, 0, (jsize)blen, (const jbyte*)body);
                if (w) env->CallVoidMethod(os, w, bb);
                if (f) env->CallVoidMethod(os, f);
                if (cl) env->CallVoidMethod(os, cl);
                env->DeleteLocalRef(bb);
                env->DeleteLocalRef(os);
                if (env->ExceptionCheck()) env->ExceptionClear();
            }
        }

        m = env->GetMethodID(g_cls_Http, "getResponseCode", "()I");
        if (!m || env->ExceptionCheck()) { env->ExceptionClear(); break; }
        jint code = env->CallIntMethod(conn, m);
        if (env->ExceptionCheck()) { env->ExceptionClear(); break; }

        char codeBuf[16];
        snprintf(codeBuf, sizeof(codeBuf), "%d\n", (int)code);
        result = codeBuf;

        jobject is = NULL;
        if (code >= 200 && code < 300) {
            m = env->GetMethodID(g_cls_Http, "getInputStream", "()Ljava/io/InputStream;");
            if (m && !env->ExceptionCheck()) is = env->CallObjectMethod(conn, m);
        } else {
            m = env->GetMethodID(g_cls_Http, "getErrorStream", "()Ljava/io/InputStream;");
            if (m && !env->ExceptionCheck()) is = env->CallObjectMethod(conn, m);
        }
        if (env->ExceptionCheck()) env->ExceptionClear();

        if (is) {
            jmethodID readM = env->GetMethodID(g_cls_IS, "read", "([B)I");
            if (readM && !env->ExceptionCheck()) {
                jbyteArray buf = env->NewByteArray(8192);
                jint n;
                jbyte* tmp = (jbyte*)malloc(8192);
                while ((n = env->CallIntMethod(is, readM, buf)) > 0) {
                    if (env->ExceptionCheck()) { env->ExceptionClear(); break; }
                    env->GetByteArrayRegion(buf, 0, n, tmp);
                    result.append((const char*)tmp, n);
                }
                free(tmp);
                env->DeleteLocalRef(buf);
            }
            env->DeleteLocalRef(is);
        }
        env->DeleteLocalRef(conn);
        if (env->ExceptionCheck()) env->ExceptionClear();
    } while (false);
    if (attached) g_vm->DetachCurrentThread();
    return result;
}

// Simple GET: body on HTTP 200, empty string otherwise.
std::string licFetchUrl(const char* urlStr) {
    std::string r = licHttpReq("GET", urlStr, NULL, NULL);
    if (r.compare(0, 4, "200\n") == 0) return r.substr(4);
    return "";
}

// ------------------------- minimal JSON extractors -------------------------
// Schema is ours and stable: {"version":..,"keys":[{...}],"banned_devices":[..]}
// The shared helpers (licJsBool/licJsInt/licJsArray/licJsObjects/licJsStrList)
// live in liccore.h; only licJsStr is defined here (declared in liccore.h).

std::string licJsStr(const std::string& obj, const char* field) {
    std::string token = std::string("\"") + field + "\"";
    size_t p = obj.find(token);
    if (p == std::string::npos) return "";
    p = obj.find(':', p + token.size());
    if (p == std::string::npos) return "";
    p = obj.find('"', p + 1);
    if (p == std::string::npos) return "";
    // scan to closing quote, unescaping \" \\ \n \t on the way
    std::string out;
    for (size_t i = p + 1; i < obj.size(); i++) {
        char c = obj[i];
        if (c == '"') return out;
        if (c == '\\' && i + 1 < obj.size()) {
            char n = obj[++i];
            if (n == 'n') out += '\n';
            else if (n == 't') out += '\t';
            else if (n == 'r') out += '\r';
            else out += n; // \" \\ \/ etc.
        } else out += c;
    }
    return "";
}

// Device list: accepts both plain string arrays (app writes hashes) and
// object arrays (admin panel writes {id:.., name:..}); returns hash list.
static std::string deviceList(const std::string& arr) {
    if (arr.find('{') == std::string::npos) return licJsStrList(arr);
    std::vector<std::string> objs = licJsObjects(arr);
    std::string out;
    for (size_t i = 0; i < objs.size(); i++) {
        std::string id = licJsStr(objs[i], "id");
        if (id.empty()) id = licJsStr(objs[i], "hash");
        if (id.empty()) continue;
        if (!out.empty()) out += ',';
        out += id;
    }
    return out;
}

// Flatten the whole keys.json into the decision format natively.
static void flattenKeysJson(const std::string& json, std::string& flatOut, std::string& bannedOut) {
    bannedOut = licJsStrList(licJsArray(json, "banned_devices"));
    std::vector<std::string> objs = licJsObjects(licJsArray(json, "keys"));
    std::string flat;
    for (size_t i = 0; i < objs.size(); i++) {
        const std::string& o = objs[i];
        flat += licJsStr(o, "key");
        flat += '\t';
        flat += licJsBool(o, "active") ? "1" : "0";
        flat += '\t';
        {
            char buf[16];
            snprintf(buf, sizeof(buf), "%ld", licJsInt(o, "minutes", 0));
            flat += buf;
        }
        flat += '\t';
        flat += licJsStr(o, "expiry");
        flat += '\t';
        {
            char buf[16];
            snprintf(buf, sizeof(buf), "%ld", licJsInt(o, "max_devices", 1));
            flat += buf;
        }
        flat += '\t';
        flat += deviceList(licJsArray(o, "devices"));
        flat += '\t';
        {
            // expires_at ISO -> epoch ms (0 = not activated yet)
            char buf[24];
            snprintf(buf, sizeof(buf), "%lld", licIsoMs(licJsStr(o, "expires_at")));
            flat += buf;
        }
        flat += '\t';
        {
            // premium duration in days (countdown starts on import)
            char buf[16];
            snprintf(buf, sizeof(buf), "%ld", licJsInt(o, "days", 0));
            flat += buf;
        }
        flat += '\t';
        {
            // import_deadline ISO -> epoch ms (0 = none)
            char buf[24];
            snprintf(buf, sizeof(buf), "%lld", licIsoMs(licJsStr(o, "import_deadline")));
            flat += buf;
        }
        flat += '\n';
    }
    flatOut = flat;
}

// ------------------------- JNI exports -------------------------

extern "C" {

// FULLY NATIVE online validation: fetch + parse + decide, zero Java logic.
// out[0] = untilMs (0 = lifetime), out[1] = isTrial(0/1). Returns RC_* code.
JNIEXPORT jint JNICALL
Java_myscanne_com_X_m(JNIEnv* env, jclass,
                                           jstring jkey, jstring jdev, jlong now,
                                           jlongArray out) {
    const char* key = env->GetStringUTFChars(jkey, 0);
    const char* dev = env->GetStringUTFChars(jdev, 0);
    std::string sKey(key), sDev(dev);
    env->ReleaseStringUTFChars(jkey, key);
    env->ReleaseStringUTFChars(jdev, dev);

    std::string url = licKeysUrl();
    std::string json = licFetchUrl(url.c_str());

    jint rc = RC_NET;
    long long until = 0;
    int isTrial = 0;
    if (!json.empty()) {
        std::string flat, banned;
        flattenKeysJson(json, flat, banned);
        rc = licCoreDecision(flat, sKey, sDev, banned, (long long)now, until, isTrial);
    }

    jlong buf[2];
    buf[0] = (jlong)until;
    buf[1] = (jlong)isTrial;
    env->SetLongArrayRegion(out, 0, 2, buf);
    return rc;
}

// Raw keys.json for the admin view (fetched natively; Java only displays it).
JNIEXPORT jstring JNICALL
Java_myscanne_com_X_n(JNIEnv* env, jclass) {
    std::string url = licKeysUrl();
    std::string json = licFetchUrl(url.c_str());
    return env->NewStringUTF(json.c_str());
}

// Block reasons / result texts - kept native so smali strings reveal nothing.
JNIEXPORT jstring JNICALL
Java_myscanne_com_X_o(JNIEnv* env, jclass, jint code) {
    const char* t;
    switch (code) {
        case RC_INVALID:  t = "Invalid key"; break;
        case RC_EXPIRED:  t = "Key expired"; break;
        case RC_DEVLIMIT: t = "Device limit reached"; break;
        case RC_INACTIVE: t = "Key deactivated by admin"; break;
        case RC_BANNED:   t = "Device banned"; break;
        case 10:          t = "Free plan: single scans used up."; break;
        case 11:          t = "Free plan: file scans used up."; break;
        default:          t = "Network error - check connection"; break;
    }
    return env->NewStringUTF(t);
}

} // extern "C"
