package app.capgo.webviewcrash;

import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebView;
import androidx.appcompat.app.AppCompatActivity;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.WebViewListener;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "WebViewCrash")
public class WebViewCrashPlugin extends Plugin {

    private final WebViewCrash implementation = new WebViewCrash();
    private boolean didDispatchPendingEvent = false;

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
            didDispatchPendingEvent = false;

            AppCompatActivity currentActivity = getActivity();
            if (currentActivity != null) {
                currentActivity.runOnUiThread(() -> {
                    bridge.reset();
                    currentActivity.recreate();
                });
            }

            return true;
        }
    };

    @Override
    public void load() {
        bridge.addWebViewListener(webViewListener);
    }

    @Override
    protected void handleOnDestroy() {
        bridge.removeWebViewListener(webViewListener);
    }

    @Override
    public void addListener(PluginCall call) {
        super.addListener(call);

        if (WebViewCrash.EVENT_NAME.equals(call.getString("eventName"))) {
            dispatchPendingCrashIfNeeded();
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
        didDispatchPendingEvent = false;
        call.resolve();
    }

    @PluginMethod
    public void simulateCrashRecovery(PluginCall call) {
        String url = bridge.getWebView() != null ? bridge.getWebView().getUrl() : null;
        JSObject crashInfo = implementation.buildCrashInfo("simulated", url, null, null);

        implementation.writePendingCrashInfo(getContext(), crashInfo);
        didDispatchPendingEvent = false;
        dispatchPendingCrashIfNeeded();

        JSObject result = new JSObject();
        result.put("value", crashInfo);
        call.resolve(result);
    }

    private void dispatchPendingCrashIfNeeded() {
        if (didDispatchPendingEvent) {
            return;
        }

        JSObject crashInfo = implementation.readPendingCrashInfo(getContext());
        if (crashInfo == null) {
            return;
        }

        didDispatchPendingEvent = true;
        notifyListeners(WebViewCrash.EVENT_NAME, crashInfo);
    }
}
