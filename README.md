# pi.dev Chat — Android App

Android chat client for [pi.dev](https://pi.dev) coding agent. Connects to `pi --mode rpc` running on your PC over WebSocket.

## Quick Start

**1. Install proxy dependencies on your PC:**
```bash
npm install ws
```

**2. Run the proxy on your PC:**
```bash
node pi-rpc-proxy.js
# listens on ws://0.0.0.0:8765
```

**3. Transfer `pi-dev-chat.apk` to your phone and install**

**4. Enter your PC's IP address (same WiFi network)**

That's it — no API key needed, no pi on the phone.

## How It Works

```
┌──────────────┐         WebSocket          ┌──────────────┐
│  Android App │◄───── ws://ip:8765 ───────►│  Your PC     │
│  (chat.html) │                             │              │
└──────────────┘    JSON events / commands   │  pi-rpc-     │
                                              │  proxy.js   │
                                              │              │
                                              │  stdin/stdout│
                                              └──────┬──────┘
                                                     ▼
                                              ┌──────────────┐
                                              │  pi --mode rpc│
                                              │  (your model) │
                                              └──────────────┘
```

The proxy spawns `pi --mode rpc --no-session` on your PC and bridges its stdin/stdout to WebSocket clients. The APK connects to that WebSocket and relays events to the chat UI.

## Config Screen

Only two fields:
- **PC IP Address** — Your PC's local IP on the WiFi network (e.g. `192.168.1.190`)
- **Proxy Port** — Defaults to `8765` (change if you run proxy on a different port)

No API key. No model selection. No binary path. Your PC handles everything.

## Build

Requires Android SDK 34, build-tools 34, JDK 17:

```bash
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

## License

MIT
