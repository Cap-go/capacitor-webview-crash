import type { PluginListenerHandle } from '@capacitor/core';
import { WebPlugin } from '@capacitor/core';

import type { PendingCrashInfoResult, WebViewCrashInfo, WebViewCrashPlugin } from './definitions';

export class WebViewCrashWeb extends WebPlugin implements WebViewCrashPlugin {
  private dispatchedPendingEvents = new Set<string>();

  async getPendingCrashInfo(): Promise<PendingCrashInfoResult> {
    return { value: this.readPendingCrashInfo() };
  }

  async clearPendingCrashInfo(): Promise<void> {
    this.removePendingCrashInfo();
    this.dispatchedPendingEvents.clear();
  }

  async simulateCrashRecovery(): Promise<PendingCrashInfoResult> {
    const value = this.buildCrashInfo();
    this.writePendingCrashInfo(value);
    this.dispatchedPendingEvents.clear();
    this.flushPendingCrashEvent();
    this.flushPendingCrashEvent(WebViewCrashWeb.restartEventName);
    return { value };
  }

  async restartWebView(): Promise<PendingCrashInfoResult> {
    const value = this.buildCrashInfo('manualRestart');
    this.writePendingCrashInfo(value);
    this.dispatchedPendingEvents.clear();
    this.flushPendingCrashEvent(WebViewCrashWeb.restartEventName);
    return { value };
  }

  async addListener(eventName: string, listenerFunc: (...args: any[]) => any): Promise<PluginListenerHandle> {
    const handle = await super.addListener(eventName, listenerFunc);
    if (eventName === WebViewCrashWeb.crashEventName || eventName === WebViewCrashWeb.restartEventName) {
      this.flushPendingCrashEvent(eventName);
    }
    return handle;
  }

  private flushPendingCrashEvent(eventName = WebViewCrashWeb.crashEventName): void {
    if (this.dispatchedPendingEvents.has(eventName)) {
      return;
    }

    const value = this.readPendingCrashInfo();
    if (!value || !this.shouldDispatchEvent(eventName, value)) {
      return;
    }

    this.dispatchedPendingEvents.add(eventName);
    this.notifyListeners(eventName, value);
  }

  private shouldDispatchEvent(eventName: string, value: WebViewCrashInfo): boolean {
    if (eventName === WebViewCrashWeb.crashEventName) {
      return value.reason !== 'periodicRestart' && value.reason !== 'manualRestart';
    }

    return eventName === WebViewCrashWeb.restartEventName;
  }

  private buildCrashInfo(reason: WebViewCrashInfo['reason'] = 'simulated'): WebViewCrashInfo {
    const timestamp = Date.now();
    return {
      platform: 'web',
      timestamp,
      timestampISO: new Date(timestamp).toISOString(),
      reason,
      url: globalThis.location?.href,
      appState: 'active',
    };
  }

  private readPendingCrashInfo(): WebViewCrashInfo | null {
    const raw = globalThis.localStorage?.getItem(WebViewCrashWeb.storageKey);
    if (!raw) {
      return null;
    }

    try {
      return JSON.parse(raw) as WebViewCrashInfo;
    } catch {
      return null;
    }
  }

  private writePendingCrashInfo(value: WebViewCrashInfo): void {
    globalThis.localStorage?.setItem(WebViewCrashWeb.storageKey, JSON.stringify(value));
  }

  private removePendingCrashInfo(): void {
    globalThis.localStorage?.removeItem(WebViewCrashWeb.storageKey);
  }

  private static readonly crashEventName = 'webViewRestoredAfterCrash';
  private static readonly restartEventName = 'webViewRestoredAfterRestart';
  private static readonly storageKey = 'capgo.webview-crash.pending';
}
