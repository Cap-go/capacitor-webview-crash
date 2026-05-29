package app.capgo.webviewcrash;

import android.os.Handler;
import android.os.Looper;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebView;
import androidx.appcompat.app.AppCompatActivity;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.WebViewListener;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.HashSet;
import java.util.Set;

@CapacitorPlugin(name = "WebViewCrash")
public class WebViewCrashPlugin extends Plugin {

    private final WebViewCrash implementation = new WebViewCrash();
    private final Set<String> dispatchedPendingEvents = new HashSet<>();
    private WebViewCrash.RestartOptions restartOptions = new WebViewCrash.RestartOptions(true, 0, 0);
    private Handler mainHandler;

    private final Runnable periodicRestartRunnable = () -> {
        WebView webView = bridge != null ? bridge.getWebView() : null;
        String url = webView != null ? webView.getUrl() : null;
        JSObject restartInfo = implementation.buildCrashInfo("periodicRestart", url, null, null);

        implementation.writePendingCrashInfo(getContext(), restartInfo);
        dispatchedPendingEvents.clear();
        restartWebView(0);
    };

    private final WebViewListener webViewListener = new WebViewListener() {
        @Override
        public boolean onRenderProcessGone(WebView webView, RenderProcessGoneDetail detail) {
            JSObject crashInfo = implementation.buildCrashInfo(
                "renderProcessGone",
                webView.getUrl(),
                detail.didCrash(),
                detail.rendererPriorityAtExit()
            );

            implementation.writePendingCrashInfo(getContext(), crashInfo);
            dispatchedPendingEvents.clear();

            if (!restartOptions.restartOnCrash) {
                return false;
            }

            restartWebView(restartOptions.restartAfterCrashDelayMs);
            return true;
        }
    };

    @Override
    public void load() {
        restartOptions = implementation.readRestartOptions(getConfig());
        bridge.addWebViewListener(webViewListener);
        schedulePeriodicRestart();
    }

    @Override
    protected void handleOnDestroy() {
        bridge.removeWebViewListener(webViewListener);
        cancelPeriodicRestart();
    }

    @Override
    public void addListener(PluginCall call) {
        super.addListener(call);

        String eventName = call.getString("eventName");
        if (WebViewCrash.CRASH_EVENT_NAME.equals(eventName) || WebViewCrash.RESTART_EVENT_NAME.equals(eventName)) {
            dispatchPendingCrashIfNeeded(eventName);
        }
    }

    @PluginMethod
    public void getPendingCrashInfo(PluginCall call) {
        JSObject result = new JSObject();
        result.put("value", implementation.readPendingCrashInfo(getContext()));
        call.resolve(result);
    }

    @PluginMethod
    public void clearPendingCrashInfo(PluginCall call) {
        implementation.clearPendingCrashInfo(getContext());
        dispatchedPendingEvents.clear();
        call.resolve();
    }

    @PluginMethod
    public void simulateCrashRecovery(PluginCall call) {
        String url = bridge.getWebView() != null ? bridge.getWebView().getUrl() : null;
        JSObject crashInfo = implementation.buildCrashInfo("simulated", url, null, null);

        implementation.writePendingCrashInfo(getContext(), crashInfo);
        dispatchedPendingEvents.clear();
        dispatchPendingCrashIfNeeded(WebViewCrash.CRASH_EVENT_NAME);
        dispatchPendingCrashIfNeeded(WebViewCrash.RESTART_EVENT_NAME);

        JSObject result = new JSObject();
        result.put("value", crashInfo);
        call.resolve(result);
    }

    private void dispatchPendingCrashIfNeeded(String eventName) {
        if (dispatchedPendingEvents.contains(eventName)) {
            return;
        }

        JSObject crashInfo = implementation.readPendingCrashInfo(getContext());
        if (crashInfo == null || !implementation.shouldDispatchEvent(eventName, crashInfo)) {
            return;
        }

        dispatchedPendingEvents.add(eventName);
        notifyListeners(eventName, crashInfo);
    }

    private void schedulePeriodicRestart() {
        cancelPeriodicRestart();
        if (restartOptions.restartIntervalMs <= 0) {
            return;
        }

        getMainHandler().postDelayed(periodicRestartRunnable, restartOptions.restartIntervalMs);
    }

    private void cancelPeriodicRestart() {
        if (mainHandler != null) {
            mainHandler.removeCallbacks(periodicRestartRunnable);
        }
    }

    private void restartWebView(int delayMs) {
        Runnable restart = () -> {
            AppCompatActivity currentActivity = getActivity();
            if (currentActivity == null) {
                schedulePeriodicRestart();
                return;
            }

            currentActivity.runOnUiThread(() -> {
                if (bridge != null) {
                    bridge.reset();
                }
                currentActivity.recreate();
            });
        };

        if (delayMs > 0) {
            getMainHandler().postDelayed(restart, delayMs);
        } else {
            getMainHandler().post(restart);
        }
    }

    private Handler getMainHandler() {
        if (mainHandler == null) {
            mainHandler = new Handler(Looper.getMainLooper());
        }

        return mainHandler;
    }
}
