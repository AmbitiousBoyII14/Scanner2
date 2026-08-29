// ============================================================================
// nativelic.cpp - JNI library entry. The protection logic lives in
// license.cpp (validation/limits) and admin.cpp (password/URLs); this TU
// owns library init and version identity.
// ============================================================================

#include <jni.h>

void licFetchInit(JavaVM* vm); // fetch.cpp

extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    licFetchInit(vm);
    return JNI_VERSION_1_6;
}

// Native core version tag (also proves the right .so is loaded).
JNIEXPORT jstring JNICALL
Java_myscanne_com_X_a(JNIEnv* env, jclass) {
    return env->NewStringUTF("spro-2.3");
}

} // extern "C"
