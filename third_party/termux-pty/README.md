# Termux PTY source

Pinned source and modifications are listed in ../../THIRD_PARTY_NOTICES.md.
Native JNI symbol/class names are retained to avoid unnecessary rewrites. The app owns
ParcelFileDescriptor lifetime and does not use JNI.close in addition to stream closure.
These files create processes and PTYs; they do not embed a Termux bootstrap or grant
another app access to an installed Termux environment. Apply patches only with provenance.
