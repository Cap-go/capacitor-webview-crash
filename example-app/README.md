## WebView Crash Demo App

This app exercises the local `@capgo/capacitor-webview-crash` package and shows the pending crash marker plus the recovery event.

### Run

```bash
bun install
bun run start
```

### Sync Native Platforms

```bash
bunx cap sync ios
bunx cap sync android
```

### Notes

- `Simulate Recovery` creates a fake pending marker so the UI can be tested on desktop and mobile.
- On device, the plugin is meant to report a previous native WebView crash after recovery.
