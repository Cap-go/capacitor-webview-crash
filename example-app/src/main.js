import './style.css';

import { WebViewCrash } from '@capgo/capacitor-webview-crash';

const pendingNode = document.querySelector('#pending');
const logNode = document.querySelector('#log');

void boot();

async function boot() {
  await WebViewCrash.addListener('webViewRestoredAfterCrash', (info) => {
    log(`event: ${JSON.stringify(info)}`);
    renderPending(info);
  });

  bind('#refresh', refreshPendingState);
  bind('#simulate', simulateRecovery);
  bind('#clear', clearPendingState);

  log('listener attached');
  await refreshPendingState();
}

async function refreshPendingState() {
  const pending = await WebViewCrash.getPendingCrashInfo();
  renderPending(pending.value);
  log(`refresh: ${pending.value ? 'marker found' : 'no marker'}`);
}

async function simulateRecovery() {
  const result = await WebViewCrash.simulateCrashRecovery();
  renderPending(result.value);
  log('simulated recovery marker created');
}

async function clearPendingState() {
  await WebViewCrash.clearPendingCrashInfo();
  renderPending(null);
  log('pending marker cleared');
}

function bind(selector, handler) {
  const element = document.querySelector(selector);
  element?.addEventListener('click', () => {
    void handler();
  });
}

function renderPending(value) {
  pendingNode.textContent = value ? JSON.stringify(value, null, 2) : 'null';
}

function log(message) {
  const item = document.createElement('li');
  item.textContent = `${new Date().toLocaleTimeString()}  ${message}`;
  logNode.prepend(item);
}
