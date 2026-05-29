import Capacitor
import Foundation
import UIKit

@objc(WebViewCrashPlugin)
public class WebViewCrashPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "WebViewCrashPlugin"
    public let jsName = "WebViewCrash"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "getPendingCrashInfo", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "clearPendingCrashInfo", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "simulateCrashRecovery", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "restartWebView", returnType: CAPPluginReturnPromise)
    ]

    private var dispatchedPendingEvents = Set<String>()
    private var restartOptions = WebViewCrashRestartOptions()
    private var restartTimer: Timer?

    override public func load() {
        restartOptions = WebViewCrashRestartOptions(config: getConfig())
        WebViewCrashRuntime.update(options: restartOptions)
        WebViewCrashSwizzler.installIfNeeded()
        schedulePeriodicRestart()
    }

    deinit {
        restartTimer?.invalidate()
    }

    @objc override public func addListener(_ call: CAPPluginCall) {
        super.addListener(call)

        if let eventName = call.getString("eventName"),
            eventName == WebViewCrashBridge.crashEventName || eventName == WebViewCrashBridge.restartEventName {
            dispatchPendingCrashIfNeeded(eventName: eventName)
        }
    }

    @objc func getPendingCrashInfo(_ call: CAPPluginCall) {
        call.resolve(WebViewCrashBridge.pendingResult(WebViewCrashStore.read()))
    }

    @objc func clearPendingCrashInfo(_ call: CAPPluginCall) {
        WebViewCrashStore.clear()
        dispatchedPendingEvents.removeAll()
        call.resolve()
    }

    @objc func simulateCrashRecovery(_ call: CAPPluginCall) {
        let crashInfo = WebViewCrashStore.buildCrashInfo(
            platform: "ios",
            reason: "simulated",
            url: bridge?.webView?.url?.absoluteString,
            appState: UIApplication.shared.applicationState.capgoWebViewCrashValue
        )

        WebViewCrashStore.write(crashInfo)
        dispatchedPendingEvents.removeAll()
        dispatchPendingCrashIfNeeded(eventName: WebViewCrashBridge.crashEventName)
        dispatchPendingCrashIfNeeded(eventName: WebViewCrashBridge.restartEventName)

        call.resolve(WebViewCrashBridge.pendingResult(crashInfo))
    }

    @objc func restartWebView(_ call: CAPPluginCall) {
        guard bridge?.viewController is CAPBridgeViewController else {
            call.reject("Unable to restart WebView because the bridge view controller is unavailable.")
            return
        }

        let restartInfo = WebViewCrashStore.buildCrashInfo(
            platform: "ios",
            reason: WebViewCrashBridge.manualRestartReason,
            url: bridge?.webView?.url?.absoluteString,
            appState: UIApplication.shared.applicationState.capgoWebViewCrashValue
        )

        WebViewCrashStore.write(restartInfo)
        dispatchedPendingEvents.removeAll()
        call.resolve(WebViewCrashBridge.pendingResult(restartInfo))

        DispatchQueue.main.async { [weak self] in
            _ = self?.recreateBridgeWebView()
        }
    }

    private func dispatchPendingCrashIfNeeded(eventName: String) {
        guard !dispatchedPendingEvents.contains(eventName),
            let crashInfo = WebViewCrashStore.read(),
            WebViewCrashStore.shouldDispatch(eventName: eventName, crashInfo: crashInfo) else {
            return
        }

        dispatchedPendingEvents.insert(eventName)
        notifyListeners(eventName, data: crashInfo)
    }

    private func schedulePeriodicRestart() {
        restartTimer?.invalidate()

        guard let restartDelaySeconds = restartOptions.nextRestartDelaySeconds, restartDelaySeconds > 0 else {
            return
        }

        DispatchQueue.main.async { [weak self] in
            guard let self else {
                return
            }

            restartTimer = Timer.scheduledTimer(withTimeInterval: restartDelaySeconds, repeats: false) { [weak self] _ in
                self?.restartWebView(reason: WebViewCrashBridge.periodicRestartReason)
            }
        }
    }

    private func restartWebView(reason: String) {
        DispatchQueue.main.async { [weak self] in
            guard let self, let webView = bridge?.webView else {
                return
            }

            let restartInfo = WebViewCrashStore.buildCrashInfo(
                platform: "ios",
                reason: reason,
                url: webView.url?.absoluteString,
                appState: UIApplication.shared.applicationState.capgoWebViewCrashValue
            )

            WebViewCrashStore.write(restartInfo)
            dispatchedPendingEvents.removeAll()
            if !recreateBridgeWebView() {
                webView.reload()
                schedulePeriodicRestart()
            }
        }
    }

    private func recreateBridgeWebView() -> Bool {
        guard let viewController = bridge?.viewController as? CAPBridgeViewController else {
            return false
        }

        restartTimer?.invalidate()
        viewController.webView?.stopLoading()
        viewController.webView?.navigationDelegate = nil
        viewController.webView?.uiDelegate = nil
        viewController.loadView()
        viewController.loadWebView()
        return true
    }
}
