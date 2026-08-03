// 通用 shell 身份 launcher：
// 以 root 身份运行本程序，尝试切换到 shell 的 SELinux 域，
// 然后切换到 shell 用户 (uid/gid 2000)，最后直接 execvp 目标命令。

#include <unistd.h>
#include <sys/types.h>
#include <sys/syscall.h>
#include <fcntl.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <grp.h>

// 从 Shizuku 源码简化移植的 SELinux helper。直接读写 procfs，避免运行时动态装载私有实现。
namespace se {

    static int getcon(char **context) {
        int fd = open("/proc/self/attr/current", O_RDONLY | O_CLOEXEC);
        if (fd < 0)
            return fd;

        char *buf;
        size_t size;
        int errno_hold;
        ssize_t ret;

        size = (size_t)sysconf(_SC_PAGE_SIZE);
        buf = (char *)malloc(size);
        if (!buf) {
            ret = -1;
            goto out;
        }
        memset(buf, 0, size);

        do {
            ret = read(fd, buf, size - 1);
        } while (ret < 0 && errno == EINTR);
        if (ret < 0)
            goto out2;

        if (ret == 0) {
            *context = nullptr;
            goto out2;
        }

        *context = strdup(buf);
        if (!(*context)) {
            ret = -1;
            goto out2;
        }
        ret = 0;
    out2:
        free(buf);
    out:
        errno_hold = errno;
        close(fd);
        errno = errno_hold;
        return (int)ret;
    }

    static int setcon(const char *ctx) {
        int fd = open("/proc/self/attr/current", O_WRONLY | O_CLOEXEC);
        if (fd < 0)
            return fd;
        size_t len = strlen(ctx) + 1;
        ssize_t rc = write(fd, ctx, len);
        close(fd);
        return rc != (ssize_t)len;
    }

    static void freecon(char *con) {
        free(con);
    }
}

int main(int argc, char *argv[]) {
    if (argc < 2) {
        fprintf(stderr, "Usage: %s <command ...>\n", argv[0]);
        return 1;
    }

    uid_t uid = getuid();
    if (uid != 0 && uid != 2000) {
        // 与 Shizuku starter 一致：要求以 root/su 身份运行，由 su/Magisk 自己决定 SELinux 域。
        fprintf(stderr, "[operit_shell_exec] must run as root (uid 0) or shell (uid 2000), current uid=%d\n", (int)uid);
        return 1;
    }

    // 对齐 Shizuku 的思路：在 root(0) 进程中初始化 SELinux helper，打印当前上下文用于调试，
    // 然后将 uid/gid 降级为 shell 用户 (2000)，让后续通过 FakeContext(PACKAGE_NAME=com.android.shell)
    // 调用系统服务时，DisplayManagerService 中的 "packageName must match the calling uid" 校验能够通过。
    char *cur_ctx = nullptr;
    se::getcon(&cur_ctx);
    if (cur_ctx) {
        fprintf(stderr, "[operit_shell_exec] current selinux context (before drop): %s\n", cur_ctx);
        se::freecon(cur_ctx);
    }

    if (uid == 0) {
        // 在当前 su/Magisk 域下先降级到 shell 的 uid/gid 和补充组，避免在 shell 域中被拒绝 setuid/setgid。
        const gid_t shell_groups[] = {
                2000, // shell
                1004, // input
                1007, // log
                1011, // adb
                1015, // sdcard_rw
                1028, // sdcard_r
                3001, // net_bt_admin
                3002, // net_bt
                3003, // inet
                3006, // net_bw_stats
                3009, // readproc
                3011  // uhid
        };
        if (setgroups(sizeof(shell_groups) / sizeof(shell_groups[0]), shell_groups) != 0) {
            perror("[operit_shell_exec] setgroups(shell) failed");
            return 1;
        }

        if (setgid(2000) != 0) {
            perror("setgid(2000) failed");
            return 1;
        }
        if (setuid(2000) != 0) {
            perror("setuid(2000) failed");
            return 1;
        }

        // 现在已经是 uid/gid=2000，但仍处于 su/Magisk 的 SELinux 域，
        // 在该域中尝试切换到 shell 的 SELinux 域。
        const char *target_ctx = "u:r:shell:s0";
        if (se::setcon(target_ctx) != 0) {
            perror("[operit_shell_exec] setcon(u:r:shell:s0) failed");
            return 1;
        }
        char *after_ctx = nullptr;
        if (se::getcon(&after_ctx) != 0 || after_ctx == nullptr) {
            perror("[operit_shell_exec] unable to verify shell SELinux context");
            return 1;
        }
        if (strcmp(after_ctx, target_ctx) != 0) {
            fprintf(stderr, "[operit_shell_exec] unexpected SELinux context after setcon: %s\n", after_ctx);
            se::freecon(after_ctx);
            return 1;
        }
        fprintf(stderr, "[operit_shell_exec] selinux context (after setcon): %s\n", after_ctx);
        se::freecon(after_ctx);
    } else {
        // 以 shell 身份直接启动（uid=2000）时，只尝试记录当前上下文，不修改 uid/gid。
    }

    uid_t final_uid = getuid();
    gid_t final_gid = getgid();
    if (final_uid != 2000 || final_gid != 2000) {
        fprintf(stderr, "[operit_shell_exec] failed to switch to shell identity (uid=2000,gid=2000); final uid=%d gid=%d\n",
                (int)final_uid, (int)final_gid);
        return 1;
    }

    fprintf(stderr, "[operit_shell_exec] running as uid=%d gid=%d\n", (int)final_uid, (int)final_gid);

    // 解析前缀形式的环境变量赋值（例如 CLASSPATH=...），然后直接 execvp 目标程序
    int cmd_index = 1;
    for (; cmd_index < argc; ++cmd_index) {
        char *eq = strchr(argv[cmd_index], '=');
        if (eq == NULL) {
            // 第一个不包含 '=' 的参数视为要执行的程序名
            break;
        }

        // 拆分 KEY=VALUE 并设置环境变量
        *eq = '\0';
        const char *key = argv[cmd_index];
        const char *value = eq + 1;
        if (setenv(key, value, 1) != 0) {
            perror("setenv");
            return 1;
        }
    }

    if (cmd_index >= argc) {
        fprintf(stderr, "[operit_shell_exec] no command to exec after env vars\n");
        return 1;
    }

    // 直接执行 argv[cmd_index]，其后参数保持不变
    execvp(argv[cmd_index], &argv[cmd_index]);

    // execvp 返回说明失败
    perror("execvp");
    return 1;
}
