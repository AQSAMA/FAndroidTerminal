// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal.sessions;
import android.os.ParcelFileDescriptor;
interface IRemoteTerminal {
    int uid() = 0;
    ParcelFileDescriptor open(in IBinder owner, String library, String cwd) = 1;
    void resize(int rows, int columns, int width, int height) = 2;
    int waitForExit() = 3;
    void close() = 4;
    void destroy() = 16777114;
}
