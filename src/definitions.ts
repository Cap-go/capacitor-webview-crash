import type { PluginListenerHandle } from '@capacitor/core';

/**
 * Native reason reported for the previous WebView failure.
 */
export type WebViewCrashReason = 'renderProcessGone' | 'webContentProcessDidTerminate' | 'simulated';

/**
 * Platform that produced the stored crash marker.
 */
export type WebViewCrashPlatform = 'android' | 'ios' | 'web';

/**
 * Best-effort application state captured on iOS when the WebView process died.
 */
export type WebViewCrashAppState = 'active' | 'inactive' | 'background' | 'unknown';

/**
 * Metadata captured natively after the previous WebView process died.
 */
export interface WebViewCrashInfo {
  /**
   * Platform that detected and stored the crash marker.
   */
  platform: WebViewCrashPlatform;
  /**
   * Unix timestamp in milliseconds for when the crash marker was written.
   */
  timestamp: number;
  /**
   * ISO-8601 version of `timestamp`.
   */
  timestampISO: string;
  /**
   * Platform-specific reason for the crash marker.
   */
  reason: WebViewCrashReason;
  /**
   * Last known WebView URL when the crash marker was written.
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
   * iOS-only application state captured when the crash marker was written.
   */
  appState?: WebViewCrashAppState;
}

/**
 * Pending crash marker returned to JavaScript.
 */
export interface PendingCrashInfoResult {
  /**
   * Stored crash metadata, or `null` when no marker is pending.
   */
  value: WebViewCrashInfo | null;
}

/**
 * Capacitor API for recovered WebView crash detection.
 */
export interface WebViewCrashPlugin {
  /**
   * Returns the pending native crash marker, if one exists.
   */
  getPendingCrashInfo(): Promise<PendingCrashInfoResult>;

  /**
   * Clears the stored crash marker after the app has handled recovery.
   */
  clearPendingCrashInfo(): Promise<void>;

  /**
   * Creates a fake crash marker so recovery flows can be tested locally.
   */
  simulateCrashRecovery(): Promise<PendingCrashInfoResult>;

  /**
   * Fires after a new JavaScript runtime attaches a listener and a crash marker is still pending.
   */
  addListener(
    eventName: 'webViewRestoredAfterCrash',
    listenerFunc: (info: WebViewCrashInfo) => void,
  ): Promise<PluginListenerHandle>;

  /**
   * Removes all plugin listeners.
   */
  removeAllListeners(): Promise<void>;
}
