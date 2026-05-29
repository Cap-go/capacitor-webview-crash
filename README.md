# @capgo/capacitor-webview-crash

<a href="https://capgo.app/">
  <img
    src="https://raw.githubusercontent.com/Cap-go/capgo/main/assets/capgo_banner.png"
    alt="Capgo - Instant updates for Capacitor"
  />
</a>

<div align="center">
  <h2>
    <a href="https://capgo.app/?ref=plugin_webview_crash"> ➡️ Get Instant updates for your App with Capgo</a>
  </h2>
  <h2>
    <a href="https://capgo.app/consulting/?ref=plugin_webview_crash">
      Missing a feature? We’ll build the plugin for you 💪
    </a>
  </h2>
</div>

Detect recovered Capacitor WebView crashes, restart dead WebViews natively, and optionally recycle long-running WebViews on a fixed interval before memory pressure turns into an OOM.

## What It Does

- Stores a native crash marker when Android reports `onRenderProcessGone`.
- Hooks the iOS WebView termination callback and persists equivalent crash metadata.
- Restarts the WebView natively after crashes so recovery does not depend on a still-running JavaScript runtime.
- Can restart the WebView on a fixed native interval for kiosk, POS, signage, telemetry, and other always-on apps.
- Lets JavaScript request a native WebView restart with `restartWebView()` when the app wants to proactively recycle memory.
- Exposes the marker through an event, a polling method, and a simulation helper for testing recovery flows.
- Ships a web implementation that simulates the same recovery flow with local storage.

## What It Does Not Do

- Prevent the underlying WebView crash from happening.
- Restore lost in-memory JavaScript state automatically.
- Replace application-level state persistence. Persist critical state before enabling scheduled restarts.

## Compatibility

| Plugin version | Capacitor compatibility | Maintained |
| -------------- | ----------------------- | ---------- |
| v8.\*.\*       | v8.\*.\*                | ✅         |
| v7.\*.\*       | v7.\*.\*                | On demand  |
| v6.\*.\*       | v6.\*.\*                | On demand  |

Policy:

- New plugins start at version `8.0.0` (Capacitor 8 baseline).
- Backward compatibility for older Capacitor majors is supported on demand.

## Install

```bash
npm install @capgo/capacitor-webview-crash
npx cap sync
```

## Usage

```typescript
import { WebViewCrash } from '@capgo/capacitor-webview-crash';

await WebViewCrash.addListener('webViewRestoredAfterCrash', async (info) => {
  console.log('Recovered after a WebView crash', info);
  await WebViewCrash.clearPendingCrashInfo();
});

await WebViewCrash.addListener('webViewRestoredAfterRestart', async (info) => {
  console.log('Recovered after a native WebView restart', info);
  await WebViewCrash.clearPendingCrashInfo();
});

const pending = await WebViewCrash.getPendingCrashInfo();
if (pending.value) {
  console.log('Pending crash or restart marker', pending.value);
}
```

Use `simulateCrashRecovery()` in development or automated tests to exercise your recovery UI without forcing a real native WebView crash.

Call `restartWebView()` when the current JavaScript runtime decides the native WebView should be replaced:

```typescript
await WebViewCrash.restartWebView();
```

The call writes a pending marker with `reason: 'manualRestart'`, then native code restarts the WebView. Android recreates the host Activity. iOS rebuilds the Capacitor bridge view so a new `WKWebView` is created instead of reloading the current page.

## Native Auto Restart

Configure the plugin in `capacitor.config.ts` so restart decisions happen in native code, even when the JavaScript runtime is unavailable:

```typescript
import type { CapacitorConfig } from '@capacitor/cli';
import type { WebViewCrashPluginConfig } from '@capgo/capacitor-webview-crash';

const webViewCrash: WebViewCrashPluginConfig = {
  // Keep native crash recovery enabled. This is the default.
  restartOnCrash: true,

  // Recycle the WebView every hour. Set to 0 to disable interval restarts.
  restartIntervalMs: 60 * 60 * 1000,

  // Or use a 5-field cron schedule in the device local timezone.
  // When set, restartCron takes precedence over restartIntervalMs.
  restartCron: '0 3 * * *',

  // Optional delay before restarting after a crash.
  restartAfterCrashDelayMs: 0,
};

const config: CapacitorConfig = {
  plugins: {
    WebViewCrash: webViewCrash,
  },
};

export default config;
```

Use scheduled restarts for apps that stay open for days: kiosk screens, control-room dashboards, point-of-sale terminals, warehouse scanners, vehicle tablets, or any Capacitor app that cannot rely on users force-closing it. The restart is native, writes a pending marker with `reason: 'periodicRestart'`, and then creates a fresh WebView.

Set `restartIntervalMs` to a maintenance window that your product can tolerate, or set `restartCron` for a wall-clock schedule such as `0 3 * * *` for a daily 03:00 restart. `restartCron` uses 5-field cron syntax in the device local timezone and supports `*`, lists, ranges, and steps. When both are set, `restartCron` takes precedence. The user will get a fresh JavaScript runtime, so persist unsaved form state, queued events, and in-progress work before enabling a short interval or cron schedule.

## Config Type

```typescript
export interface WebViewCrashPluginConfig {
  restartOnCrash?: boolean;
  restartIntervalMs?: number;
  restartCron?: string;
  restartAfterCrashDelayMs?: number;
}
```

## Platform Notes

- **iOS:** Uses method swizzling on Capacitor's `WebViewDelegationHandler` to persist crash metadata before Capacitor reloads the WebView. Manual and scheduled restarts rebuild the Capacitor bridge view so a new `WKWebView` instance is created. No extra permissions are required.
- **Android:** Registers a Capacitor `WebViewListener` and persists crash metadata from `onRenderProcessGone`. Crash and scheduled restarts reset the bridge and recreate the host activity, giving the app a fresh WebView. No extra permissions are required.
- **Web:** There is no real browser crash detection. The web implementation only simulates the recovery flow with local storage.

