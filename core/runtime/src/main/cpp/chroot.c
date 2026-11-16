// SPDX-License-Identifier: GPL-3.0-only
// Original namespace supervisor. Mounts exist only in the child; never in Android's mount namespace.
#define _GNU_SOURCE
#include <jni.h>
#include <sched.h>
#include <sys/mount.h>
#include <sys/prctl.h>
#include <sys/wait.h>
#include <sys/stat.h>
#include <sys/file.h>
#include <sys/ioctl.h>
#include <fcntl.h>
#include <signal.h>
#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <ftw.h>
static volatile sig_atomic_t stopped;
static void stop(int s) { stopped = s; }
static uid_t owner;
static int takeLease(int fd, int repair) { return flock(fd, (repair ? LOCK_EX : LOCK_SH) | LOCK_NB); }
static int cleanupLease(int fd) { flock(fd, LOCK_UN); return flock(fd, LOCK_EX | LOCK_NB); }
static int restore(const char *p, const struct stat *s, int t, struct FTW *f) {
    (void)s; (void)t; (void)f;
    return lchown(p, owner, owner);
}
static void fail(const char *what) { perror(what); _exit(125); }
static void target(const char *root, const char *name, char *out) {
    if (snprintf(out, 4096, "%s/%s", root, name) >= 4096) fail("path too long");
    struct stat st;
    if (lstat(out, &st) && mkdir(out, 0755)) fail("mkdir mount target");
    if (lstat(out, &st) || !S_ISDIR(st.st_mode)) { errno = EINVAL; fail("mount target must be a real directory"); }
}
static void bind(const char *root, const char *src, const char *name, int ro) {
    char dst[4096]; target(root, name, dst);
    if (mount(src, dst, NULL, MS_BIND | MS_REC, NULL)) fail("bind mount");
    if (ro && mount(NULL, dst, NULL, MS_BIND | MS_REMOUNT | MS_RDONLY, NULL)) fail("read-only mount");
}
JNIEXPORT jint JNICALL Java_io_github_aqsama_fandroidterminal_sessions_RootChroot_run(
    JNIEnv *e, jclass c, jstring jr, jint uid, jstring js, jstring jd, jstring jsh, jobjectArray vars) {
    (void)c;
    if (getuid() != 0) { fprintf(stderr, "Device root authorization required\n"); return 126; }
    const char *root = (*e)->GetStringUTFChars(e, jr, NULL);
    const char *shared = (*e)->GetStringUTFChars(e, js, NULL);
    const char *dir = (*e)->GetStringUTFChars(e, jd, NULL);
    const char *shell = (*e)->GetStringUTFChars(e, jsh, NULL);
    owner = (uid_t)uid;
    char pending[4096], lockpath[4096];
    snprintf(pending, sizeof(pending), "%s/../root-access.pending", root);
    snprintf(lockpath, sizeof(lockpath), "%s/../root-access.lock", root);
    int lock = open(lockpath, O_CREAT | O_RDWR | O_CLOEXEC | O_NOFOLLOW, 0600);
    if (lock < 0 || takeLease(lock, !strcmp(dir, "!repair"))) { fprintf(stderr, "Root environment is still in use or cleaning up\n"); return 125; }
    if (!strcmp(dir, "!repair") && access(pending, F_OK)) { lchown(lockpath, owner, owner); close(lock); return 0; }
    int marker = open(pending, O_CREAT | O_WRONLY | O_CLOEXEC | O_NOFOLLOW, 0600);
    if (marker < 0) return 125;
    close(marker);
    if (prctl(PR_SET_CHILD_SUBREAPER, 1)) return 125;
    int count = (*e)->GetArrayLength(e, vars);
    char **variables = calloc((size_t)count + 1, sizeof(char *));
    for (int i = 0; i < count; i++) {
        jstring v = (*e)->GetObjectArrayElement(e, vars, i);
        const char *value = (*e)->GetStringUTFChars(e, v, NULL);
        variables[i] = strdup(value);
        (*e)->ReleaseStringUTFChars(e, v, value);
    }
    if (!strcmp(dir, "!repair")) {
        if (nftw(root, restore, 32, FTW_PHYS | FTW_DEPTH)) return 125;
        unlink(pending); lchown(lockpath, owner, owner); close(lock); return 0;
    }
    struct sigaction sa = {0}; sa.sa_handler = stop; sigemptyset(&sa.sa_mask);
    sigaction(SIGHUP, &sa, NULL); sigaction(SIGTERM, &sa, NULL);
    // Fork before namespace changes. Supervisor retains a view with no guest mounts.
    pid_t child = fork();
    if (child < 0) return 125;
    if (!child) {
        signal(SIGHUP, SIG_DFL); signal(SIGTERM, SIG_DFL);
        if (prctl(PR_SET_PDEATHSIG, SIGKILL) || unshare(CLONE_NEWNS | CLONE_NEWPID)) fail("private namespaces unavailable");
        if (mount(NULL, "/", NULL, MS_REC | MS_PRIVATE, NULL)) fail("private mount propagation");
        pid_t init = fork(); if (init < 0) fail("namespace init");
        if (init) { int s; while (waitpid(init, &s, 0) < 0 && errno == EINTR) {} _exit(WIFEXITED(s) ? WEXITSTATUS(s) : 128 + WTERMSIG(s)); }
        if (prctl(PR_SET_PDEATHSIG, SIGKILL)) fail("parent lifetime");
        if (setsid() < 0 || ioctl(STDIN_FILENO, TIOCSCTTY, 1) < 0) fail("guest controlling terminal");
        bind(root, "/dev", "dev", 0);
        bind(root, "/sys", "sys", 1);
        char path[4096]; target(root, "proc", path);
        if (mount("proc", path, "proc", MS_NOSUID | MS_NODEV | MS_NOEXEC, NULL)) fail("proc mount");
        if (*shared) bind(root, shared, "shared", 0);
        // Explicit host bridge: Android loader and tools are reachable only via /android/*.
        target(root, "android", path);
        const char *names[] = {"system", "apex", "vendor"};
        for (int i = 0; i < 3; i++) {
            char src[64], name[64]; snprintf(src, sizeof(src), "/%s", names[i]); snprintf(name, sizeof(name), "android/%s", names[i]);
            bind(root, src, name, 1);
        }
        // Android ELF interpreters use absolute /system and /apex paths.
        bind(root, "/system", "system", 1); bind(root, "/apex", "apex", 1); bind(root, "/vendor", "vendor", 1);
        pid_t guest = fork(); if (guest < 0) fail("guest fork");
        if (!guest) {
            if (chroot(root) || chdir(dir)) fail("chroot/start directory");
            clearenv(); setenv("HOME", "/root", 1); setenv("USER", "root", 1); setenv("LOGNAME", "root", 1);
            setenv("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin", 1);
            setenv("TERM", "xterm-256color", 1); setenv("LANG", "C.UTF-8", 1); setenv("TMPDIR", "/tmp", 1); setenv("SHELL", shell, 1);
            for (int i = 0; i < count; i++) putenv(variables[i]);
            execl(shell, shell, "-l", NULL); fail("guest shell");
        }
        int s; while (waitpid(guest, &s, 0) < 0 && errno == EINTR) {}
        // Exiting PID 1 kills descendants, so detached guests cannot retain mounts.
        _exit(WIFEXITED(s) ? WEXITSTATUS(s) : 128 + WTERMSIG(s));
    }
    int status = 0;
    for (;;) {
        if (stopped) kill(child, SIGKILL);
        if (waitpid(child, &status, 0) >= 0) break;
        if (errno != EINTR) return 125;
    }
    while (waitpid(-1, NULL, 0) >= 0 || errno == EINTR) {}
    // Release the running-session lease. Only the last root session repairs ownership.
    if (cleanupLease(lock)) { close(lock); return WIFEXITED(status) ? WEXITSTATUS(status) : 128 + WTERMSIG(status); }
    // No mounts in this namespace. Physical walk never follows guest symlinks.
    if (nftw(root, restore, 32, FTW_PHYS | FTW_DEPTH)) { perror("Restore app ownership; reauthorize root before managing files"); return 125; }
    unlink(pending); lchown(lockpath, owner, owner); close(lock);
    return WIFEXITED(status) ? WEXITSTATUS(status) : 128 + WTERMSIG(status);
}
