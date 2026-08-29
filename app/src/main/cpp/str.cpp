// ============================================================================
// str.cpp - every user-facing license/premium/admin string, XOR-encoded.
// Smali contains ZERO readable strings; these bytes only decode in memory.
// X.p(id) = plain string, X.q(id,a,b) = formatted with two ints,
// X.r(until,now) = badge text computed fully native.
// ============================================================================

#include <jni.h>
#include <cstdio>
#include "liccore.h"

static const unsigned char S_1[] = {28,191,255,200};
static const unsigned char S_2[] = {10,191,255,192,83,120,55};
static const unsigned char S_3[] = {14,191,243,204,86,13,49,136,131};
static const unsigned char S_4[] = {25,184,232,223,95,99,46,237,138,33,123,67};
static const unsigned char S_5[] = {104,205,201,228,116,74,22,168,250,30,89,108,244,222,218,101,46,130,206,236,118,4};
static const unsigned char S_6[] = {104,205,220,228,118,72,90,190,185,12,84,126,186,133,151,44,34,205,139,161,42,29,74,237,178,2,73,121,233,141,159,44,57,133,147};
static const unsigned char S_7[] = {24,140,201,228,121,13,21,171,188,1,83,99,255,141,142,34,53,129,201,173,117,67,22,180};
static const unsigned char S_8[] = {20,130,154,222,121,76,20,224,155,1,86,45,181,141,190,40,63,157,154,162,58,126,15,175,190,2,87,108,243,195};
static const unsigned char S_9[] = {15,131,214,228,119,68,14,168,190,77,73,100,244,202,150,40,122,198,154,235,115,65,31,237,169,14,91,99,233};
static const unsigned char S_10[] = {27,161,246,173,110,66,21,161,169,77,79,99,246,194,153,38,63,137,154,165,73,78,27,163,247,44,86,97,182,141,190,40,63,157,150,173,73,88,24,169,181,0,91,100,244,131,212,99,115};
static const unsigned char S_11[] = {28,152,214,225,58,89,18,191,191,12,94,45,233,221,159,40,62,193,154,227,117,13,25,172,170,30};
static const unsigned char S_12[] = {30,140,206,232,55,79,27,190,191,9,26,102,255,212,218,101,53,159,154,225,115,75,31,185,179,0,95,36};
static const unsigned char S_13[] = {28,152,214,225,58,93,8,168,183,4,79,96,186,203,149,63,122,220,138,160,41,29,90,160,179,3,79,121,255,222};
static const unsigned char S_14[] = {25,130,207,227,110,73,21,186,180,77,73,121,251,223,142,62,122,130,212,173,123,78,14,164,172,12,78,100,245,195};
static const unsigned char S_15[] = {29,136,206,173,123,13,14,191,179,12,86,45,241,200,131,109,53,131,154,217,127,65,31,170,168,12,87};
static const unsigned char S_16[] = {29,168,238,173,74,127,63,128,147,56,119,45,183,141,186,25,40,136,219,238,113,84,37,252};
static const unsigned char S_17[] = {29,168,238,173,78,127,51,140,150,77,113,72,195,141,215,109,26,185,200,232,123,78,17,180,133,92};
static const unsigned char S_18[] = {27,174,238,196,76,108,46,136,250,38,127,84};
static const unsigned char S_19[] = {31,131,206,232,104,13,27,237,170,31,95,96,243,216,151,109,53,159,154,249,104,68,27,161,250,6,95,116,186,217,149,109,47,131,214,226,121,70};
static const unsigned char S_20[] = {9,142,219,227,116,72,8,237,138,31,85};
static const unsigned char S_21[] = {12,136,200,228,124,84,19,163,189,77,86,100,249,200,148,62,63,195,148,163};
static const unsigned char S_22[] = {12,140,214,228,126,76,14,164,180,10,26,102,255,212,212,99,116};
static const unsigned char S_23[] = {22,132,217,232,116,94,31,237,191,21,74,100,232,200,158,109,53,159,154,233,127,76,25,185,179,27,91,121,255,201,218,96,122,159,223,160,127,67,14,168,168,77,91,45,241,200,131};
static const unsigned char S_24[] = {19,131,206,232,125,95,19,185,163,77,89,101,255,206,145,109,60,140,211,225,127,73};
static const unsigned char S_25[] = {27,137,215,228,116,13,59,174,185,8,73,126};
static const unsigned char S_26[] = {27,137,215,228,116,13,10,172,169,30,77,98,232,201};
static const unsigned char S_27[] = {15,131,214,226,121,70};
static const unsigned char S_28[] = {25,140,212,238,127,65};
static const unsigned char S_29[] = {13,159,213,227,125,13,10,172,169,30,77,98,232,201};
static const unsigned char S_30[] = {22,130,219,233,58,75,27,164,182,8,94,45,183,141,153,37,63,142,209,173,121,66,20,163,191,14,78,100,245,195};
static const unsigned char S_31[] = {27,137,215,228,116,13,87,237,177,8,67,126,186,133,150,36,44,136,147};
static const unsigned char S_32[] = {25,129,213,254,127};
static const unsigned char S_33[] = {15,157,221,255,123,73,31,237,168,8,75,120,243,223,159,41};
static const unsigned char S_34[] = {20,130,206,173,116,66,13};
static const unsigned char S_35[] = {24,140,201,228,121,13,14,162,181,1,73,45,245,195,150,52,118,205,212,226,58,126,25,172,180,64,123,97,246,130,190,40,63,157,149,222,111,79,30,162,183,12,83,99};
static const unsigned char S_36[] = {15,131,214,228,119,68,14,168,190,77,73,110,251,195,137,109,119,205,251,193,86,13,14,162,181,1,73,45,183,141,156,56,54,129,154,254,106,72,31,169};
static const unsigned char S_37[] = {30,140,206,232,55,79,27,190,191,9,26,98,232,141,150,36,60,136,206,228,119,72,90,166,191,20};
static const unsigned char S_38[] = {28,152,214,225,58,93,8,168,183,4,79,96,186,203,149,63,122,220,138,160,41,29,90,160,179,3,79,121,255,222,218,96,122,140,201,230,58,66,20,237,142,8,86,104,253,223,155,32};
static const unsigned char S_39[] = {14,191,243,204,86};
static const unsigned char S_40[] = {122,132,201,173,123,13,42,159,159,32,115,88,215,141,156,40,59,153,207,255,127,3};
static const unsigned char S_41[] = {28,191,255,200,58,5,25,184,168,31,95,99,238,141,138,33,59,131,147};
static const unsigned char S_42[] = {122,140,217,249,115,91,31,237,244,77};
static const unsigned char S_43[] = {14,159,211,236,118};
static const unsigned char S_44[] = {10,159,223,224,115,88,23};
static const unsigned char S_45[] = {27,169,247,196,84,13,42,140,148,40,118};
static const unsigned char S_46[] = {17,168,227,222};
static const unsigned char S_47[] = {20,168,237,173,78,127,51,140,150};
static const unsigned char S_48[] = {20,168,237,173,74,127,63,128,147,56,119};
static const unsigned char S_49[] = {29,168,244,200,72,108,46,136};
static const unsigned char S_50[] = {27,169,254,173,81,104,35};
static const unsigned char S_51[] = {23,132,212,248,110,72,9,237,242,92,10,32,169,157,211};
static const unsigned char S_52[] = {30,140,195,254,58,5,74,237,231,77,86,100,252,200,142,36,55,136,147};
static const unsigned char S_53[] = {27,137,222,228,116,74,90,166,191,20,20,35,180};
static const unsigned char S_54[] = {17,168,227,173,91,105,62,136,158};
static const unsigned char S_55[] = {28,172,243,193,95,105};

