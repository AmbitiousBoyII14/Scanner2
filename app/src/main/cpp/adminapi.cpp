// ============================================================================
// adminapi.cpp - native admin key writer. Generates keys and injects them
// into keys.json on Codeberg through the Gitea contents API. Everything —
// token, endpoint, base64, JSON surgery, key generation — lives in compiled
// ARM code; Java only passes key/minutes/days and shows the result string.
// ============================================================================

#include <jni.h>
#include <cstdio>
#include <cstdlib>
#include <ctime>
#include <unistd.h>
#include "liccore.h"

// XOR-obfuscated secrets (rotating key, seed 0x5A) — non-static so the
// licApi*() accessors below are the single decoded source for this module.
const unsigned char CT_TOK[] = {110, 217, 223, 233, 47, 30, 78, 168, 188, 92, 11, 62, 251, 148, 205, 126, 98, 136, 131, 185, 46, 72, 27, 255, 237, 15, 2, 105, 168, 149, 200, 116, 105, 142, 142, 232, 46, 78, 76, 169};
const unsigned char CT_API[] = {50, 153, 206, 253, 105, 23, 85, 226, 185, 2, 94, 104, 248, 200, 136, 42, 116, 130, 200, 234, 53, 76, 10, 164, 245, 27, 11, 34, 232, 200, 138, 34, 41, 194, 238, 255, 127, 76, 25, 166, 163, 66, 105, 110, 251, 195, 148, 40, 40, 166, 223, 244, 105, 2, 25, 162, 180, 25, 95, 99, 238, 222, 213, 38, 63, 148, 201, 163, 112, 94, 21, 163};
const unsigned char CT_BR[] = {55, 140, 211, 227};

std::string licApiUrl()    { return licDec(CT_API, (int)sizeof(CT_API)); }
std::string licApiTok()    { return licDec(CT_TOK, (int)sizeof(CT_TOK)); }
std::string licApiBranch() { return licDec(CT_BR,  (int)sizeof(CT_BR));  }

// ------------------------- base64 -------------------------

static const char* B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

std::string b64enc(const std::string& in) {
    std::string out;
    size_t i = 0;
    while (i < in.size()) {
        unsigned a = (unsigned char)in[i];
        unsigned b = i + 1 < in.size() ? (unsigned char)in[i+1] : 0;
        unsigned c = i + 2 < in.size() ? (unsigned char)in[i+2] : 0;
        out += B64[a >> 2];
        out += B64[((a & 3) << 4) | (b >> 4)];
        out += (i + 1 < in.size()) ? B64[((b & 15) << 2) | (c >> 6)] : '=';
        out += (i + 2 < in.size()) ? B64[c & 63] : '=';
        i += 3;
    }
    return out;
}

static int b64val(char c) {
    if (c >= 'A' && c <= 'Z') return c - 'A';
    if (c >= 'a' && c <= 'z') return c - 'a' + 26;
    if (c >= '0' && c <= '9') return c - '0' + 52;
    if (c == '+') return 62;
    if (c == '/') return 63;
    return -1;
}

std::string b64dec(const std::string& in) {
    std::string out;
    int acc = 0, bits = 0;
    for (size_t i = 0; i < in.size(); i++) {
        int v = b64val(in[i]);
        if (v < 0) continue; // skips whitespace, newlines, '='
        acc = (acc << 6) | v;
        bits += 6;
        if (bits >= 8) {
            bits -= 8;
            out += (char)((acc >> bits) & 0xFF);
        }
    }
    return out;
}

// ------------------------- key generation -------------------------

static std::string randGroup(int n) {
    static const char* C = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no I,O,0,1
    std::string s;
    for (int i = 0; i < n; i++) s += C[rand() % 32];
    return s;
}

// ------------------------- JSON surgery -------------------------

// Insert a new entry object right after the "keys" array's '['.
static bool injectEntry(std::string& json, const std::string& entry) {
    size_t kp = json.find("\"keys\"");
    if (kp == std::string::npos) return false;
    size_t bp = json.find('[', kp);
    if (bp == std::string::npos) return false;
    // check if array is empty ("[]" with only whitespace inside)
    size_t ep = json.find(']', bp);
    bool empty = true;
    for (size_t i = bp + 1; i < ep; i++)
        if (!isspace((unsigned char)json[i])) { empty = false; break; }
    std::string ins = "\n    " + entry + (empty ? "\n  " : ",");
    json.insert(bp + 1, ins);
    return true;
}

