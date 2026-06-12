import { createServer, IncomingMessage, ServerResponse } from 'node:http';
import { handleJsonRpcRequest } from './rpc-handler.js';
import { TOOL_WHITELIST } from './types.js';

const MCP_PORT = parseInt(process.env.MCP_PORT || '3001', 10);
const MCP_MODE = process.env.MCP_MODE || 'mock';

/**
 * MCP Server — Kubernetes context provider for the AI analysis pipeline.
 *
 * Exposes JSON-RPC 2.0 endpoint on POST / and POST /rpc for MCP tool
 * invocations, and a GET /health endpoint for Docker healthcheck probes.
 *
 * Supports MCP_MODE=mock for local development without a real K8s cluster.
 */

function handleHealth(_req: IncomingMessage, res: ServerResponse): void {
  res.writeHead(200, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ status: 'ok', mode: MCP_MODE }));
}

function handleNotFound(_req: IncomingMessage, res: ServerResponse): void {
  res.writeHead(404, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ error: 'Not Found' }));
}

function handleMethodNotAllowed(_req: IncomingMessage, res: ServerResponse): void {
  res.writeHead(405, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ error: 'Method Not Allowed' }));
}

/**
 * Reads the full request body from an IncomingMessage stream.
 */
function readBody(req: IncomingMessage): Promise<string> {
  return new Promise((resolve, reject) => {
    const chunks: Buffer[] = [];
    req.on('data', (chunk: Buffer) => chunks.push(chunk));
    req.on('end', () => resolve(Buffer.concat(chunks).toString('utf-8')));
    req.on('error', reject);
  });
}

/**
 * Handles JSON-RPC POST requests on / or /rpc.
 */
async function handleRpcPost(req: IncomingMessage, res: ServerResponse): Promise<void> {
  const body = await readBody(req);
  const response = await handleJsonRpcRequest(body);

  res.writeHead(200, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify(response));
}

function requestHandler(req: IncomingMessage, res: ServerResponse): void {
  const url = req.url || '/';
  const method = req.method || 'GET';

  // Health endpoint
  if (url === '/health') {
    if (method === 'GET') {
      handleHealth(req, res);
    } else {
      handleMethodNotAllowed(req, res);
    }
    return;
  }

  // JSON-RPC endpoint on / or /rpc
  if (url === '/' || url === '/rpc') {
    if (method === 'POST') {
      handleRpcPost(req, res).catch((err) => {
        console.error('Error handling RPC request:', err);
        res.writeHead(500, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: 'Internal Server Error' }));
      });
    } else {
      handleMethodNotAllowed(req, res);
    }
    return;
  }

  handleNotFound(req, res);
}

const server = createServer(requestHandler);

export function main(): void {
  console.log(`MCP Server starting on port ${MCP_PORT}`);
  console.log(`MCP_MODE=${MCP_MODE} (${MCP_MODE === 'mock' ? 'synthetic K8s data' : 'live cluster'})`);

  server.listen(MCP_PORT, '0.0.0.0', () => {
    console.log(`MCP Server listening on 0.0.0.0:${MCP_PORT}`);
  });
}

// Start the server
main();

export { server, MCP_MODE, MCP_PORT, TOOL_WHITELIST };
