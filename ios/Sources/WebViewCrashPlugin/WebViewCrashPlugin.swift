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
        CAPPluginMethod(name: "simulateCrashRecovery", returnType: CAPPluginReturnPromise)
    ]

    private var didDispatchPendingEvent = false

    override public func load() {
        WebViewCrashSwizzler.installIfNeeded()
    }

    @objc override public func addListener(_ call: CAPPluginCall) {
        super.addListener(call)

        if call.getString("eventName") == WebViewCrashBridge.eventName {
            dispatchPendingCrashIfNeeded()
        }
    }

    @objc func getPendingCrashInfo(_ call: CAPPluginCall) {
        call.resolve(WebViewCrashBridge.pendingResult(WebViewCrashStore.read()))
    }

    @objc func clearPendingCrashInfo(_ call: CAPPluginCall) {
        WebViewCrashStore.clear()
        didDispatchPendingEvent = false
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
        didDispatchPendingEvent = false
        dispatchPendingCrashIfNeeded()

        call.resolve(WebViewCrashBridge.pendingResult(crashInfo))
    }

    private func dispatchPendingCrashIfNeeded() {
        guard !didDispatchPendingEvent, let crashInfo = WebViewCrashStore.read() else {
            return
        }

        didDispatchPendingEvent = true
        notifyListeners(WebViewCrashBridge.eventName, data: crashInfo)
    }
}
