// SPDX-License-Identifier: GPL-3.0-only
package android.util;

/** The host benchmark has no Android logging service. */
public final class Log {
    public static int e(String tag, String message) { return 0; }
}
