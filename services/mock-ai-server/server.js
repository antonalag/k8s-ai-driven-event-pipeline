'use strict';

const http = require('node:http');

const PORT = parseInt(process.env.MOCK_AI_PORT || '8090', 10);

// --- AiAnalysis response templates keyed by detected failure type ---

const RESPONSES = {
  crashloop: {
    podName: 'crashloop-pod-e2e-001',
    namespace: 'chaos-validation',
    verdict: 'UNHEALTHY',
    rootCauseAnalysis: 'Pod is in CrashLoopBackOff due to application startup failure. The container exits immediately after starting, indicating a misconfigured entrypoint or missing dependency.',
    recommendedActions: [
      'kubectl describe pod crashloop-pod-e2e-001 -n chaos-validation',
      'kubectl logs crashloop-pod-e2e-001 -n chaos-validation --previous',
      'Review liveness probe configuration and startup command'
    ],
    mcpToolsUsed: ['describe_pod', 'get_events', 'get_logs'],
    mcpContextAvailable: true,
    modelUsed: 'mock-model'
  },
  oomkill: {
    podName: 'oomkilled-pod-e2e-002',
    namespace: 'chaos-validation',
    verdict: 'UNHEALTHY',
    rootCauseAnalysis: 'Container was OOMKilled. Memory limit of 64Mi is insufficient for the application workload.',
    recommendedActions: [
      'kubectl describe pod oomkilled-pod-e2e-002 -n chaos-validation',
      'Increase memory limit to at least 128Mi in deployment spec',
      'Review application memory usage patterns'
    ],
    mcpToolsUsed: ['describe_pod', 'get_events'],
    mcpContextAvailable: true,
    modelUsed: 'mock-model'
  },
  imagepull: {
    podName: 'imagepull-pod-e2e-003',
    namespace: 'chaos-validation',
    verdict: 'UNHEALTHY',
    rootCauseAnalysis: 'Image pull failed. The specified image tag does not exist in the registry.',
    recommendedActions: [
      'kubectl describe pod imagepull-pod-e2e-003 -n chaos-validation',
      'Verify image name and tag exist in the container registry',
      'Check imagePullSecrets configuration'
    ],
    mcpToolsUsed: ['describe_pod', 'get_events'],
    mcpContextAvailable: true,
    modelUsed: 'mock-model'
  },
  default: {
    podName: 'unknown-pod',
    namespace: 'default',
    verdict: 'UNHEALTHY',
    rootCauseAnalysis: 'Pod is in a failed state. Further investigation needed.',
    recommendedActions: [
      'kubectl describe pod -n default',
      'kubectl get events -n default'
    ],
    mcpToolsUsed: ['describe_pod'],
    mcpContextAvailable: true,
    modelUsed: 'mock-model'
  }
};

/**
 * Detect failure type keyword from the last message content.
 * @param {string} content - message content to inspect
 * @returns {{ key: string, label: string }}
 */
function detectKeyword(content) {
  const lower = (content || '').toLowerCase();
  if (lower.includes('crashloop') || lower.includes('crashloopbackoff')) {
    return { key: 'crashloop', label: 'crashloop' };
  }
  if (lower.includes('oomkill') || lower.includes('oomkilled')) {
    return { key: 'oomkill', label: 'oomkill' };
  }
  if (lower.includes('imagepull') || lower.includes('imagepullbackoff')) {
    return { key: 'imagepull', label: 'imagepull' };
  }
  return { key: 'default', label: 'default' };
}

/**
 * Build an OpenAI-compatible chat completion response.
 * @param {object} analysis - AiAnalysis object
 * @returns {object}
 */
function buildCompletionResponse(analysis) {
  return {
    id: 'mock-completion-001',
    object: 'chat.completion',
    created: Math.floor(Date.now() / 1000),
    model: 'mock-model',
    choices: [
      {
        index: 0,
        message: {
          role: 'assistant',
          content: JSON.stringify(analysis)
        },
        finish_reason: 'stop'
      }
    ],
    usage: {
      prompt_tokens: 100,
      completion_tokens: 200,
      total_tokens: 300
    }
  };
}

/**
 * Read the full request body as a string.
 * @param {http.IncomingMessage} req
 * @returns {Promise<string>}
 */
function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    req.on('data', (chunk) => chunks.push(chunk));
    req.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
    req.on('error', reject);
  });
}

// --- HTTP Server ---

const server = http.createServer(async (req, res) => {
  // Health endpoint
  if (req.method === 'GET' && req.url === '/health') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ status: 'ok' }));
    return;
  }

  // Chat completions endpoint
  if (req.method === 'POST' && req.url === '/v1/chat/completions') {
    let body;
    try {
      const raw = await readBody(req);
      body = JSON.parse(raw);
    } catch {
      res.writeHead(400, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: 'Invalid JSON body' }));
      return;
    }

    // Extract last message content for keyword detection
    const messages = body.messages || [];
    const lastMessage = messages[messages.length - 1] || {};
    const content = lastMessage.content || '';

    const { key, label } = detectKeyword(content);
    console.log(`[MOCK-AI] POST /v1/chat/completions — detected: ${label}`);

    const analysis = RESPONSES[key];
    const response = buildCompletionResponse(analysis);

    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(response));
    return;
  }

  // 404 for everything else
  res.writeHead(404, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ error: 'Not Found' }));
});

server.listen(PORT, () => {
  console.log(`[MOCK-AI] Server listening on port ${PORT}`);
});
