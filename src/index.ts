import { registerPlugin } from '@capacitor/core';

import type { WebViewCrashPlugin } from './definitions';

const WebViewCrash = registerPlugin<WebViewCrashPlugin>('WebViewCrash', {
  web: () => import('./web').then((m) => new m.WebViewCrashWeb()),
});

export * from './definitions';
export { WebViewCrash };