## Documentation

- [Plugin docs](https://capgo.app/docs/plugins/webview-crash/)
- [Getting started guide](https://capgo.app/docs/plugins/webview-crash/getting-started/)
- [Example app](./example-app)

## API

<docgen-index>

- [`getPendingCrashInfo()`](#getpendingcrashinfo)
- [`clearPendingCrashInfo()`](#clearpendingcrashinfo)
- [`simulateCrashRecovery()`](#simulatecrashrecovery)
- [`restartWebView()`](#restartwebview)
- [`addListener('webViewRestoredAfterCrash' | 'webViewRestoredAfterRestart', ...)`](#addlistenerwebviewrestoredaftercrash--webviewrestoredafterrestart-)
- [`removeAllListeners()`](#removealllisteners)
- [Interfaces](#interfaces)
- [Type Aliases](#type-aliases)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

Capacitor API for recovered WebView crash and restart detection.

### getPendingCrashInfo()

```typescript
getPendingCrashInfo() => Promise<PendingCrashInfoResult>
```

Returns the pending native crash or restart marker, if one exists.

**Returns:** <code>Promise&lt;<a href="#pendingcrashinforesult">PendingCrashInfoResult</a>&gt;</code>

---

### clearPendingCrashInfo()

```typescript
clearPendingCrashInfo() => Promise<void>
```

Clears the stored marker after the app has handled recovery.

---

### simulateCrashRecovery()

```typescript
simulateCrashRecovery() => Promise<PendingCrashInfoResult>
```

Creates a fake crash marker so recovery flows can be tested locally.

**Returns:** <code>Promise&lt;<a href="#pendingcrashinforesult">PendingCrashInfoResult</a>&gt;</code>

---

### restartWebView()

```typescript
restartWebView() => Promise<PendingCrashInfoResult>
```

Stores a manual restart marker and asks native code to create a fresh WebView.

On Android this recreates the host Activity. On iOS this rebuilds the Capacitor bridge view so a new `WKWebView`
instance is created instead of reloading the current page.

**Returns:** <code>Promise&lt;<a href="#pendingcrashinforesult">PendingCrashInfoResult</a>&gt;</code>

---

### addListener('webViewRestoredAfterCrash' | 'webViewRestoredAfterRestart', ...)

```typescript
addListener(eventName: 'webViewRestoredAfterCrash' | 'webViewRestoredAfterRestart', listenerFunc: (info: WebViewCrashInfo) => void) => Promise<PluginListenerHandle>
```

Fires after a new JavaScript runtime attaches a listener and a matching marker is still pending.

| Param              | Type                                                                             |
| ------------------ | -------------------------------------------------------------------------------- |
| **`eventName`**    | <code>'webViewRestoredAfterCrash' \| 'webViewRestoredAfterRestart'</code>        |
| **`listenerFunc`** | <code>(info: <a href="#webviewcrashinfo">WebViewCrashInfo</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

---

### removeAllListeners()

```typescript
removeAllListeners() => Promise<void>
```

Removes all plugin listeners.

---

### Interfaces

#### PendingCrashInfoResult

Pending crash or restart marker returned to JavaScript.

| Prop        | Type                                                                  | Description                                                            |
| ----------- | --------------------------------------------------------------------- | ---------------------------------------------------------------------- |
| **`value`** | <code><a href="#webviewcrashinfo">WebViewCrashInfo</a> \| null</code> | Stored crash or restart metadata, or `null` when no marker is pending. |

#### WebViewCrashInfo

Metadata captured natively after the previous WebView process died or was restarted.

| Prop                         | Type                                                                  | Description                                                      |
| ---------------------------- | --------------------------------------------------------------------- | ---------------------------------------------------------------- |
| **`platform`**               | <code><a href="#webviewcrashplatform">WebViewCrashPlatform</a></code> | Platform that detected and stored the marker.                    |
| **`timestamp`**              | <code>number</code>                                                   | Unix timestamp in milliseconds for when the marker was written.  |
| **`timestampISO`**           | <code>string</code>                                                   | ISO-8601 version of `timestamp`.                                 |
| **`reason`**                 | <code><a href="#webviewcrashreason">WebViewCrashReason</a></code>     | Platform-specific reason for the crash or restart marker.        |
| **`url`**                    | <code>string</code>                                                   | Last known WebView URL when the marker was written.              |
| **`didCrash`**               | <code>boolean</code>                                                  | Android-only hint from `RenderProcessGoneDetail.didCrash()`.     |
| **`rendererPriorityAtExit`** | <code>number</code>                                                   | Android-only renderer priority reported at exit.                 |
| **`appState`**               | <code><a href="#webviewcrashappstate">WebViewCrashAppState</a></code> | iOS-only application state captured when the marker was written. |

#### PluginListenerHandle

| Prop         | Type                                      |
| ------------ | ----------------------------------------- |
| **`remove`** | <code>() =&gt; Promise&lt;void&gt;</code> |

### Type Aliases

#### WebViewCrashPlatform

Platform that produced the stored marker.

<code>'android' | 'ios' | 'web'</code>

#### WebViewCrashReason

Native reason reported for the previous WebView failure or restart.

<code>'renderProcessGone' | 'webContentProcessDidTerminate' | 'periodicRestart' | 'manualRestart' | 'simulated'</code>

#### WebViewCrashAppState

Best-effort application state captured on iOS when the WebView process died.

<code>'active' | 'inactive' | 'background' | 'unknown'</code>

</docgen-api>
