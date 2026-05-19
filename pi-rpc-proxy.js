#!/usr/bin/env node
/**
 * pi-rpc-proxy — Bridges `pi --mode rpc` to a WebSocket server.
 *
 * Run on your PC where pi is installed:
 *   node pi-rpc-proxy.js [--port 8765] [--host 0.0.0.0]
 *
 * The Android APK connects to ws://<your-pc-ip>:<port>
 */
const { spawn } = require('child_process');
const { WebSocketServer } = require('ws');

const port = parseInt(process.argv[2]) || 8765;
const host = process.argv.includes('--host')
  ? process.argv[process.argv.indexOf('--host') + 1]
  : '0.0.0.0';

const piProcess = spawn('pi', ['--mode', 'rpc', '--no-session'], {
  env: { ...process.env, PI_TELEMETRY: '0', PI_SKIP_VERSION_CHECK: '1', PI_OFFLINE: '1' },
});

const clients = new Set();
const wss = new WebSocketServer({ host, port });
console.log(`pi-rpc-proxy listening on ws://${host}:${port}`);

// Broadcast pi stdout to all connected clients
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
  wss.close();
  process.exit(code || 0);
});

wss.on('connection', ws => {
  clients.add(ws);
  console.log(`Client connected (${clients.size} total)`);

  ws.on('message', msg => {
    const text = typeof msg === 'string' ? msg : msg.toString();
    if (text.trim()) {
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
  piProcess.kill('SIGTERM');
  wss.close(() => process.exit(0));
});
