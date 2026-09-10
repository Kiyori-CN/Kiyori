#include <jni.h>
#include <cerrno>
#include <fcntl.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <string>

// 直接 syscall 兼容 minSdk 26；不依赖较新 libc 的 renameat2 导出符号。
// 不支持的内核/文件系统返回 errno，不降级为会覆盖目标的 rename。
extern "C" JNIEXPORT jint JNICALL
Java_com_ai_assistance_operit_core_tools_defaultTool_standard_NativeNoReplaceCommit_renameNoReplace(
        JNIEnv* env, jobject, jbyteArray source_bytes, jbyteArray destination_bytes) {
    if (source_bytes == nullptr || destination_bytes == nullptr) return EINVAL;
    const auto source_size = env->GetArrayLength(source_bytes);
    const auto destination_size = env->GetArrayLength(destination_bytes);
    if (source_size <= 0 || destination_size <= 0 || source_size >= 4096 || destination_size >= 4096) return ENAMETOOLONG;
    std::string source(static_cast<size_t>(source_size), '\0');
    std::string destination(static_cast<size_t>(destination_size), '\0');
    env->GetByteArrayRegion(source_bytes, 0, source_size, reinterpret_cast<jbyte*>(&source[0]));
    if (env->ExceptionCheck()) return EINVAL;
    env->GetByteArrayRegion(destination_bytes, 0, destination_size, reinterpret_cast<jbyte*>(&destination[0]));
    if (env->ExceptionCheck()) return EINVAL;
    if (source.find('\0') != std::string::npos || destination.find('\0') != std::string::npos) return EINVAL;
#ifdef __NR_renameat2
    constexpr unsigned int no_replace = 1; // Linux UAPI RENAME_NOREPLACE
    const auto result = syscall(__NR_renameat2, AT_FDCWD, source.c_str(), AT_FDCWD, destination.c_str(), no_replace);
    return result == 0 ? 0 : errno;
#else
    return ENOSYS;
#endif
}
