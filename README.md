# @capgo/capacitor-webview-crash

<a href="https://capgo.app/">
  <img
    src="https://raw.githubusercontent.com/Cap-go/capgo/main/assets/capgo_banner.png"
    alt="Capgo - Instant updates for capacitor"
  />
</a>

<div align="center">
  <h2>
    <a href="https://capgo.app/?ref=plugin_webview_crash"> ➡️ Get Instant updates for your App with Capgo</a>
  </h2>
  <h2>
    <a href="https://capgo.app/consulting/?ref=plugin_webview_crash">
      {' '}
      Missing a feature? We’ll build the plugin for you 💪
    </a>
  </h2>
</div>

## Snapshot

- **Plugin name:** `WebView Crash`
- **One-line value:** `Detect a crashed Capacitor WebView and tell the next JavaScript runtime what happened.`
- **Maintainer:** `Capgo`
- **Status:** `alpha`

## Pre-Release Checklist

- [x] Replace all template placeholders in this README.
- [x] Replace the Capgo CTA tracking slug.
- [x] Replace the template keywords in `package.json`.
- [x] Remove the bootstrap-only init script from the generated plugin copy.
- [x] Update `src/definitions.ts` with the real public API and JSDoc.
- [ ] Set GitHub repo description to start with `Capacitor plugin for ...`.
- [ ] Set GitHub repo homepage to `https://capgo.app/docs/plugins/webview-crash/`.
- [ ] Open the docs/website PR for the Capgo site.
- [ ] Run `bun run verify` on a machine with full iOS toolchain support before publishing.

## Problem & Scope

### Why this plugin exists

A WebView renderer crash kills the current JavaScript context. After the app recovers, the next JS runtime has no built-in signal that the previous WebView died and some in-memory state may have been lost. This plugin stores that crash marker natively and exposes it to the recovered runtime.

### What it does

- Stores a native crash marker when Android reports `onRenderProcessGone`.
- Hooks the iOS WebView termination callback and preserves equivalent crash metadata.
- Exposes that marker through an event, a polling method, and a simulation helper for testing.

### What it does not do

- Prevent the underlying WebView crash from happening.
- Restore lost in-memory JavaScript state automatically.

## Capgo Links

- **Plugin docs URL:** `https://capgo.app/docs/plugins/webview-crash/`
- **Plugin tutorial URL:** `Pending publication`
- **Website/docs repo:** `https://github.com/Cap-go/website`

## Compatibility

| Plugin version | Capacitor compatibility | Maintained |
| -------------- | ----------------------- | ---------- |
| v8.\*.\*       | v8.\*.\*                | ✅         |
| v7.\*.\*       | v7.\*.\*                | On demand  |
| v6.\*.\*       | v6.\*.\*                | On demand  |

Policy:

- New plugins start at version `8.0.0` (Capacitor 8 baseline).
- Backward compatibility for older Capacitor majors is supported on demand.

## Template Base

This repository was initialized from `Cap-go/capacitor-plugin-template` and then rewritten with the real WebView crash recovery API and native implementations.

## Install

```bash
bun add @capgo/capacitor-webview-crash
bunx cap sync
```

## Minimal Usage

```typescript
import { WebViewCrash } from '@capgo/capacitor-webview-crash';

await WebViewCrash.addListener('webViewRestoredAfterCrash', async (info) => {
  console.log('Recovered after a WebView crash', info);
  await WebViewCrash.clearPendingCrashInfo();
});

const pending = await WebViewCrash.getPendingCrashInfo();
if (pending.value) {
  console.log('Pending crash marker', pending.value);
}
```

## Integration Notes

- **iOS:** Uses method swizzling on Capacitor's `WebViewDelegationHandler` to persist crash metadata before Capacitor reloads the WebView. No extra permissions are required.
- **Android:** Registers a Capacitor `WebViewListener` and persists crash metadata from `onRenderProcessGone`. No extra permissions are required.
- **Web:** There is no real browser crash detection. The web implementation only simulates the recovery flow with local storage.

## Example App

The `example-app/` folder is linked via `file:..` and shows the pending marker, recovery event, and simulation button for local validation.

## API

<docgen-index>