static const unsigned char F_1[] = {25,162,244,217,83,99,47,136,250,43,104,72,223,141,210,104,62,205,201,228,116,74,22,168,250,70,26,40,254,141,156,36,54,136,154,225,127,75,14,228};
static const unsigned char F_2[] = {28,159,223,232,32,13,95,169,245,95,26,126,243,195,157,33,63,205,148,173,63,73,85,255,250,11,83,97,255,141,150,40,60,153};
static const unsigned char F_3[] = {28,159,223,232,58,0,90,232,190,66,8,45,233,196,148,42,54,136,154,166,58,8,30,226,232,77,92,100,246,200,218,33,63,139,206};
static const unsigned char F_4[] = {28,191,255,200,58,0,90,232,190,30,17,40,254,203,218,33,63,139,206};
static const unsigned char F_5[] = {127,137,149,168,126,13,9,164,180,10,86,104,186,131,218,104,62,194,159,233,58,75,19,161,191,77,73,110,251,195,137,109,54,136,220,249};
static const unsigned char F_6[] = {28,159,223,232,58,93,22,172,180,87,26,107,243,193,159,109,41,142,219,227,58,64,27,181,250,92,10,61,170,141,146,34,41,153,201,173,50,84,21,184,168,30,26,101,251,222,218,104,62,196,148};

