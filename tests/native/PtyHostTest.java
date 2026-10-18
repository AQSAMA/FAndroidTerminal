// SPDX-License-Identifier: GPL-3.0-only
import com.termux.terminal.JNI;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Runs the same JNI PTY source on Linux; this does not certify Android policy or IME. */
public final class PtyHostTest {
    private static FileDescriptor descriptor(int fd) throws Exception {
        FileDescriptor result = new FileDescriptor();
        Field field = FileDescriptor.class.getDeclaredField("fd"); field.setAccessible(true); field.set(result, fd);
        return result;
    }
    private static String readUntil(FileInputStream input, String marker) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        while (System.nanoTime() < deadline) {
            if (input.available() > 0) {
                byte[] data = new byte[4096]; int n = input.read(data);
                if (n < 0) break; bytes.write(data, 0, n);
                if (bytes.toString(StandardCharsets.UTF_8).contains(marker)) return bytes.toString(StandardCharsets.UTF_8);
            } else Thread.sleep(10);
        }
        throw new AssertionError("Missing " + marker + ": " + bytes.toString(StandardCharsets.UTF_8));
    }
    public static void main(String[] args) throws Exception {
        int[] pid = new int[1];
        String[] env = {"PATH=/usr/bin:/bin", "TERM=xterm-256color", "PS1=PTY_READY> "};
        int fd = JNI.createSubprocess("/bin/sh", "/tmp", new String[]{"/bin/sh", "-i"}, env, pid, 24, 80, 0, 0);
        FileDescriptor descriptor = descriptor(fd);
        FileInputStream input = new FileInputStream(descriptor);
        FileOutputStream output = new FileOutputStream(descriptor);
        try {
            readUntil(input, "PTY_READY>");
            output.write("test -t 0 && test -t 1 && echo PTY_OK; stty size\n".getBytes(StandardCharsets.UTF_8));
            String first = readUntil(input, "PTY_READY>");
            if (!first.contains("PTY_OK\r\n") || !first.contains("24 80")) throw new AssertionError(first);
            JNI.setPtyWindowSize(fd, 40, 120, 0, 0);
            output.write("stty size\n".getBytes(StandardCharsets.UTF_8));
            String resized = readUntil(input, "PTY_READY>");
            if (!resized.contains("40 120")) throw new AssertionError(resized);
            output.write("sleep 20\n".getBytes(StandardCharsets.UTF_8)); output.flush(); Thread.sleep(200);
            output.write(new byte[]{3}); output.flush();
            readUntil(input, "PTY_READY>");
            output.write("exit 23\n".getBytes(StandardCharsets.UTF_8)); output.flush();
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try { if (executor.submit(() -> JNI.waitFor(pid[0])).get(5, TimeUnit.SECONDS) != 23) throw new AssertionError("Exit code"); }
            finally { executor.shutdownNow(); }
        } finally {
            ProcessHandle.of(pid[0]).ifPresent(ProcessHandle::destroyForcibly);
            input.close(); output.close();
        }
        System.out.println("PASS: real PTY, terminal dimensions, resize, Ctrl-C/job control, and exit status");
    }
}
