import Capacitor
import Foundation
import ObjectiveC.runtime
import UIKit
import WebKit

enum WebViewCrashBridge {
    static let crashEventName = "webViewRestoredAfterCrash"
    static let restartEventName = "webViewRestoredAfterRestart"

    static func pendingResult(_ value: [String: Any]?) -> [String: Any] {
        [
            "value": value ?? NSNull()
        ]
    }
}

struct WebViewCrashRestartOptions {
    let restartOnCrash: Bool
    let restartIntervalMs: Int
    let restartAfterCrashDelayMs: Int

    init(config: PluginConfig? = nil) {
        restartOnCrash = config?.getBoolean("restartOnCrash", true) ?? true
        restartIntervalMs = max(0, config?.getInt("restartIntervalMs", 0) ?? 0)
        restartAfterCrashDelayMs = max(0, config?.getInt("restartAfterCrashDelayMs", 0) ?? 0)
    }

    var restartIntervalSeconds: TimeInterval {
        TimeInterval(restartIntervalMs) / 1_000
    }

    var restartAfterCrashDelaySeconds: TimeInterval {
        TimeInterval(restartAfterCrashDelayMs) / 1_000
    }
}

enum WebViewCrashRuntime {
    private static var options = WebViewCrashRestartOptions()

    static func update(options newOptions: WebViewCrashRestartOptions) {
        options = newOptions
    }

    static var restartOnCrash: Bool {
        options.restartOnCrash
    }

    static var restartAfterCrashDelaySeconds: TimeInterval {
        options.restartAfterCrashDelaySeconds
    }
}

enum WebViewCrashStore {
    private static let pendingCrashKey = "CapgoWebViewCrash.pendingInfo"
    private static let timestampFormatter = ISO8601DateFormatter()

    static func read() -> [String: Any]? {
        UserDefaults.standard.dictionary(forKey: pendingCrashKey)
    }

    static func write(_ value: [String: Any]) {
        UserDefaults.standard.set(value, forKey: pendingCrashKey)
    }

    static func clear() {
        UserDefaults.standard.removeObject(forKey: pendingCrashKey)
    }

    static func buildCrashInfo(platform: String, reason: String, url: String?, appState: String? = nil) -> [String: Any] {
        let date = Date()
        let timestamp = Int(date.timeIntervalSince1970 * 1000)
        var value: [String: Any] = [
            "platform": platform,
            "timestamp": timestamp,
            "timestampISO": timestampFormatter.string(from: date),
            "reason": reason
        ]

        if let url, !url.isEmpty {
            value["url"] = url
        }

        if let appState {
            value["appState"] = appState
        }

        return value
    }

    static func shouldDispatch(eventName: String, crashInfo: [String: Any]) -> Bool {
        if eventName == WebViewCrashBridge.restartEventName {
            return true
        }

        guard eventName == WebViewCrashBridge.crashEventName else {
            return false
        }

        return crashInfo["reason"] as? String != "periodicRestart"
    }
}

enum WebViewCrashSwizzler {
    private static var didInstall = false

    static func installIfNeeded() {
        guard !didInstall else {
            return
        }

        let originalSelector = #selector(WebViewDelegationHandler.webViewWebContentProcessDidTerminate(_:))
        let swizzledSelector = #selector(WebViewDelegationHandler.capgo_webViewCrash_webViewWebContentProcessDidTerminate(_:))

        guard
            let originalMethod = class_getInstanceMethod(WebViewDelegationHandler.self, originalSelector),
            let swizzledMethod = class_getInstanceMethod(WebViewDelegationHandler.self, swizzledSelector)
        else {
            return
        }

        method_exchangeImplementations(originalMethod, swizzledMethod)
        didInstall = true
    }
}

private extension WebViewDelegationHandler {
    @objc func capgo_webViewCrash_webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
        let crashInfo = WebViewCrashStore.buildCrashInfo(
            platform: "ios",
            reason: "webContentProcessDidTerminate",
            url: webView.url?.absoluteString,
            appState: UIApplication.shared.applicationState.capgoWebViewCrashValue
        )

        WebViewCrashStore.write(crashInfo)

        guard WebViewCrashRuntime.restartOnCrash else {
            return
        }

        let restart = {
            self.capgo_webViewCrash_webViewWebContentProcessDidTerminate(webView)
        }

        let delay = WebViewCrashRuntime.restartAfterCrashDelaySeconds
        if delay > 0 {
            DispatchQueue.main.asyncAfter(deadline: .now() + delay, execute: restart)
        } else {
            restart()
        }
    }
}

extension UIApplication.State {
    var capgoWebViewCrashValue: String {
        switch self {
        case .active:
            return "active"
        case .inactive:
            return "inactive"
        case .background:
            return "background"
        @unknown default:
            return "unknown"
        }
    }
}
