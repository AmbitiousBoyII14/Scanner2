LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE    := spro
LOCAL_SRC_FILES := ../cpp/nativelic.cpp ../cpp/license.cpp ../cpp/admin.cpp ../cpp/adminapi.cpp ../cpp/fetch.cpp ../cpp/str.cpp ../cpp/native-scan.cpp
LOCAL_CPPFLAGS  := -O2 -fvisibility=hidden -fvisibility-inlines-hidden
LOCAL_LDLIBS    := -llog
include $(BUILD_SHARED_LIBRARY)
