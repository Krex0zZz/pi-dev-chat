#!/usr/bin/env node
/**
 * pi-rpc-proxy — Bridges `pi --mode rpc` to a WebSocket server.
 *
 * Run on your PC where pi is installed:
 *   node pi-rpc-proxy.js [--port 8765] [--host 0.0.0.0] [--session]
 *
 * The Android APK connects to ws://<your-pc-ip>:<port>
 *
 * Options:
 *   --port <N>    WebSocket port (default: 8765)
 *   --host <H>    Bind address  (default: 0.0.0.0)
 *   --session     Enable persistent sessions (default: no-session)
 *
 * Session mode:
 *   Without --session, each prompt is independent (no memory between prompts).
 *   With --session, pi remembers context. The "New Chat" button restarts pi
 *   to start a fresh session.
 */
const { spawn } = require('child_process');
const { WebSocketServer } = require('ws');

const args = process.argv.slice(2);

function getArg(name) {
  const idx = args.indexOf(name);
  return idx >= 0 ? args[idx + 1] : undefined;
}
const port = parseInt(getArg('--port')) || 8765;
const host = getArg('--host') || '0.0.0.0';
const sessionMode = args.includes('--session');

console.log(`Session mode: ${sessionMode ? 'enabled' : 'disabled (--no-session)'}`);

let piProcess;

function spawnPi() {
  if (piProcess) {
    try { piProcess.kill('SIGTERM'); } catch (_) {}
  }
  const cmdArgs = ['--mode', 'rpc'];
  if (!sessionMode) cmdArgs.push('--no-session');

  piProcess = spawn('pi', cmdArgs, {
    env: { ...process.env, PI_TELEMETRY: '0', PI_SKIP_VERSION_CHECK: '1', PI_OFFLINE: '1' },
  });

  let buf = '';
  piProcess.stdout.on('data', chunk => {
    buf += typeof chunk === 'string' ? chunk : chunk.toString();
    const lines = buf.split('\n');
    buf = lines.pop();
    for (const line of lines) {
      if (line.trim()) {
        for (const ws of clients) {
          if (ws.readyState === 1) ws.send(line);
        }
      }
    }
  });

  piProcess.stderr.on('data', chunk => process.stderr.write(chunk));

  piProcess.on('close', code => {
    console.log(`pi process exited with code ${code}`);
    if (!process.exitCode) {
      // If we didn't already exit, try to restart
      setTimeout(() => {
        console.log('Restarting pi process...');
        spawnPi();
      }, 2000);
    }
  });

  console.log(`pi spawned: ${cmdArgs.join(' ')}`);
}

const clients = new Set();
const wss = new WebSocketServer({ host, port });
console.log(`pi-rpc-proxy listening on ws://${host}:${port}`);

spawnPi();

wss.on('connection', ws => {
  clients.add(ws);
  console.log(`Client connected (${clients.size} total)`);

  ws.on('message', msg => {
    const text = typeof msg === 'string' ? msg : msg.toString();
    try {
      const json = JSON.parse(text);
      if (json.type === 'new_session') {
        console.log('New session requested — restarting pi');
        spawnPi();
        // Broadcast a notification to all clients
        const notify = JSON.stringify({ type: 'session_reset' });
        for (const c of clients) {
          if (c.readyState === 1) c.send(notify);
        }
        return;
      }
    } catch (_) { /* not JSON, pass through */ }

    if (text.trim() && piProcess && piProcess.stdin.writable) {
      piProcess.stdin.write(text + '\n');
    }
  });

  ws.on('close', () => {
    clients.delete(ws);
    console.log(`Client disconnected (${clients.size} remaining)`);
  });

  ws.on('error', err => {
    clients.delete(ws);
    console.error(`WebSocket error: ${err.message}`);
  });
});

// Graceful shutdown
process.on('SIGINT', () => {
  console.log('\nShutting down…');
  if (piProcess) piProcess.kill('SIGTERM');
  wss.close(() => process.exit(0));
});