- [`getPendingCrashInfo()`](#getpendingcrashinfo)
- [`clearPendingCrashInfo()`](#clearpendingcrashinfo)
- [`simulateCrashRecovery()`](#simulatecrashrecovery)
- [`addListener('webViewRestoredAfterCrash', ...)`](#addlistenerwebviewrestoredaftercrash-)
- [`removeAllListeners()`](#removealllisteners)
- [Interfaces](#interfaces)
- [Type Aliases](#type-aliases)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

Capacitor API for recovered WebView crash detection.

### getPendingCrashInfo()

```typescript
getPendingCrashInfo() => Promise<PendingCrashInfoResult>
```

Returns the pending native crash marker, if one exists.

**Returns:** <code>Promise&lt;<a href="#pendingcrashinforesult">PendingCrashInfoResult</a>&gt;</code>

---

### clearPendingCrashInfo()

```typescript
clearPendingCrashInfo() => Promise<void>
```

Clears the stored crash marker after the app has handled recovery.

---

### simulateCrashRecovery()

```typescript
simulateCrashRecovery() => Promise<PendingCrashInfoResult>
```

Creates a fake crash marker so recovery flows can be tested locally.

**Returns:** <code>Promise&lt;<a href="#pendingcrashinforesult">PendingCrashInfoResult</a>&gt;</code>

---

### addListener('webViewRestoredAfterCrash', ...)

```typescript
addListener(eventName: 'webViewRestoredAfterCrash', listenerFunc: (info: WebViewCrashInfo) => void) => Promise<PluginListenerHandle>
```

Fires after a new JavaScript runtime attaches a listener and a crash marker is still pending.

| Param              | Type                                                                             |
| ------------------ | -------------------------------------------------------------------------------- |
| **`eventName`**    | <code>'webViewRestoredAfterCrash'</code>                                         |
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

Pending crash marker returned to JavaScript.

| Prop        | Type                                                                  | Description                                                 |
| ----------- | --------------------------------------------------------------------- | ----------------------------------------------------------- |
| **`value`** | <code><a href="#webviewcrashinfo">WebViewCrashInfo</a> \| null</code> | Stored crash metadata, or `null` when no marker is pending. |

#### WebViewCrashInfo

Metadata captured natively after the previous WebView process died.

| Prop                         | Type                                                                  | Description                                                            |
| ---------------------------- | --------------------------------------------------------------------- | ---------------------------------------------------------------------- |
| **`platform`**               | <code><a href="#webviewcrashplatform">WebViewCrashPlatform</a></code> | Platform that detected and stored the crash marker.                    |
| **`timestamp`**              | <code>number</code>                                                   | Unix timestamp in milliseconds for when the crash marker was written.  |
| **`timestampISO`**           | <code>string</code>                                                   | ISO-8601 version of `timestamp`.                                       |
| **`reason`**                 | <code><a href="#webviewcrashreason">WebViewCrashReason</a></code>     | Platform-specific reason for the crash marker.                         |
| **`url`**                    | <code>string</code>                                                   | Last known WebView URL when the crash marker was written.              |
| **`didCrash`**               | <code>boolean</code>                                                  | Android-only hint from `RenderProcessGoneDetail.didCrash()`.           |
| **`rendererPriorityAtExit`** | <code>number</code>                                                   | Android-only renderer priority reported at exit.                       |
| **`appState`**               | <code><a href="#webviewcrashappstate">WebViewCrashAppState</a></code> | iOS-only application state captured when the crash marker was written. |

#### PluginListenerHandle

| Prop         | Type                                      |
| ------------ | ----------------------------------------- |
| **`remove`** | <code>() =&gt; Promise&lt;void&gt;</code> |

### Type Aliases

#### WebViewCrashPlatform

Platform that produced the stored crash marker.

<code>'android' | 'ios' | 'web'</code>

#### WebViewCrashReason

Native reason reported for the previous WebView failure.

<code>'renderProcessGone' | 'webContentProcessDidTerminate' | 'simulated'</code>

#### WebViewCrashAppState

Best-effort application state captured on iOS when the WebView process died.

<code>'active' | 'inactive' | 'background' | 'unknown'</code>

</docgen-api>
