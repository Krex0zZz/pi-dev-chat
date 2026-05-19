# pi.dev Chat — Android App

Android chat client for [pi.dev](https://pi.dev) coding agent. Connects to `pi --mode rpc` running on your PC over WebSocket.

## Quick Start

**1. Install proxy dependencies on your PC:**
```bash
npm install ws
```

**2. Run the proxy on your PC:**
```bash
# Each prompt independent (no memory between prompts):
node pi-rpc-proxy.js

# Persistent sessions (pi remembers context, "New Chat" starts fresh):
node pi-rpc-proxy.js --session
```

Options:
- `--port <N>` — WebSocket port (default: `8765`)
- `--host <H>` — Bind address (default: `0.0.0.0`)
- `--session` — Enable persistent sessions

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

The proxy spawns `pi --mode rpc` on your PC and bridges its stdin/stdout to WebSocket clients. The APK connects to that WebSocket and relays events to the chat UI.

### Session Modes

| Mode | Command | Behavior |
|------|---------|----------|
| **No session** (default) | `node pi-rpc-proxy.js` | Each prompt is independent. No memory between prompts. |
| **Session** | `node pi-rpc-proxy.js --session` | Pi remembers context across prompts. "New Chat" button restarts pi for a fresh session. |

## Config Screen

Only two fields:
- **PC IP Address** — Your PC's local IP on the WiFi network (e.g. `192.168.1.190`)
- **Proxy Port** — Defaults to `8765` (change if you run proxy on a different port)

No API key. No model selection. No binary path. Your PC handles everything.

---

## ⚠️ WSL Users — Important!

If `pi` is installed inside WSL (Ubuntu), the proxy **must** run inside WSL too — Windows CMD/PowerShell won't find the `pi` binary and will crash with `spawn pi ENOENT`.

### The Problem

WSL2 runs in a VM with its own network. By default, `0.0.0.0` inside WSL binds to the VM's internal IP (e.g. `172.26.128.5`), **not** your Windows WiFi IP (`192.168.1.190`). Your phone can't reach it.

### Fix A — Mirrored Networking (Recommended)

This makes WSL share the Windows network stack permanently.

1. On Windows, create/edit `%USERPROFILE%\.wslconfig`:
```ini
[wsl2]
networkingMode=mirrored
```

2. In Windows CMD/PowerShell:
```powershell
wsl --shutdown
```

3. Re-enter WSL. Verify:
```bash
hostname -I   # should show 192.168.1.190 (your Windows WiFi IP)
```

4. **Allow inbound connections through Windows Firewall** (required!):
```powershell
# Run in Windows PowerShell as Administrator:
New-NetFirewallRule -DisplayName "WSL2-Port8765" -Direction Inbound -Protocol TCP -LocalPort 8765 -Action Allow
```

> ⚠️ Without this, Windows Firewall blocks inbound traffic to WSL2 even with mirrored networking, causing `ETIMEDOUT` on your phone.

5. Run the proxy inside WSL:
```bash
cd /path/to/pi-dev-app
npm install ws
node pi-rpc-proxy.js
```

Your phone now connects to `192.168.1.190:8765` — same as normal.

### Fix B — Use WSL's IP (Quick, temporary)

If you don't want to change WSL networking:

```bash
# Inside WSL, find the VM IP:
hostname -I | awk '{print $1}'
# e.g. 172.26.128.5
```

Run the proxy inside WSL, then enter **that WSL IP** in the phone app config.

> ⚠️ The WSL2 IP changes after every restart. Fix A avoids this.

### Common Errors

| Error | Cause | Fix |
|-------|-------|-----|
| `spawn pi ENOENT` | Running proxy in Windows CMD instead of WSL | Run `node pi-rpc-proxy.js` **inside WSL** |
| `ETIMEDOUT` / can't connect (WSL) | Windows Firewall blocking inbound TCP to WSL2 | Add firewall rule (step 4 above) |
| `ETIMEDOUT` / can't connect | Phone using `192.168.1.190` but proxy is on WSL VM IP | Use mirrored networking (Fix A) or enter WSL IP (Fix B) |
| `EADDRNOTAVAIL` | Binding to wrong interface | Ensure `--host 0.0.0.0` in proxy |

---

## Build

Requires Android SDK 34, build-tools 34, JDK 17:

```bash
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

## License

MIT