static const unsigned char* pick(int id, const unsigned char* table[], const int sizes[], int n, int& outLen) {
    if (id < 1 || id > n) { outLen = 0; return 0; }
    outLen = sizes[id - 1];
    return table[id - 1];
}

extern "C" {

JNIEXPORT jstring JNICALL
Java_myscanne_com_X_p(JNIEnv* env, jclass, jint id) {
    static const unsigned char* T[] = {
        S_1,
        S_2,
        S_3,
        S_4,
        S_5,
        S_6,
        S_7,
        S_8,
        S_9,
        S_10,
        S_11,
        S_12,
        S_13,
        S_14,
        S_15,
        S_16,
        S_17,
        S_18,
        S_19,
        S_20,
        S_21,
        S_22,
        S_23,
        S_24,
        S_25,
        S_26,
        S_27,
        S_28,
        S_29,
        S_30,
        S_31,
        S_32,
        S_33,
        S_34,
        S_35,
        S_36,
        S_37,
        S_38,
        S_39,
        S_40,
        S_41,
        S_42,
        S_43,
        S_44,
        S_45,
        S_46,
        S_47,
        S_48,
        S_49,
        S_50,
        S_51,
        S_52,
        S_53,
        S_54,
        S_55
    };
    static const int Z[] = {4,7,9,12,22,35,24,30,29,49,26,28,30,30,27,24,26,12,38,11,20,17,47,22,12,14,6,6,14,30,19,5,16,7,44,40,26,48,5,22,19,10,5,7,11,4,9,11,8,7,15,19,13,9,6};
    int len;
    const unsigned char* p = pick((int)id, T, Z, sizeof(Z)/sizeof(Z[0]), len);
    if (!p) return env->NewStringUTF("");
    std::string s = licDec(p, len);
    return env->NewStringUTF(s.c_str());
}

JNIEXPORT jstring JNICALL
Java_myscanne_com_X_q(JNIEnv* env, jclass, jint id, jint a, jint b) {
    static const unsigned char* T[] = {
        F_1,
        F_2,
        F_3,
        F_4,
        F_5,
        F_6
    };
    static const int Z[] = {40,34,35,19,36,51};
    int len;
    const unsigned char* p = pick((int)id, T, Z, sizeof(Z)/sizeof(Z[0]), len);
    if (!p) return env->NewStringUTF("");
    std::string f = licDec(p, len);
    char buf[160];
    snprintf(buf, sizeof(buf), f.c_str(), (int)a, (int)b);
    return env->NewStringUTF(buf);
}

// Badge text: untilMs==0 lifetime, <0 free, else countdown. Fully native.
JNIEXPORT jstring JNICALL
Java_myscanne_com_X_r(JNIEnv* env, jclass, jlong untilMs, jlong now, jboolean premium) {
    char buf[32];
    if (premium != JNI_TRUE) {
        snprintf(buf, sizeof(buf), "FREE");
    } else if (untilMs == 0) {
        snprintf(buf, sizeof(buf), "LIFETIME");
    } else {
        long long left = (long long)untilMs - (long long)now;
        if (left <= 0) snprintf(buf, sizeof(buf), "ACTIVE");
        else {
            long long mins = left / 60000;
            if (mins < 60) snprintf(buf, sizeof(buf), "%lldm LEFT", mins < 1 ? 1 : mins);
            else {
                long long hours = mins / 60;
                if (hours < 48) snprintf(buf, sizeof(buf), "%lldh LEFT", hours);
                else snprintf(buf, sizeof(buf), "%lldd LEFT", hours / 24 + 1);
            }
        }
    }
    return env->NewStringUTF(buf);
}

} // extern "C"
