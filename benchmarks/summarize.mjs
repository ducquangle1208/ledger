import fs from 'node:fs';
import path from 'node:path';

const directory = process.argv[2];
if (!directory) throw new Error('Usage: node benchmarks/summarize.mjs <evidence-directory>');
const read = name => JSON.parse(fs.readFileSync(path.join(directory, name), 'utf8'));
const median = values => [...values].sort((a, b) => a - b)[Math.floor(values.length / 2)];
const cases = ['index-before', 'index-after', 'offset-page1', 'keyset-page1', 'offset-page1000', 'keyset-page1000'];
const sql = cases.map(name => {
  const runs = read(`${name}.json`).map(run => run[0]);
  if (runs.length !== 5 || runs.some(run => !Number.isFinite(run['Execution Time']))) {
    throw new Error(`Invalid EXPLAIN evidence: ${name}`);
  }
  const scans = new Set();
  const visit = plan => {
    if (plan['Node Type'].includes('Scan')) scans.add(`${plan['Node Type']}${plan['Index Name'] ? ` (${plan['Index Name']})` : ''}`);
    (plan.Plans || []).forEach(visit);
  };
  runs.forEach(run => visit(run.Plan));
  return {
    case: name, runs: runs.length,
    median_execution_ms: median(runs.map(run => run['Execution Time'])),
    median_shared_hit_blocks: median(runs.map(run => run.Plan['Shared Hit Blocks'] || 0)),
    median_shared_read_blocks: median(runs.map(run => run.Plan['Shared Read Blocks'] || 0)),
    scans: [...scans],
  };
});
const k6 = read('k6-summary.json');
const wanted = ['offset_page1', 'keyset_page1', 'offset_page1000', 'keyset_page1000'];
const requiredThresholds = ['checks', 'http_req_failed', ...wanted.flatMap(name => [`${name}_samples`, `${name}_errors`])];
if (k6.rows.length !== 4 || wanted.some(name => !k6.rows.some(row => row.case === name
    && row.samples === 50 && row.error_rate === 0 && Number.isFinite(row.p50_ms) && Number.isFinite(row.p95_ms)))
    || requiredThresholds.some(name => !Object.keys(k6.raw.metrics[name]?.thresholds || {}).length)
    || Object.values(k6.raw.metrics).some(metric => Object.values(metric.thresholds || {}).some(threshold => !threshold.ok))) {
  throw new Error('k6 evidence did not pass all sample, parity, and error thresholds');
}
const report = { sql, http: k6.rows };
fs.writeFileSync(path.join(directory, 'summary.json'), JSON.stringify(report, null, 2) + '\n');
console.log(JSON.stringify(report, null, 2));
