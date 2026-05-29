package app.capgo.webviewcrash;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class WebViewCrashTest {

    @Test
    public void validateRestartScheduleConfigThrowsWhenBothSchedulesAreConfigured() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
            WebViewCrash.validateRestartScheduleConfig(1_000, "0 3 * * *")
        );

        assertEquals("Invalid WebViewCrash config: set either restartIntervalMs or restartCron, not both.", exception.getMessage());
    }

    @Test
    public void validateRestartScheduleConfigAllowsDisabledIntervalWithCron() {
        WebViewCrash.validateRestartScheduleConfig(0, "0 3 * * *");
    }

    @Test
    public void validateRestartScheduleConfigAllowsIntervalWithoutCron() {
        WebViewCrash.validateRestartScheduleConfig(1_000, null);
    }
}
