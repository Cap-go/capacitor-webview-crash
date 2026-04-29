import XCTest
@testable import WebViewCrashPlugin

final class WebViewCrashPluginTests: XCTestCase {
    func testPluginMetadata() {
        let plugin = WebViewCrashPlugin()

        XCTAssertEqual(plugin.identifier, "WebViewCrashPlugin")
        XCTAssertEqual(plugin.jsName, "WebViewCrash")
        XCTAssertEqual(plugin.pluginMethods.count, 3)
    }
}
