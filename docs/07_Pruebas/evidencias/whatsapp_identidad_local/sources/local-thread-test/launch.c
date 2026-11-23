#define _GNU_SOURCE
#include <sched.h>
#include <sys/mount.h>
#include <unistd.h>
#include <grp.h>
#include <stdlib.h>
int main(int argc, char **argv) {
    if (argc < 2) return 2;
    /* Disposable analogue of systemd PrivateTmp + User; no host mounts. */
    if (unshare(CLONE_NEWNS) || mount(NULL, "/", NULL, MS_REC|MS_PRIVATE, NULL)
        || mount("tmpfs", "/tmp", "tmpfs", 0, "mode=1777,size=16m")
        || setgroups(0, NULL) || setgid(65534) || setuid(65534)) return 3;
    execv(argv[1], argv+1);
    return 4;
}
