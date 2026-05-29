package app.capgo.webviewcrash;

import android.content.Context;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginConfig;
import java.time.Instant;
import org.json.JSONException;

final class WebViewCrash {

    static final String CRASH_EVENT_NAME = "webViewRestoredAfterCrash";
    static final String RESTART_EVENT_NAME = "webViewRestoredAfterRestart";
    static final String PERIODIC_RESTART_REASON = "periodicRestart";
    static final String MANUAL_RESTART_REASON = "manualRestart";

    private static final String PREFERENCES_NAME = "CapgoWebViewCrash";
    private static final String PENDING_CRASH_KEY = "pendingCrashInfo";

    JSObject buildCrashInfo(String reason, String url, Boolean didCrash, Integer rendererPriorityAtExit) {
        long timestamp = System.currentTimeMillis();
        JSObject crashInfo = new JSObject();
        crashInfo.put("platform", "android");
        crashInfo.put("timestamp", timestamp);
        crashInfo.put("timestampISO", Instant.ofEpochMilli(timestamp).toString());
        crashInfo.put("reason", reason);

        if (url != null && !url.isBlank()) {
            crashInfo.put("url", url);
        }

        if (didCrash != null) {
            crashInfo.put("didCrash", didCrash);
        }

        if (rendererPriorityAtExit != null) {
            crashInfo.put("rendererPriorityAtExit", rendererPriorityAtExit);
        }

        return crashInfo;
    }

    JSObject readPendingCrashInfo(Context context) {
        String raw = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).getString(PENDING_CRASH_KEY, null);
        if (raw == null) {
            return null;
        }

        try {
            return new JSObject(raw);
        } catch (JSONException ignored) {
            return null;
        }
    }

    void writePendingCrashInfo(Context context, JSObject value) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).edit().putString(PENDING_CRASH_KEY, value.toString()).apply();
    }

    void clearPendingCrashInfo(Context context) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).edit().remove(PENDING_CRASH_KEY).apply();
    }

    boolean shouldDispatchEvent(String eventName, JSObject crashInfo) {
        if (RESTART_EVENT_NAME.equals(eventName)) {
            return true;
        }

        if (!CRASH_EVENT_NAME.equals(eventName)) {
            return false;
        }

        String reason = crashInfo.optString("reason", "");
        return !PERIODIC_RESTART_REASON.equals(reason) && !MANUAL_RESTART_REASON.equals(reason);
    }

    RestartOptions readRestartOptions(PluginConfig config) {
        return new RestartOptions(
            config.getBoolean("restartOnCrash", true),
            Math.max(0, config.getInt("restartIntervalMs", 0)),
            Math.max(0, config.getInt("restartAfterCrashDelayMs", 0))
        );
    }

    static final class RestartOptions {

        final boolean restartOnCrash;
        final int restartIntervalMs;
        final int restartAfterCrashDelayMs;

        RestartOptions(boolean restartOnCrash, int restartIntervalMs, int restartAfterCrashDelayMs) {
            this.restartOnCrash = restartOnCrash;
            this.restartIntervalMs = restartIntervalMs;
            this.restartAfterCrashDelayMs = restartAfterCrashDelayMs;
        }
    }
}
