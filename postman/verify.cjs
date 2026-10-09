// Run the collection twice and retain only evidence without credentials or cookies.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

const root = path.resolve(__dirname, '..');
const modulePath = path.join(root, 'target/verification-tools/node_modules/newman');
const newman = require(modulePath);
const baseUrl = process.env.BASE_URL || 'http://127.0.0.1:8081';
const url = new URL(baseUrl);
if (!['127.0.0.1', 'localhost', '[::1]'].includes(url.hostname)) {
  throw new Error('Verification is restricted to a local test application');
}
const output = path.resolve(process.argv[2] || path.join(root, 'target/postman', new Date().toISOString().replace(/:/g, '-')));
fs.mkdirSync(output, { recursive: true });
const collectionFile = path.join(__dirname, 'MiniLedger.postman_collection.json');
const environmentFile = path.join(__dirname, 'MiniLedger.local.postman_environment.json');
const hash = file => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');

function run() {
  return new Promise((resolve, reject) => newman.run({
    collection: JSON.parse(fs.readFileSync(collectionFile, 'utf8')),
    environment: JSON.parse(fs.readFileSync(environmentFile, 'utf8')),
    envVar: [{ key: 'baseUrl', value: baseUrl }],
    bail: true,
    timeoutRequest: 30000,
    reporters: [],
  }, (error, summary) => error ? reject(error) : resolve(summary)));
}

(async () => {
  for (let iteration = 1; iteration <= 2; iteration++) {
    const summary = await run();
    // Newman also records pm.sendRequest CSRF calls against the same item.
    const items = [...new Map(summary.run.executions.map(execution => [execution.item.id, execution])).values()];
    const executions = items.map(execution => ({
      request: execution.item.name,
      status: execution.response?.code ?? null,
      assertions: (execution.assertions || []).map(assertion => ({
        name: assertion.assertion,
        passed: !assertion.error && !assertion.skipped,
      })),
    }));
    const passed = summary.run.failures.length === 0
      && summary.run.stats.items.total === 31 && executions.length === 31
      && executions.every(execution => execution.assertions.length > 0
        && execution.assertions.every(assertion => assertion.passed));
    const report = {
      recordedAt: new Date().toISOString(), iteration, baseUrl, passed,
      node: process.version,
      newman: require(path.join(modulePath, 'package.json')).version,
      collectionSha256: hash(collectionFile), environmentSha256: hash(environmentFile),
      stats: summary.run.stats,
      failureCount: summary.run.failures.length,
      executions,
    };
    fs.writeFileSync(path.join(output, `run-${iteration}.json`), JSON.stringify(report, null, 2) + '\n');
    console.log(`Run ${iteration}: ${passed ? 'PASS' : 'FAIL'}, ${executions.length}/31 requests, ${summary.run.stats.assertions.total} assertions, ${summary.run.failures.length} failures`);
    if (!passed) {
      console.error(JSON.stringify(executions.filter(execution => execution.assertions.some(assertion => !assertion.passed)), null, 2));
      process.exitCode = 1;
      return;
    }
  }
  console.log(`Evidence: ${output}`);
})().catch(error => {
  console.error(error.message);
  process.exitCode = 1;
});
