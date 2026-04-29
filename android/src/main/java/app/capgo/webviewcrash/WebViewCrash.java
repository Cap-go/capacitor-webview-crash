package app.capgo.webviewcrash;

import android.content.Context;
import com.getcapacitor.JSObject;
import java.time.Instant;
import org.json.JSONException;

final class WebViewCrash {

    static final String EVENT_NAME = "webViewRestoredAfterCrash";

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
}
