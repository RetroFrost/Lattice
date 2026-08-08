/*
 * Lattice additions to Telegram for Android.
 * This file is licensed under GNU GPL v2 or later, matching the upstream project.
 */
package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

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

    public static ArrayList<Long> getFilesOnlyDialogs() {
        ArrayList<Long> result = new ArrayList<>();
        for (Map.Entry<String, ?> entry : preferences().getAll().entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(FILES_ONLY_PREFIX) || !Boolean.TRUE.equals(entry.getValue())) {
                continue;
            }
            try {
                long dialogId = Long.parseLong(key.substring(FILES_ONLY_PREFIX.length()));
                if (dialogId < 0) {
                    result.add(dialogId);
                }
            } catch (NumberFormatException ignore) {
                // Ignore stale or malformed local preference entries.
            }
        }
        Collections.sort(result);
        return result;
    }

    public static int getFilesOnlyDialogsCount() {
        return getFilesOnlyDialogs().size();
    }

    public static void clearFilesOnlyDialogs() {
        SharedPreferences prefs = preferences();
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith(FILES_ONLY_PREFIX)) {
                editor.remove(key);
            }
        }
        editor.apply();
    }
}
