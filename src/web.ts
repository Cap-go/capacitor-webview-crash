import type { PluginListenerHandle } from '@capacitor/core';
import { WebPlugin } from '@capacitor/core';

import type { PendingCrashInfoResult, WebViewCrashInfo, WebViewCrashPlugin } from './definitions';

export class WebViewCrashWeb extends WebPlugin implements WebViewCrashPlugin {
  private didDispatchPendingEvent = false;

  async getPendingCrashInfo(): Promise<PendingCrashInfoResult> {
    return { value: this.readPendingCrashInfo() };
  }

  async clearPendingCrashInfo(): Promise<void> {
    this.removePendingCrashInfo();
    this.didDispatchPendingEvent = false;
  }

  async simulateCrashRecovery(): Promise<PendingCrashInfoResult> {
    const value = this.buildCrashInfo();
    this.writePendingCrashInfo(value);
    this.didDispatchPendingEvent = false;
    this.flushPendingCrashEvent();
    return { value };
  }

  async addListener(eventName: string, listenerFunc: (...args: any[]) => any): Promise<PluginListenerHandle> {
    const handle = await super.addListener(eventName, listenerFunc);
    if (eventName === WebViewCrashWeb.eventName) {
      this.flushPendingCrashEvent();
    }
    return handle;
  }

  private flushPendingCrashEvent(): void {
    if (this.didDispatchPendingEvent) {
      return;
    }

    const value = this.readPendingCrashInfo();
    if (!value) {
      return;
    }

    this.didDispatchPendingEvent = true;
    this.notifyListeners(WebViewCrashWeb.eventName, value);
  }

  private buildCrashInfo(): WebViewCrashInfo {
    const timestamp = Date.now();
    return {
      platform: 'web',
      timestamp,
      timestampISO: new Date(timestamp).toISOString(),
      reason: 'simulated',
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

  private static readonly eventName = 'webViewRestoredAfterCrash';
  private static readonly storageKey = 'capgo.webview-crash.pending';
}
