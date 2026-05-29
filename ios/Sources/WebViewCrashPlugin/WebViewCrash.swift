import Capacitor
import Foundation
import ObjectiveC.runtime
import UIKit
import WebKit

enum WebViewCrashBridge {
    static let crashEventName = "webViewRestoredAfterCrash"
    static let restartEventName = "webViewRestoredAfterRestart"
    static let periodicRestartReason = "periodicRestart"
    static let manualRestartReason = "manualRestart"

    static func pendingResult(_ value: [String: Any]?) -> [String: Any] {
        [
            "value": value ?? NSNull()
        ]
    }
}

struct WebViewCrashRestartOptions {
    let restartOnCrash: Bool
    let restartIntervalMs: Int
    let restartCron: WebViewCrashCronSchedule?
    let restartAfterCrashDelayMs: Int

    init(config: PluginConfig? = nil) {
        let intervalMs = max(0, config?.getInt("restartIntervalMs", 0) ?? 0)
        let cronExpression = config?.getString("restartCron", "") ?? ""

        if intervalMs > 0 && !cronExpression.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            fatalError("Invalid WebViewCrash config: set either restartIntervalMs or restartCron, not both.")
        }

        restartOnCrash = config?.getBoolean("restartOnCrash", true) ?? true
        restartIntervalMs = intervalMs
        restartCron = WebViewCrashCronSchedule(cronExpression)
        restartAfterCrashDelayMs = max(0, config?.getInt("restartAfterCrashDelayMs", 0) ?? 0)
    }

    var restartIntervalSeconds: TimeInterval {
        TimeInterval(restartIntervalMs) / 1_000
    }

    var nextRestartDelaySeconds: TimeInterval? {
        if let restartCron {
            return restartCron.nextDelaySeconds()
        }

        return restartIntervalMs > 0 ? restartIntervalSeconds : nil
    }

    var restartAfterCrashDelaySeconds: TimeInterval {
        TimeInterval(restartAfterCrashDelayMs) / 1_000
    }
}

struct WebViewCrashCronSchedule {
    private static let searchLimitMinutes = 366 * 24 * 60 * 5

    private let minutes: CronField
    private let hours: CronField
    private let daysOfMonth: CronField
    private let months: CronField
    private let daysOfWeek: CronField

    init?(_ expression: String?) {
        guard let expression else {
            return nil
        }

        let parts = expression
            .split(whereSeparator: { $0 == " " || $0 == "\t" || $0 == "\n" })
            .map(String.init)

        guard parts.count == 5,
            let minutes = CronField(parts[0], min: 0, max: 59),
            let hours = CronField(parts[1], min: 0, max: 23),
            let daysOfMonth = CronField(parts[2], min: 1, max: 31),
            let months = CronField(parts[3], min: 1, max: 12),
            let daysOfWeek = CronField(parts[4], min: 0, max: 7, normalizeSunday: true) else {
            return nil
        }

        self.minutes = minutes
        self.hours = hours
        self.daysOfMonth = daysOfMonth
        self.months = months
        self.daysOfWeek = daysOfWeek
    }

    func nextDelaySeconds(from date: Date = Date(), calendar sourceCalendar: Calendar = .current) -> TimeInterval? {
        var calendar = sourceCalendar
        calendar.timeZone = .current

        guard var candidate = calendar.date(byAdding: .minute, value: 1, to: date) else {
            return nil
        }

        var components = calendar.dateComponents([.year, .month, .day, .hour, .minute], from: candidate)
        components.second = 0
        components.nanosecond = 0
        guard let roundedCandidate = calendar.date(from: components) else {
            return nil
        }
        candidate = roundedCandidate

        for _ in 0..<Self.searchLimitMinutes {
            if matches(candidate, calendar: calendar) {
                return max(0, candidate.timeIntervalSince(date))
            }

            guard let nextCandidate = calendar.date(byAdding: .minute, value: 1, to: candidate) else {
                return nil
            }
            candidate = nextCandidate
        }

        return nil
    }

    private func matches(_ date: Date, calendar: Calendar) -> Bool {
        let components = calendar.dateComponents([.minute, .hour, .day, .month, .weekday], from: date)

        guard let minute = components.minute,
            let hour = components.hour,
            let day = components.day,
            let month = components.month,
            let weekday = components.weekday else {
            return false
        }

        guard minutes.matches(minute), hours.matches(hour), months.matches(month) else {
            return false
        }

        let cronWeekday = (weekday - 1) % 7
        let dayOfMonthMatches = daysOfMonth.matches(day)
        let dayOfWeekMatches = daysOfWeek.matches(cronWeekday)

        if daysOfMonth.restricted && daysOfWeek.restricted {
            return dayOfMonthMatches || dayOfWeekMatches
        }

        return dayOfMonthMatches && dayOfWeekMatches
    }

    private struct CronField {
        let values: Set<Int>
        let restricted: Bool

        init?(_ expression: String, min: Int, max: Int, normalizeSunday: Bool = false) {
            var values = Set<Int>()

            for part in expression.split(separator: ",", omittingEmptySubsequences: false) {
                guard Self.apply(String(part), to: &values, min: min, max: max, normalizeSunday: normalizeSunday) else {
                    return nil
                }
            }

            guard !values.isEmpty else {
                return nil
            }

            let allValueCount = normalizeSunday ? 7 : max - min + 1
            self.values = values
            self.restricted = values.count != allValueCount
        }

        func matches(_ value: Int) -> Bool {
            values.contains(value)
        }

        private static func apply(_ part: String, to values: inout Set<Int>, min: Int, max: Int, normalizeSunday: Bool) -> Bool {
            let stepParts = part.split(separator: "/", omittingEmptySubsequences: false)
            guard stepParts.count <= 2 else {
                return false
            }

            var step = 1
            if stepParts.count == 2 {
                guard let parsedStep = Int(stepParts[1]), parsedStep > 0 else {
                    return false
                }
                step = parsedStep
            }

            let rangePart = String(stepParts[0])
            let start: Int
            let end: Int

            if rangePart == "*" {
                start = min
                end = max
            } else if rangePart.contains("-") {
                let range = rangePart.split(separator: "-", omittingEmptySubsequences: false)
                guard range.count == 2, let rangeStart = Int(range[0]), let rangeEnd = Int(range[1]) else {
                    return false
                }
                start = rangeStart
                end = rangeEnd
            } else {
                guard let value = Int(rangePart) else {
                    return false
                }
                start = value
                end = value
            }

            guard start >= min, end <= max, start <= end else {
                return false
            }

            var value = start
            while value <= end {
                values.insert(normalize(value, normalizeSunday: normalizeSunday))
                value += step
            }

            return true
        }

        private static func normalize(_ value: Int, normalizeSunday: Bool) -> Int {
            normalizeSunday && value == 7 ? 0 : value
        }
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

        let reason = crashInfo["reason"] as? String
        return reason != WebViewCrashBridge.periodicRestartReason && reason != WebViewCrashBridge.manualRestartReason
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
