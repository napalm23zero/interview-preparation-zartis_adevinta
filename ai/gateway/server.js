'use strict';

const http = require('node:http');

const MAX_BODY = 90 * 1024 * 1024;
const MAX_PROVIDER_BODY = 65536;
const LIMITS = { title: 120, description: 3000, category: 80, condition: 20, price: 13 };
const CATEGORIES = new Set(['OTHER', 'ELECTRONICS', 'HOME', 'FASHION', 'SPORTS', 'BOOKS']);
const CONDITIONS = new Set(['NEW', 'LIKE_NEW', 'GOOD', 'FAIR', 'USED', 'BAD', 'VERY_BAD']);

function validateFields(value, allowEmpty = false) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('invalid_fields');
  const keys = Object.keys(value);
  if ((!allowEmpty && keys.length === 0) || keys.some(key => !Object.hasOwn(LIMITS, key))) throw new Error('invalid_fields');
  const result = {};
  for (const key of keys) {
    const field = key === 'price' && typeof value[key] === 'number' ? String(value[key]) : value[key];
    if (typeof field !== 'string' || field.length > LIMITS[key] || (key === 'category' && !CATEGORIES.has(field)) || (key === 'condition' && !CONDITIONS.has(field)) || (key === 'price' && !/^(?:0|[1-9][0-9]{0,9})(?:\.[0-9]{1,2})?$/.test(field)) || /[<>\u0000-\u0008\u000b\u000c\u000e-\u001f]/.test(field)) {
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

function createServer({ mode, ollamaUrl, model, fetchImpl = fetch, timeoutMs = 90000 }) {
  let busy = false;
  return http.createServer(async (req, res) => {
    const reply = (status, body) => {
      res.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
      res.end(JSON.stringify(body));
    };
    if (req.method === 'GET' && req.url === '/health') return reply(200, { status: 'ok', mode });
    if (req.method === 'GET' && req.url === '/models') {
      try {
        if (mode !== 'ollama' || !ollamaUrl) return reply(503, { error: 'provider_not_configured' });
        const response = await fetchImpl(new URL('/api/tags', ollamaUrl), { signal: AbortSignal.timeout(5000) });
        if (!response.ok) return reply(503, { error: 'provider_unavailable' });
        const data = JSON.parse(await boundedBody(response));
        if (!Array.isArray(data.models)) throw new Error('invalid_models');
        return reply(200, { models: data.models.map(item => item.name).filter(name => typeof name === 'string' && name.length > 0 && name.length <= 120), defaultModel: model || null });
      } catch { return reply(503, { error: 'provider_unavailable' }); }
    }
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
        if (!request || typeof request !== 'object' || Object.keys(request).some(key => !['facts', 'image', 'model'].includes(key))) throw new Error();
        if (request.model !== undefined && (typeof request.model !== 'string' || !request.model.trim() || request.model.length > 120)) throw new Error();
        if (request.image !== undefined && (typeof request.image !== 'string' || !request.image || request.image.length > 89478488 || request.image.length % 4 !== 0 || !/^[A-Za-z0-9+/]*={0,2}$/.test(request.image))) throw new Error();
        input = { facts: validateFields(request.facts, !!request.image), image: request.image, model: request.model };
        if (!input.image && !Object.keys(input.facts).length) throw new Error();
      } catch {
        console.error('Generation request failed validation');
        return reply(400, { error: 'invalid_request' });
      }

      if (mode === 'deterministic') return reply(200, { provider: 'deterministic', model: null, suggestions: input.facts });
      if (mode !== 'ollama' || !model || !ollamaUrl) {
        console.error('Ollama provider is not configured');
        return reply(503, { error: 'provider_not_configured' });
      }
      if (busy) {
        console.error('Ollama provider is busy');
        return reply(429, { error: 'provider_busy' });
      }
      busy = true;
      try {
        if (input.model) {
          const available = await fetchImpl(new URL('/api/tags', ollamaUrl), { signal: AbortSignal.timeout(5000) });
          if (!available.ok || !(JSON.parse(await boundedBody(available)).models || []).some(item => item.name === input.model)) return reply(400, { error: 'model_unavailable' });
        }
        // Keep instructions separate from seller facts; the dark side gets no free prompt privileges.
        const response = await fetchImpl(new URL('/api/generate', ollamaUrl), {
          method: 'POST', headers: { 'content-type': 'application/json' }, signal: AbortSignal.timeout(timeoutMs),
          body: JSON.stringify({ model: input.model || model, stream: false, think: false, format: 'json', options: { num_predict: 512 },
            system: 'Return only a JSON object with title, description, category, condition and price. Category must be OTHER, ELECTRONICS, HOME, FASHION, SPORTS or BOOKS. Condition must be NEW, LIKE_NEW, GOOD, FAIR, USED, BAD or VERY_BAD; use VERY_BAD for severe visible damage such as a shattered screen, infer only from visible evidence and omit if uncertain. Price is a rough second-hand estimate in Canadian dollars, as a non-negative number with at most two decimal places; omit if the item cannot be identified. Use only supplied facts and visible image details for the description. Do not invent details or include HTML.',
            prompt: JSON.stringify(input.facts), ...(input.image ? { images: [input.image] } : {}) })
        });
        if (!response.ok) {
          console.error(`Ollama returned HTTP ${response.status}`);
          return reply(503, { error: 'provider_unavailable' });
        }
        let suggestions;
        try {
          const outer = JSON.parse(await boundedBody(response));
          if (outer.done !== true || typeof outer.response !== 'string') throw new Error();
          suggestions = validateFields(JSON.parse(outer.response));
          // Reject explicit guarantees; even Sith promises need evidence we do not have.
          if (Object.values(suggestions).some(text => /\b(?:lifetime|warrant(?:y|ies)|guarantee(?:d|s)?|authentic(?:ity)?|genuine|certified)\b/i.test(text))) throw new Error();
        } catch {
          console.error('Ollama returned invalid or rejected suggestions');
          return reply(502, { error: 'invalid_provider_output' });
        }
        return reply(200, { provider: 'ollama', model: input.model || model, suggestions });
      } catch (error) {
        console.error(`Ollama request failed: ${error?.name || 'Error'}`);
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
