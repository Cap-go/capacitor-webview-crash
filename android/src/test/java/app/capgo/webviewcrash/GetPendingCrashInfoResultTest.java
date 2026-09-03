package app.capgo.webviewcrash;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

public class GetPendingCrashInfoResultTest {

    @Test
    public void pendingCrashInfoResultKeepsValueKeyWhenNothingIsPending() throws JSONException {
        JSONObject result = new JSONObject();
        JSONObject pendingCrashInfo = null;

        result.put("value", pendingCrashInfo != null ? pendingCrashInfo : JSONObject.NULL);

        assertTrue(result.has("value"));
        assertTrue(result.isNull("value"));
    }

    @Test
    public void puttingJavaNullDropsValueKey() throws JSONException {
        JSONObject result = new JSONObject();

        result.put("value", (Object) null);

        assertFalse(result.has("value"));
    }
}
