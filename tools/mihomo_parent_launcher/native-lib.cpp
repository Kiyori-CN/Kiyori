#include <errno.h>
#include <signal.h>
#include <stdio.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/types.h>
#include <unistd.h>

int main(int argc, char** argv) {
    if (argc < 2 || argv[1] == nullptr || argv[1][0] == '\0') {
        fprintf(stderr, "Mihomo executable path is required\n");
        return 64;
    }

    const pid_t expected_parent = getppid();
    if (expected_parent == 1) {
        fprintf(stderr, "Kiyori parent process is unavailable\n");
        return 70;
    }
    if (prctl(PR_SET_PDEATHSIG, SIGTERM) != 0) {
        fprintf(stderr, "Unable to bind Mihomo lifetime to Kiyori: %s\n", strerror(errno));
        return 71;
    }
    if (getppid() != expected_parent) {
        fprintf(stderr, "Kiyori parent process exited during Mihomo startup\n");
        return 72;
    }

    execv(argv[1], &argv[1]);
    fprintf(stderr, "Unable to execute Mihomo: %s\n", strerror(errno));
    return 127;
}
