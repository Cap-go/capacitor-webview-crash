import { beforeEach, expect, test } from 'bun:test';

import { WebViewCrashWeb } from './web';

function createStorage() {
  const entries = new Map<string, string>();

  return {
    clear() {
      entries.clear();
    },
    getItem(key: string) {
      return entries.get(key) ?? null;
    },
    key(index: number) {
      return Array.from(entries.keys())[index] ?? null;
    },
    removeItem(key: string) {
      entries.delete(key);
    },
    setItem(key: string, value: string) {
      entries.set(key, value);
    },
    get length() {
      return entries.size;
    },
  };
}

beforeEach(() => {
  Object.defineProperty(globalThis, 'localStorage', {
    configurable: true,
    value: createStorage(),
  });
});

test('simulateCrashRecovery stores a pending crash payload', async () => {
  const plugin = new WebViewCrashWeb();

  const simulated = await plugin.simulateCrashRecovery();
  const pending = await plugin.getPendingCrashInfo();

  expect(simulated.value?.platform).toBe('web');
  expect(simulated.value?.reason).toBe('simulated');
  expect(pending.value).toEqual(simulated.value);
});

test('clearPendingCrashInfo removes the stored crash payload', async () => {
  const plugin = new WebViewCrashWeb();

  await plugin.simulateCrashRecovery();
  await plugin.clearPendingCrashInfo();

  const pending = await plugin.getPendingCrashInfo();
  expect(pending.value).toBeNull();
});
