# Platform Support

## Current Step 1 Support

- Android: launches through `AndroidMainActivity`
- iOS: exposes `MainViewController()` for Xcode integration
- Desktop: launches via `desktopMain`
- Web WASM: renders the shared Compose UI through `ComposeViewport`

## Current Limitations

- screens show scaffold-level behavior only
- no local persistence yet
- no polling/sync engine yet
- no secure storage or notifications yet
- Android push is not integrated in this step
