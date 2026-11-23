#include <jni.h>
#include <sys/prctl.h>
JNIEXPORT void JNICALL Java_Hold_privateProcess(JNIEnv *env, jclass klass) {
    if (prctl(PR_SET_DUMPABLE, 0) != 0) {
        jclass error = (*env)->FindClass(env, "java/lang/IllegalStateException");
        (*env)->ThrowNew(env, error, "PR_SET_DUMPABLE failed");
    }
}
