import http from 'k6/http';
import { check, fail } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import exec from 'k6/execution';

const baseUrl = __ENV.BASE_URL || 'http://127.0.0.1:8090';
const cases = [
  { name: 'offset_page1', strategy: 'offset', page: 1 },
  { name: 'keyset_page1', strategy: 'keyset', page: 1 },
  { name: 'offset_page1000', strategy: 'offset', page: 1000 },
  { name: 'keyset_page1000', strategy: 'keyset', page: 1000 },
];
const latency = {};
const samples = {};
const errors = {};
const thresholds = { checks: ['rate==1'], http_req_failed: ['rate==0'] };
for (const item of cases) {
  latency[item.name] = new Trend(`${item.name}_latency`, true);
  samples[item.name] = new Counter(`${item.name}_samples`);
  errors[item.name] = new Rate(`${item.name}_errors`);
  thresholds[`${item.name}_samples`] = ['count==50'];
  thresholds[`${item.name}_errors`] = ['rate==0'];
}

export const options = {
  scenarios: {
    measured: { executor: 'shared-iterations', vus: 2, iterations: 50, maxDuration: '10m' },
  },
  thresholds,
  setupTimeout: '5m',
  summaryTrendStats: ['med', 'p(95)', 'min', 'max', 'avg'],
};

function request(item, expected, phase) {
  const response = http.get(
    `${baseUrl}/benchmark/history?strategy=${item.strategy}&page=${item.page}`,
    { timeout: '35s', tags: { case: item.name, phase } },
  );
  let ids = null;
  try {
    ids = response.json();
  } catch (_) {
    ids = null;
  }
  const wanted = expected[`page${item.page}`];
  const valid = response.status === 200 && Array.isArray(ids) && ids.length === 21
    && JSON.stringify(ids) === JSON.stringify(wanted);
  check(response, { [`${phase} ${item.name}: exact ordered IDs`]: () => valid });
  if (phase === 'measured') {
    errors[item.name].add(!valid);
    if (valid) {
      latency[item.name].add(response.timings.duration);
      samples[item.name].add(1);
    }
  }
  if (!valid) {
    fail(`${phase} ${item.name}: HTTP ${response.status} or pagination parity failure`);
  }
}

export function setup() {
  const response = http.get(`${baseUrl}/benchmark/expected`, { tags: { phase: 'setup' } });
  if (response.status !== 200) fail('Benchmark harness is not ready');
  const expected = response.json();
  for (const page of [1, 1000]) {
    if (!Array.isArray(expected[`page${page}`]) || expected[`page${page}`].length !== 21) {
      fail('Invalid fixture metadata');
    }
  }
  for (let i = 0; i < 5; i++) {
    for (const item of cases) request(item, expected, 'warmup');
  }
  return expected;
}

export default function (expected) {
  const sequence = exec.scenario.iterationInTest % 2 === 0 ? cases : [...cases].reverse();
  for (const item of sequence) request(item, expected, 'measured');
}

export function handleSummary(data) {
  const rows = cases.map(({ name }) => {
    const stats = data.metrics[`${name}_latency`]?.values || {};
    return {
      case: name,
      samples: data.metrics[`${name}_samples`]?.values.count || 0,
      p50_ms: stats.med ?? null,
      p95_ms: stats['p(95)'] ?? null,
      error_rate: data.metrics[`${name}_errors`]?.values.rate ?? null,
    };
  });
  const report = { recordedAt: new Date().toISOString(), baseUrl, vus: 2, iterations: 50, rows, raw: data };
  return {
    [__ENV.SUMMARY_PATH || 'target/benchmarks/k6-summary.json']: JSON.stringify(report, null, 2),
    stdout: `${JSON.stringify(rows, null, 2)}\nUse the process exit code and raw thresholds to determine success.\n`,
  };
}
