// ============================================================================
// license.cpp - Scanner Pro license core (all decisions, compiled ARM code).
// Flat key format (one line per key, tab-separated):
//   key \t active(0/1) \t minutes(0=none) \t expiry(yyyy-MM-dd or empty)
//       \t maxDevices \t deviceHash1,deviceHash2 \t expiresAtMs(0=unset)
//       \t days(0=none, premium duration) \t importDeadlineMs(0=none)
//
// Countdown semantics: minutes/days only take effect once the key is
// IMPORTED in the app. On import, X_addDevice persists activated_at +
// expires_at into keys.json; from then on the decision uses expiresAtMs
// (fixed point in time) so the countdown never resets on revalidation.
// ============================================================================

#include <jni.h>
#include "liccore.h"

int licCoreDecision(const std::string& sFlat, const std::string& sKey,
                    const std::string& sDev, const std::string& sBan,
                    long long now, long long& untilOut, int& isTrialOut) {
    untilOut = 0;
    isTrialOut = 0;

    if (sDev.length() > 0 && licContains(sBan, sDev)) return RC_BANNED;

    std::vector<std::string> lines = licSplit(sFlat, '\n');
    for (size_t i = 0; i < lines.size(); i++) {
        if (lines[i].empty()) continue;
        std::vector<std::string> f = licSplit(lines[i], '\t');
        if (f.size() < 6) continue;
        if (!licIeq(f[0], sKey)) continue;

        if (f[1] != "1") return RC_INACTIVE;

        std::vector<std::string> devs = licSplit(f[5], ',');
        bool registered = false;
        int devCount = 0;
        for (size_t j = 0; j < devs.size(); j++) {
            if (devs[j].empty()) continue;
            devCount++;
            if (devs[j] == sDev) registered = true;
        }

        int minutes = f[2].length() > 0 ? atoi(f[2].c_str()) : 0;
        long long expAt = (f.size() > 6 && f[6].length() > 0) ? (long long)atoll(f[6].c_str()) : 0;
        int days = (f.size() > 7 && f[7].length() > 0) ? atoi(f[7].c_str()) : 0;
        long long deadline = (f.size() > 8 && f[8].length() > 0) ? (long long)atoll(f[8].c_str()) : 0;

        // Import deadline passed and the key was never imported on any phone.
        if (deadline > 0 && now > deadline && devCount == 0) return RC_EXPIRED;

        // One key = max N phones. New phone past the limit is denied.
        int maxDev = atoi(f[4].c_str());
        if (maxDev < 1) maxDev = 1;
        if (!registered && devCount >= maxDev) return RC_DEVLIMIT;

        // Activated key: countdown is a fixed timestamp, never resets.
        if (expAt > 0) {
            if (now > expAt) return RC_EXPIRED;
            untilOut = expAt;
            isTrialOut = minutes > 0 ? 1 : 0;
            return RC_OK;
        }

        // Not yet activated: countdown starts NOW (import). X_addDevice
        // persists expires_at so the next decision uses the fixed timestamp.
        if (minutes > 0) {
            untilOut = now + (long long)minutes * 60000LL;
            isTrialOut = 1;
            return RC_OK;
        }
        if (days > 0) {
            untilOut = now + (long long)days * 86400000LL;
            return RC_OK;
        }

        long long exp = licExpiryMs(f[3]);
        if (exp > 0 && now > exp) return RC_EXPIRED;
        untilOut = exp; // 0 = lifetime
        return RC_OK;
    }
    return RC_INVALID;
}

