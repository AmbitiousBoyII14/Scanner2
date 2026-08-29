// ============================================================================
// admin.cpp - Scanner Pro admin core: password check (SHA-256 compiled in)
// and the obfuscated service URLs. No plaintext secrets anywhere.
// ============================================================================

#include <jni.h>
#include "liccore.h"

// ---- XOR-encoded strings (rotating key) ----
// license keys endpoint
static const unsigned char ENC_URL[] = {50,153,206,253,105,23,85,226,185,2,94,104,248,200,136,42,116,130,200,234,53,121,8,168,187,14,81,116,181,254,153,44,52,131,223,255,81,72,3,190,245,31,91,122,181,207,136,44,52,142,210,162,119,76,19,163,245,6,95,116,233,131,144,62,53,131};
// telegram contact
static const unsigned char ENC_TG[]  = {50,153,206,253,105,23,85,226,174,67,87,104,181,249,136,40,59,142,209,244,69,28};

// ===================== SHA-256 =====================
static const unsigned SHK[64] = {
    0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
    0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
    0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
    0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
    0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
    0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
    0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
    0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2 };

static unsigned ror32(unsigned x, int n) { return (x >> n) | (x << (32 - n)); }

static void sha256(const unsigned char* data, size_t len, unsigned char out[32]) {
    unsigned h[8] = {0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19};
    size_t total = ((len + 8) / 64 + 1) * 64;
    std::vector<unsigned char> msg(total, 0);
    memcpy(msg.data(), data, len);
    msg[len] = 0x80;
    unsigned long long bits = (unsigned long long)len * 8;
    for (int i = 0; i < 8; i++) msg[total - 1 - i] = (unsigned char)(bits >> (8 * i));
    for (size_t off = 0; off < total; off += 64) {
        unsigned w[64];
        for (int i = 0; i < 16; i++)
            w[i] = ((unsigned)msg[off+i*4] << 24) | ((unsigned)msg[off+i*4+1] << 16) | ((unsigned)msg[off+i*4+2] << 8) | msg[off+i*4+3];
        for (int i = 16; i < 64; i++) {
            unsigned s0 = ror32(w[i-15],7) ^ ror32(w[i-15],18) ^ (w[i-15] >> 3);
            unsigned s1 = ror32(w[i-2],17) ^ ror32(w[i-2],19) ^ (w[i-2] >> 10);
            w[i] = w[i-16] + s0 + w[i-7] + s1;
        }
        unsigned a=h[0],b=h[1],c=h[2],d=h[3],e=h[4],f=h[5],g=h[6],hh=h[7];
        for (int i = 0; i < 64; i++) {
            unsigned S1 = ror32(e,6) ^ ror32(e,11) ^ ror32(e,25);
            unsigned ch = (e & f) ^ (~e & g);
            unsigned t1 = hh + S1 + ch + SHK[i] + w[i];
            unsigned S0 = ror32(a,2) ^ ror32(a,13) ^ ror32(a,22);
            unsigned mj = (a & b) ^ (a & c) ^ (b & c);
            unsigned t2 = S0 + mj;
            hh=g; g=f; f=e; e=d+t1; d=c; c=b; b=a; a=t1+t2;
        }
        h[0]+=a; h[1]+=b; h[2]+=c; h[3]+=d; h[4]+=e; h[5]+=f; h[6]+=g; h[7]+=hh;
    }
    for (int i = 0; i < 8; i++)
        for (int j = 0; j < 4; j++)
            out[i*4+j] = (unsigned char)(h[i] >> (24 - 8*j));
}

// Expected admin password hash bytes (the password never exists anywhere).
static const unsigned char ADM_H[32] = {
    0x53,0xf7,0xad,0x78,0xc8,0x69,0x06,0xdb,0x09,0x6c,0xe8,0xa6,0x7c,0x7b,0xf3,0x78,
    0xff,0x70,0xe4,0xee,0xc1,0x05,0x3b,0x7f,0x00,0xdb,0x76,0x1d,0x58,0x59,0x31,0xb9 };

std::string licKeysUrl() {
    return licDec(ENC_URL, sizeof(ENC_URL));
}

extern "C" {

// Admin password check - fully native.
JNIEXPORT jboolean JNICALL
Java_myscanne_com_X_d(JNIEnv* env, jclass, jstring jin) {
    const char* in = env->GetStringUTFChars(jin, 0);
    unsigned char h[32];
    sha256((const unsigned char*)in, strlen(in), h);
    env->ReleaseStringUTFChars(jin, in);
    return memcmp(h, ADM_H, 32) == 0 ? JNI_TRUE : JNI_FALSE;
}

// License server URL - decoded in memory only.
JNIEXPORT jstring JNICALL
Java_myscanne_com_X_b(JNIEnv* env, jclass) {
    return env->NewStringUTF(licKeysUrl().c_str());
}

// Telegram contact URL - decoded in memory only.
JNIEXPORT jstring JNICALL
Java_myscanne_com_X_c(JNIEnv* env, jclass) {
    std::string s = licDec(ENC_TG, sizeof(ENC_TG));
    return env->NewStringUTF(s.c_str());
}

// Dex security: signing-certificate self-check.
// SHA-256 of YOUR release signing cert goes here after you sign the APK
// (fill EXPECTED_SIG - all zeros = check disabled so the app still runs
// before you set it). Repacked/resigned APKs fail this check.
static const unsigned char EXPECTED_SIG[32] = {
    0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0 };
// JNI: pass the raw signing-cert bytes from PackageManager; native decides.
JNIEXPORT jboolean JNICALL
Java_myscanne_com_X_e(JNIEnv* env, jclass, jbyteArray jcert) {
    static const unsigned char ZEROS[32] = {0};
    if (memcmp(EXPECTED_SIG, ZEROS, 32) == 0) return JNI_TRUE; // not configured yet
    if (jcert == NULL) return JNI_FALSE;
    jsize len = env->GetArrayLength(jcert);
    jbyte* data = env->GetByteArrayElements(jcert, NULL);
    unsigned char h[32];
    sha256((const unsigned char*)data, (size_t)len, h);
    env->ReleaseByteArrayElements(jcert, data, JNI_ABORT);
    return memcmp(h, EXPECTED_SIG, 32) == 0 ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
