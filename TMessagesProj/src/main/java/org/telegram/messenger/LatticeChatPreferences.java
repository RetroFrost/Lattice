/*
 * Lattice additions to Telegram for Android.
 * This file is licensed under GNU GPL v2 or later, matching the upstream project.
 */
package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

/** Lattice-only local preferences which are never synchronized to Telegram. */
public final class LatticeChatPreferences {
    private static final String PREFS_NAME = "lattice_chat_preferences";
    private static final String FILES_ONLY_PREFIX = "files_only_";

    private LatticeChatPreferences() {
    }

    private static SharedPreferences preferences() {
        Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            throw new IllegalStateException("Lattice preferences requested before ApplicationLoader initialization");
        }
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isFilesOnly(long dialogId) {
        if (dialogId >= 0) {
            return false;
        }
        return preferences().getBoolean(FILES_ONLY_PREFIX + dialogId, false);
    }

    public static boolean setFilesOnly(long dialogId, boolean enabled) {
        if (dialogId >= 0) {
            return false;
        }
        preferences().edit().putBoolean(FILES_ONLY_PREFIX + dialogId, enabled).apply();
        return enabled;
    }

    public static boolean toggleFilesOnly(long dialogId) {
        return setFilesOnly(dialogId, !isFilesOnly(dialogId));
    }
}
