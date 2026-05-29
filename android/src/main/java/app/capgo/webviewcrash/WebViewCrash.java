package app.capgo.webviewcrash;

import android.content.Context;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginConfig;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
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
        int restartIntervalMs = Math.max(0, config.getInt("restartIntervalMs", 0));
        String restartCronExpression = config.getString("restartCron", null);

        validateRestartScheduleConfig(restartIntervalMs, restartCronExpression);

        return new RestartOptions(
            config.getBoolean("restartOnCrash", true),
            restartIntervalMs,
            CronSchedule.parse(restartCronExpression),
            Math.max(0, config.getInt("restartAfterCrashDelayMs", 0))
        );
    }

    static void validateRestartScheduleConfig(int restartIntervalMs, String restartCronExpression) {
        if (restartIntervalMs > 0 && restartCronExpression != null && !restartCronExpression.isBlank()) {
            throw new IllegalStateException("Invalid WebViewCrash config: set either restartIntervalMs or restartCron, not both.");
        }
    }

    static final class RestartOptions {

        final boolean restartOnCrash;
        final int restartIntervalMs;
        final CronSchedule restartCron;
        final int restartAfterCrashDelayMs;

        RestartOptions(boolean restartOnCrash, int restartIntervalMs, CronSchedule restartCron, int restartAfterCrashDelayMs) {
            this.restartOnCrash = restartOnCrash;
            this.restartIntervalMs = restartIntervalMs;
            this.restartCron = restartCron;
            this.restartAfterCrashDelayMs = restartAfterCrashDelayMs;
        }

        Long nextRestartDelayMs() {
            if (restartCron != null) {
                return restartCron.nextDelayMs();
            }

            return restartIntervalMs > 0 ? (long) restartIntervalMs : null;
        }
    }

    static final class CronSchedule {

        private static final int SEARCH_LIMIT_MINUTES = 366 * 24 * 60 * 5;

        private final CronField minutes;
        private final CronField hours;
        private final CronField daysOfMonth;
        private final CronField months;
        private final CronField daysOfWeek;

        private CronSchedule(CronField minutes, CronField hours, CronField daysOfMonth, CronField months, CronField daysOfWeek) {
            this.minutes = minutes;
            this.hours = hours;
            this.daysOfMonth = daysOfMonth;
            this.months = months;
            this.daysOfWeek = daysOfWeek;
        }

        static CronSchedule parse(String expression) {
            if (expression == null || expression.isBlank()) {
                return null;
            }

            String[] parts = expression.trim().split("\\s+");
            if (parts.length != 5) {
                return null;
            }

            CronField minutes = CronField.parse(parts[0], 0, 59, false);
            CronField hours = CronField.parse(parts[1], 0, 23, false);
            CronField daysOfMonth = CronField.parse(parts[2], 1, 31, false);
            CronField months = CronField.parse(parts[3], 1, 12, false);
            CronField daysOfWeek = CronField.parse(parts[4], 0, 7, true);

            if (minutes == null || hours == null || daysOfMonth == null || months == null || daysOfWeek == null) {
                return null;
            }

            return new CronSchedule(minutes, hours, daysOfMonth, months, daysOfWeek);
        }

        Long nextDelayMs() {
            ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
            ZonedDateTime candidate = now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1);

            for (int index = 0; index < SEARCH_LIMIT_MINUTES; index++) {
                if (matches(candidate)) {
                    return Math.max(0, Duration.between(now, candidate).toMillis());
                }

                candidate = candidate.plusMinutes(1);
            }

            return null;
        }

        private boolean matches(ZonedDateTime value) {
            if (!minutes.matches(value.getMinute()) || !hours.matches(value.getHour()) || !months.matches(value.getMonthValue())) {
                return false;
            }

            boolean dayOfMonthMatches = daysOfMonth.matches(value.getDayOfMonth());
            boolean dayOfWeekMatches = daysOfWeek.matches(value.getDayOfWeek().getValue() % 7);

            if (daysOfMonth.restricted && daysOfWeek.restricted) {
                return dayOfMonthMatches || dayOfWeekMatches;
            }

            return dayOfMonthMatches && dayOfWeekMatches;
        }
    }

    private static final class CronField {

        private final boolean[] values;
        private final boolean restricted;

        private CronField(boolean[] values, boolean restricted) {
            this.values = values;
            this.restricted = restricted;
        }

        static CronField parse(String expression, int min, int max, boolean normalizeSunday) {
            boolean[] values = new boolean[normalizeSunday ? 7 : max + 1];

            for (String part : expression.split(",", -1)) {
                if (part.isBlank() || !applyPart(values, part, min, max, normalizeSunday)) {
                    return null;
                }
            }

            int selectedCount = 0;
            for (boolean selected : values) {
                if (selected) {
                    selectedCount++;
                }
            }

            if (selectedCount == 0) {
                return null;
            }

            int allValueCount = normalizeSunday ? 7 : max - min + 1;
            return new CronField(values, selectedCount != allValueCount);
        }

        boolean matches(int value) {
            return value >= 0 && value < values.length && values[value];
        }

        private static boolean applyPart(boolean[] values, String part, int min, int max, boolean normalizeSunday) {
            String[] stepParts = part.split("/", -1);
            if (stepParts.length > 2) {
                return false;
            }

            int step = 1;
            if (stepParts.length == 2) {
                step = parseNumber(stepParts[1]);
                if (step <= 0) {
                    return false;
                }
            }

            String rangePart = stepParts[0];
            int start;
            int end;
            if ("*".equals(rangePart)) {
                start = min;
                end = max;
            } else if (rangePart.contains("-")) {
                String[] range = rangePart.split("-", -1);
                if (range.length != 2) {
                    return false;
                }
                start = parseNumber(range[0]);
                end = parseNumber(range[1]);
            } else {
                start = parseNumber(rangePart);
                end = start;
            }

            if (start < min || end > max || start > end) {
                return false;
            }

            for (int value = start; value <= end; value += step) {
                values[normalizeValue(value, normalizeSunday)] = true;
            }

            return true;
        }

        private static int parseNumber(String value) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }

        private static int normalizeValue(int value, boolean normalizeSunday) {
            return normalizeSunday && value == 7 ? 0 : value;
        }
    }
}
