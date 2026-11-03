'use strict';

const fs = require('node:fs');
const crypto = require('node:crypto');
const cases = require('./evaluation-cases.json');

async function evaluate(base, expectedModel, fetchImpl = fetch) {
  const url = new URL(base);
  if (!['127.0.0.1', 'localhost', '[::1]'].includes(url.hostname)) throw new Error('Evaluation requires a loopback gateway');
  if (!expectedModel) throw new Error('Specify the installed local model');
  const health = await fetchImpl(new URL('/health', url), { signal: AbortSignal.timeout(5000) });
  const state = await health.json();
  if (!health.ok || state.mode !== 'ollama') throw new Error('Gateway is not configured for Ollama');
  const results = [];
  for (const item of cases.filter(testCase => testCase.facts)) {
    const start = performance.now();
    try {
      const response = await fetchImpl(new URL('/generate', url), {
        method: 'POST', headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ facts: item.facts }), signal: AbortSignal.timeout(100000)
      });
      const body = await response.json();
      const validProvider = response.ok && body.provider === 'ollama' && body.model === expectedModel;
      results.push({ id: item.id, input: item.facts, status: response.status, latencyMs: Math.round(performance.now() - start),
        result: body, validProvider, humanReview: 'pending' });
    } catch (error) {
      results.push({ id: item.id, input: item.facts, latencyMs: Math.round(performance.now() - start),
        error: error.name, validProvider: false, humanReview: 'pending' });
    }
    process.stderr.write(`${item.id}: recorded\n`);
  }
  return { recordedAt: new Date().toISOString(), model: expectedModel,
    gatewaySha256: crypto.createHash('sha256').update(fs.readFileSync(require.resolve('./server'))).digest('hex'),
    settings: { providerTimeoutMs: 90000, maxOutputTokens: 512 }, usage: 'unknown', monetaryCost: 'none (local inference)', results };
}

if (require.main === module) {
  evaluate(process.env.EVALUATION_GATEWAY || 'http://127.0.0.1:8100', process.env.EVALUATION_MODEL)
    .then(report => process.stdout.write(JSON.stringify(report, null, 2) + '\n'))
    .catch(error => { process.stderr.write(`${error.message}\n`); process.exitCode = 1; });
}
module.exports = { evaluate };
