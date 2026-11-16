// SPDX-License-Identifier: GPL-3.0-only
#include "../../core/runtime/src/main/cpp/chroot.c"
#include <assert.h>
int main(void) {
    char temp[] = "/tmp/fandroid-chroot-XXXXXX";
    assert(mkdtemp(temp));
    char leasepath[4096]; snprintf(leasepath, sizeof(leasepath), "%s/lease", temp);
    int a = open(leasepath, O_CREAT | O_RDWR, 0600), b = open(leasepath, O_RDWR), recovery = open(leasepath, O_RDWR);
    assert(a >= 0 && b >= 0 && recovery >= 0);
    assert(!takeLease(a, 0) && !takeLease(b, 0));
    assert(takeLease(recovery, 1)); // repair cannot run over a live root session
    assert(cleanupLease(a)); // first exit must leave root ownership alone
    assert(!cleanupLease(b)); // last exit owns cleanup
    assert(takeLease(recovery, 0)); // no new launch during ownership repair
    close(a); close(b); assert(!takeLease(recovery, 1)); close(recovery); assert(!unlink(leasepath));
    char path[4096]; target(temp, "dev", path);
    struct stat st; assert(!lstat(path, &st) && S_ISDIR(st.st_mode));
    assert(!rmdir(path)); assert(!symlink("/tmp", path));
    pid_t pid = fork(); assert(pid >= 0);
    if (!pid) { target(temp, "dev", path); _exit(0); }
    int status; assert(waitpid(pid, &status, 0) == pid);
    assert(WIFEXITED(status) && WEXITSTATUS(status) == 125);
    // The ownership walk repairs the symlink itself, never the linked tree.
    owner = getuid(); assert(!nftw(temp, restore, 32, FTW_PHYS | FTW_DEPTH));
    assert(!lstat(path, &st) && S_ISLNK(st.st_mode));
    assert(!unlink(path)); assert(!rmdir(temp));
    puts("PASS: chroot mount-target symlink refusal and physical ownership walk");
}
