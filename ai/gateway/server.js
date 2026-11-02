'use strict';

const http = require('node:http');

const MAX_BODY = 16384;
const MAX_PROVIDER_BODY = 65536;
const LIMITS = { title: 120, description: 3000, category: 80 };
const CATEGORIES = new Set(['OTHER', 'ELECTRONICS', 'HOME', 'FASHION', 'SPORTS', 'BOOKS']);

function validateFields(value, allowEmpty = false) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('invalid_fields');
  const keys = Object.keys(value);
  if ((!allowEmpty && keys.length === 0) || keys.some(key => !Object.hasOwn(LIMITS, key))) throw new Error('invalid_fields');
  const result = {};
  for (const key of keys) {
    const field = value[key];
    if (typeof field !== 'string' || field.length > LIMITS[key] || (key === 'category' && !CATEGORIES.has(field)) || /[<>\u0000-\u0008\u000b\u000c\u000e-\u001f]/.test(field)) {
      throw new Error('invalid_fields');
    }
    if (field.trim()) result[key] = field.trim();
  }
  if (!allowEmpty && Object.keys(result).length === 0) throw new Error('invalid_fields');
  return result;
}

async function boundedBody(response) {
  let size = 0;
  const chunks = [];
  for await (const chunk of response.body) {
    size += chunk.length;
    if (size > MAX_PROVIDER_BODY) throw new Error('provider_output_too_large');
    chunks.push(chunk);
  }
  return Buffer.concat(chunks).toString('utf8');
}

function createServer({ mode, ollamaUrl, model, fetchImpl = fetch, timeoutMs = 15000 }) {
  let busy = false;
  return http.createServer(async (req, res) => {
    const reply = (status, body) => {
      res.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
      res.end(JSON.stringify(body));
    };
    if (req.method === 'GET' && req.url === '/health') return reply(200, { status: 'ok', mode });
    if (req.method !== 'POST' || req.url !== '/generate') return reply(404, { error: 'not_found' });
    try {
      let size = 0;
      const chunks = [];
      for await (const chunk of req) {
        size += chunk.length;
        if (size > MAX_BODY) return reply(413, { error: 'request_too_large' });
        chunks.push(chunk);
      }
      let input;
      try {
        const request = JSON.parse(Buffer.concat(chunks).toString('utf8'));
        if (!request || typeof request !== 'object' || Object.keys(request).some(key => key !== 'facts')) throw new Error();
        input = validateFields(request.facts);
      } catch { return reply(400, { error: 'invalid_request' }); }

      if (mode === 'deterministic') return reply(200, { provider: 'deterministic', model: null, suggestions: input });
      if (mode !== 'ollama' || !model || !ollamaUrl) return reply(503, { error: 'provider_not_configured' });
      if (busy) return reply(429, { error: 'provider_busy' });
      busy = true;
      try {
        // Keep instructions separate from seller facts; the dark side gets no free prompt privileges.
        const response = await fetchImpl(new URL('/api/generate', ollamaUrl), {
          method: 'POST', headers: { 'content-type': 'application/json' }, signal: AbortSignal.timeout(timeoutMs),
          body: JSON.stringify({ model, stream: false, format: 'json', options: { num_predict: 512 },
            system: 'Return only a JSON object with title, description and optional category. Category must be OTHER, ELECTRONICS, HOME, FASHION, SPORTS or BOOKS. Use only supplied facts. Do not invent details or include HTML.',
            prompt: JSON.stringify(input) })
        });
        if (!response.ok) return reply(503, { error: 'provider_unavailable' });
        let suggestions;
        try {
          const outer = JSON.parse(await boundedBody(response));
          if (outer.done !== true || typeof outer.response !== 'string') throw new Error();
          suggestions = validateFields(JSON.parse(outer.response));
          // Reject explicit guarantees; even Sith promises need evidence we do not have.
          if (Object.values(suggestions).some(text => /\b(?:lifetime|warrant(?:y|ies)|guarantee(?:d|s)?|authentic(?:ity)?|genuine|certified)\b/i.test(text))) throw new Error();
        } catch { return reply(502, { error: 'invalid_provider_output' }); }
        return reply(200, { provider: 'ollama', model, suggestions });
      } catch {
        return reply(503, { error: 'provider_unavailable' });
      } finally { busy = false; }
    } catch {
      if (!res.headersSent) reply(400, { error: 'invalid_request' });
    }
  });
}

if (require.main === module) {
  const port = Number(process.env.PORT || 8100);
  const server = createServer({ mode: process.env.AI_GATEWAY_MODE || 'deterministic',
    ollamaUrl: process.env.OLLAMA_URL, model: process.env.OLLAMA_MODEL }).listen(port, '0.0.0.0');
  // PID 1 needs an explicit shutdown order; even a Sith knows when to leave the bridge.
  const shutdown = () => server.close(() => process.exit(0));
  process.on('SIGTERM', shutdown);
  process.on('SIGINT', shutdown);
}

module.exports = { createServer, validateFields };