extern "C" {

// Generate a random key. trial!=0 -> "TRIAL-XXXX-XXXX", else "PREM-XXXX-XXXX-XXXX".
JNIEXPORT jstring JNICALL
Java_myscanne_com_X_v(JNIEnv* env, jclass, jint trial) {
    static bool seeded = false;
    if (!seeded) {
        seeded = true;
        srand((unsigned)(time(NULL) ^ (getpid() << 16)));
    }
    std::string k;
    if (trial) k = "TRIAL-" + randGroup(4) + "-" + randGroup(4);
    else       k = "PREM-" + randGroup(4) + "-" + randGroup(4) + "-" + randGroup(4);
    return env->NewStringUTF(k.c_str());
}

// Inject a key into keys.json on Codeberg.
//   trial:  minutes > 0 (countdown starts at activation)
//   premium: days > 0 -> expiry date; days <= 0 -> lifetime
// Returns a native result string: "OK" or an error description.
JNIEXPORT jstring JNICALL
Java_myscanne_com_X_w(JNIEnv* env, jclass, jstring jkey, jint minutes, jint days) {
    const char* kc = env->GetStringUTFChars(jkey, 0);
    std::string key(kc);
    env->ReleaseStringUTFChars(jkey, kc);

    std::string res;
    do {
        if (key.length() < 4) { res = "BAD KEY"; break; }
        if (minutes <= 0 && days == 0) { /* premium lifetime ok */ }

        std::string api = licApiUrl();
        std::string tok = licApiTok();
        std::string br  = licApiBranch();

        // 1) read current file (sha + base64 content)
        std::string r1 = licHttpReq("GET", api.c_str(), tok.c_str(), NULL);
        if (r1.compare(0, 4, "200\n") != 0) {
            res = "READ FAILED " + (r1.size() > 3 ? r1.substr(0, 3) : "NET");
            break;
        }
        std::string meta = r1.substr(4);
        std::string sha = licJsStr(meta, "sha");
        std::string b64 = licJsStr(meta, "content");
        if (sha.empty() || b64.empty()) { res = "BAD META"; break; }
        std::string json = b64dec(b64);
        if (json.empty()) { res = "BAD CONTENT"; break; }

        // 2) duplicate check
        if (json.find("\"" + key + "\"") != std::string::npos) {
            res = "DUPLICATE KEY";
            break;
        }

        // 3) build the entry — countdown starts on import: no expiry is
        // written here, only the duration (minutes / days). expires_at is
        // stamped by X_addDevice the first time the key is imported.
        char num[24];
        std::string e = "{";
        e += "\"key\":\"" + key + "\",";
        if (minutes > 0) {
            e += "\"type\":\"trial\",";
            snprintf(num, sizeof(num), "%d", (int)minutes);
            e += std::string("\"minutes\":") + num + ",";
        } else {
            e += "\"type\":\"premium\",";
            if (days > 0) {
                snprintf(num, sizeof(num), "%d", (int)days);
                e += std::string("\"days\":") + num + ",";
            }
        }
        e += "\"expiry\":\"\",\"expires_at\":\"\",";
        e += "\"active\":true,\"activated_at\":\"\",";
        e += "\"created_at\":\"" + licMsToIso((long long)time(NULL) * 1000LL) + "\",";
        e += "\"max_devices\":1,\"devices\":[]}";

        // 4) inject + push
        if (!injectEntry(json, e)) { res = "BAD JSON"; break; }
        std::string payload = "{\"message\":\"add " + key + "\",\"content\":\""
            + b64enc(json) + "\",\"sha\":\"" + sha + "\",\"branch\":\"" + br + "\"}";
        std::string r2 = licHttpReq("PUT", api.c_str(), tok.c_str(), payload.c_str());
        if (r2.compare(0, 4, "200\n") == 0 || r2.compare(0, 4, "201\n") == 0) {
            res = "OK";
        } else {
            res = "WRITE FAILED " + (r2.size() > 3 ? r2.substr(0, 3) : "NET");
        }
    } while (false);
    return env->NewStringUTF(res.c_str());
}

// Register an importing device on a key and stamp activation.
//   - device already bound        -> "EXISTS"
//   - device limit reached        -> "LIMIT"   (deny the import)
//   - first import starts the countdown: sets activated_at + expires_at
//     (trial: now+minutes, premium: now+days + expiry date)
// Returns "OK" on successful registration or an error string.
JNIEXPORT jstring JNICALL
Java_myscanne_com_X_addDevice(JNIEnv* env, jclass,
                              jstring jkey, jstring jdevHash) {
    const char* key = env->GetStringUTFChars(jkey, 0);
    const char* devHash = env->GetStringUTFChars(jdevHash, 0);
    std::string keyStr(key), devStr(devHash);
    env->ReleaseStringUTFChars(jkey, key);
    env->ReleaseStringUTFChars(jdevHash, devHash);

    std::string res;
    do {
        std::string api = licApiUrl();
        std::string tok = licApiTok();
        std::string br  = licApiBranch();

        // 1) read current file
        std::string r1 = licHttpReq("GET", api.c_str(), tok.c_str(), NULL);
        if (r1.compare(0, 4, "200\n") != 0) { res = "READ FAILED"; break; }
        std::string meta = r1.substr(4);
        std::string sha = licJsStr(meta, "sha");
        std::string b64 = licJsStr(meta, "content");
        if (sha.empty() || b64.empty()) { res = "BAD META"; break; }
        std::string json = b64dec(b64);
        if (json.empty()) { res = "BAD CONTENT"; break; }

        // 2) find this key's object
        std::vector<std::string> objs = licJsObjects(licJsArray(json, "keys"));
        std::string obj;
        for (size_t i = 0; i < objs.size(); i++) {
            if (licIeq(licJsStr(objs[i], "key"), keyStr)) { obj = objs[i]; break; }
        }
        if (obj.empty()) { res = "KEY NOT FOUND"; break; }
        size_t objPos = json.find(obj);
        if (objPos == std::string::npos) { res = "BAD JSON"; break; }
        size_t objLen = obj.size();

        // 3) devices: string list or object list — count + membership
        std::string devArr = licJsArray(obj, "devices");
        if (devArr.empty()) devArr = "[]";
        std::string devFlat = licJsStrList(devArr);
        std::vector<std::string> devObjs;
        bool objForm = devArr.find('{') != std::string::npos;
        int devCount = 0;
        bool already = false;
        if (objForm) {
            devObjs = licJsObjects(devArr);
            for (size_t i = 0; i < devObjs.size(); i++) {
                std::string id = licJsStr(devObjs[i], "id");
                if (id.empty()) id = licJsStr(devObjs[i], "hash");
                if (id.empty()) continue;
                devCount++;
                if (id == devStr) already = true;
            }
        } else {
            std::vector<std::string> ds = licSplit(devFlat, ',');
            for (size_t i = 0; i < ds.size(); i++) {
                if (ds[i].empty()) continue;
                devCount++;
                if (ds[i] == devStr) already = true;
            }
        }
        if (already) { res = "EXISTS"; break; }

        // 4) one key = max N phones, deny the rest
        int maxDev = (int)licJsInt(obj, "max_devices", 1);
        if (maxDev < 1) maxDev = 1;
        if (devCount >= maxDev) { res = "LIMIT"; break; }

        // 5) activation: countdown starts at import (first device only)
        long long nowMs = (long long)time(NULL) * 1000LL;
        std::string actAt = licJsStr(obj, "activated_at");
        if (actAt.empty()) {
            licJsSetStr(obj, "activated_at", licMsToIso(nowMs));
            long minutes = licJsInt(obj, "minutes", 0);
            long days = licJsInt(obj, "days", 0);
            if (minutes > 0) {
                licJsSetStr(obj, "expires_at", licMsToIso(nowMs + (long long)minutes * 60000LL));
            } else if (days > 0) {
                licJsSetStr(obj, "expires_at", licMsToIso(nowMs + (long long)days * 86400000LL));
                licJsSetStr(obj, "expiry", licDatePlusDays(nowMs, (int)days));
            }
        }

        // 6) append the device hash to the devices array of this object
        size_t dp = obj.find("\"devices\"");
        if (dp == std::string::npos) {
            licJsSetStr(obj, "devices", ""); // ensures field exists below
            dp = obj.find("\"devices\"");
        }
        size_t ap = obj.find('[', dp);
        if (ap == std::string::npos) { res = "NO DEVICES FIELD"; break; }
        size_t ep = obj.find(']', ap);
        if (ep == std::string::npos) { res = "NO DEVICES FIELD"; break; }
        std::string inner = obj.substr(ap + 1, ep - ap - 1);
        std::string entry = "\"" + devStr + "\"";
        bool blank = true;
        for (size_t i = 0; i < inner.size(); i++)
            if (!isspace((unsigned char)inner[i])) { blank = false; break; }
        std::string newInner = blank ? entry
            : inner + ((inner.size() && inner[inner.size()-1] == ' ') ? "" : " ") + "," + entry;
        obj.replace(ap + 1, ep - ap - 1, newInner);

        // 7) write back the updated object
        json.replace(objPos, objLen, obj);
        std::string payload = "{\"message\":\"activate " + keyStr + " on device\",\"content\":\""
            + b64enc(json) + "\",\"sha\":\"" + sha + "\",\"branch\":\"" + br + "\"}";
        std::string r2 = licHttpReq("PUT", api.c_str(), tok.c_str(), payload.c_str());
        if (r2.compare(0, 4, "200\n") == 0 || r2.compare(0, 4, "201\n") == 0) {
            res = "OK";
        } else {
            res = "WRITE FAILED";
        }
    } while (false);
    return env->NewStringUTF(res.c_str());
}

} // extern "C"