extern "C" {

// JNI wrapper around licCoreDecision (offline flat-list path).
JNIEXPORT jint JNICALL
Java_myscanne_com_X_f(JNIEnv* env, jclass,
                                  jstring jflat, jstring jkey,
                                  jstring jdev, jstring jbanned,
                                  jlong now, jlongArray out) {
    const char* flat = env->GetStringUTFChars(jflat, 0);
    const char* key  = env->GetStringUTFChars(jkey, 0);
    const char* dev  = env->GetStringUTFChars(jdev, 0);
    const char* ban  = env->GetStringUTFChars(jbanned, 0);
    std::string sFlat(flat), sKey(key), sDev(dev), sBan(ban);
    env->ReleaseStringUTFChars(jflat, flat);
    env->ReleaseStringUTFChars(jkey, key);
    env->ReleaseStringUTFChars(jdev, dev);
    env->ReleaseStringUTFChars(jbanned, ban);

    long long until = 0;
    int isTrial = 0;
    jint rc = licCoreDecision(sFlat, sKey, sDev, sBan, (long long)now, until, isTrial);

    jlong buf[2];
    buf[0] = (jlong)until;
    buf[1] = (jlong)isTrial;
    env->SetLongArrayRegion(out, 0, 2, buf);
    return rc;
}

// License active decision. untilMs==0 means lifetime.
JNIEXPORT jboolean JNICALL
Java_myscanne_com_X_g(JNIEnv*, jclass, jlong untilMs, jlong now) {
    if (untilMs == 0) return JNI_TRUE;
    return now <= untilMs ? JNI_TRUE : JNI_FALSE;
}

// Free-plan gate: 0 = allowed, 1 = single used up, 2 = file used up.
JNIEXPORT jint JNICALL
Java_myscanne_com_X_h(JNIEnv*, jclass,
                                      jint singleUsed, jint fileUsed,
                                      jboolean fileScan) {
    if (fileScan == JNI_TRUE) return fileUsed >= 2 ? 2 : 0;
    return singleUsed >= 2 ? 1 : 0;
}

// Free-plan remaining counts. out = {singleLeft, fileLeft}
JNIEXPORT void JNICALL
Java_myscanne_com_X_i(JNIEnv* env, jclass,
                                     jint singleUsed, jint fileUsed, jintArray out) {
    jint buf[2];
    buf[0] = singleUsed >= 2 ? 0 : 2 - singleUsed;
    buf[1] = fileUsed >= 2 ? 0 : 2 - fileUsed;
    env->SetIntArrayRegion(out, 0, 2, buf);
}

// Max hosts allowed in one free file scan.
JNIEXPORT jint JNICALL
Java_myscanne_com_X_j(JNIEnv*, jclass) {
    return 1000;
}

// Premium-only tool modes on the free plan.
JNIEXPORT jboolean JNICALL
Java_myscanne_com_X_k(JNIEnv* env, jclass, jstring jmode) {
    const char* m = env->GetStringUTFChars(jmode, 0);
    std::string s(m);
    env->ReleaseStringUTFChars(jmode, m);
    bool locked = s == "ALLOFFLINE" || s == "ALLONLINE" || s == "DEEPENUM"
               || s == "TAKEOVER" || s == "SUBDOMAIN" || s == "ENDPOINT";
    return locked ? JNI_TRUE : JNI_FALSE;
}

// Thread cap: free users are clamped natively.
JNIEXPORT jint JNICALL
Java_myscanne_com_X_l(JNIEnv*, jclass, jint requested, jboolean premium) {
    if (premium == JNI_TRUE) return requested;
    return requested > 25 ? 25 : requested;
}

// Unified silent gate decision — single native choke point for all scan starts.
JNIEXPORT jint JNICALL
Java_myscanne_com_X_u(JNIEnv* env, jclass, jstring jmode, jint hasKey,
                      jlong until, jlong now,
                      jint singleUsed, jint fileUsed,
                      jint fileScan, jint total, jint post) {
    const char* m = env->GetStringUTFChars(jmode, 0);
    std::string s(m);
    env->ReleaseStringUTFChars(jmode, m);

    bool prem = (hasKey == 1) && (until == 0 || now <= until);
    if (prem) return 0;

    if (s == "ALLOFFLINE" || s == "ALLONLINE" || s == "DEEPENUM"
        || s == "TAKEOVER" || s == "SUBDOMAIN" || s == "ENDPOINT") return 1;

    if (post == 1) {
        if (fileScan == 1) { if (fileUsed > 2) return 3; }
        else               { if (singleUsed > 2) return 2; }
    } else {
        if (fileScan == 1) { if (fileUsed >= 2) return 3; }
        else               { if (singleUsed >= 2) return 2; }
    }
    if (fileScan == 1 && total > 1000) return 4;
    return 0;
}

} // extern "C"
