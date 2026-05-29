/// <reference types="@capacitor/cli" />

import type { PluginListenerHandle } from '@capacitor/core';

declare module '@capacitor/cli' {
  export interface PluginsConfig {
    /**
     * Configure native WebView restart behavior for `@capgo/capacitor-webview-crash`.
     */
    WebViewCrash?: WebViewCrashPluginConfig;
  }
}

/**
 * Native WebView restart behavior configured in `capacitor.config.ts`.
 */
export interface WebViewCrashPluginConfig {
  /**
   * Restart the WebView from native code when the renderer process dies.
   *
   * @default true
   */
  restartOnCrash?: boolean;

  /**
   * Fixed native interval, in milliseconds, for proactively replacing long-running WebViews.
   *
   * Set to `0` to disable scheduled restarts.
   *
   * @default 0
   */
  restartIntervalMs?: number;

  /**
   * Cron schedule for proactively replacing long-running WebViews.
   *
   * Uses standard 5-field cron syntax in the device local timezone:
   * `minute hour day-of-month month day-of-week`.
   *
   * Examples:
   * - `0 3 * * *` restarts every day at 03:00.
   * - `0,30 * * * *` restarts every 30 minutes.
   *
   * When set, this takes precedence over `restartIntervalMs`.
   */
  restartCron?: string;

  /**
   * Delay, in milliseconds, before restarting after a crash.
   *
   * @default 0
   */
  restartAfterCrashDelayMs?: number;
}

/**
 * Native reason reported for the previous WebView failure or restart.
 */
export type WebViewCrashReason =
  | 'renderProcessGone'
  | 'webContentProcessDidTerminate'
  | 'periodicRestart'
  | 'manualRestart'
  | 'simulated';

/**
 * Platform that produced the stored marker.
 */
export type WebViewCrashPlatform = 'android' | 'ios' | 'web';

/**
 * Best-effort application state captured on iOS when the WebView process died.
 */
export type WebViewCrashAppState = 'active' | 'inactive' | 'background' | 'unknown';

/**
 * Metadata captured natively after the previous WebView process died or was restarted.
 */
export interface WebViewCrashInfo {
  /**
   * Platform that detected and stored the marker.
   */
  platform: WebViewCrashPlatform;
  /**
   * Unix timestamp in milliseconds for when the marker was written.
   */
  timestamp: number;
  /**
   * ISO-8601 version of `timestamp`.
   */
  timestampISO: string;
  /**
   * Platform-specific reason for the crash or restart marker.
   */
  reason: WebViewCrashReason;
  /**
   * Last known WebView URL when the marker was written.
   */
  url?: string;
  /**
   * Android-only hint from `RenderProcessGoneDetail.didCrash()`.
   */
  didCrash?: boolean;
  /**
   * Android-only renderer priority reported at exit.
   */
  rendererPriorityAtExit?: number;
  /**
   * iOS-only application state captured when the marker was written.
   */
  appState?: WebViewCrashAppState;
}

/**
 * Pending crash or restart marker returned to JavaScript.
 */
export interface PendingCrashInfoResult {
  /**
   * Stored crash or restart metadata, or `null` when no marker is pending.
   */
  value: WebViewCrashInfo | null;
}

/**
 * Capacitor API for recovered WebView crash and restart detection.
 */
export interface WebViewCrashPlugin {
  /**
   * Returns the pending native crash or restart marker, if one exists.
   */
  getPendingCrashInfo(): Promise<PendingCrashInfoResult>;

  /**
   * Clears the stored marker after the app has handled recovery.
   */
  clearPendingCrashInfo(): Promise<void>;

  /**
   * Creates a fake crash marker so recovery flows can be tested locally.
   */
  simulateCrashRecovery(): Promise<PendingCrashInfoResult>;

  /**
   * Stores a manual restart marker and asks native code to create a fresh WebView.
   *
   * On Android this recreates the host Activity. On iOS this rebuilds the Capacitor bridge view so a new `WKWebView`
   * instance is created instead of reloading the current page.
   */
  restartWebView(): Promise<PendingCrashInfoResult>;

  /**
   * Fires after a new JavaScript runtime attaches a listener and a matching marker is still pending.
   */
  addListener(
    eventName: 'webViewRestoredAfterCrash' | 'webViewRestoredAfterRestart',
    listenerFunc: (info: WebViewCrashInfo) => void,
  ): Promise<PluginListenerHandle>;

  /**
   * Removes all plugin listeners.
   */
  removeAllListeners(): Promise<void>;
}
