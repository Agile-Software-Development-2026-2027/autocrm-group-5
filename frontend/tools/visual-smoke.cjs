// Safe local browser smoke: all API requests are mocked before navigation.
const fs = require('node:fs');
const path = require('node:path');
const { spawn } = require('node:child_process');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..');
const local = new URL(process.argv[2] || 'http://localhost:4201');
assert(['localhost', '127.0.0.1'].includes(local.hostname), 'Local frontend only');
const chrome =
  process.env.AUTOCRM_CHROME || 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const artifacts = path.join(root, '.verification');
const shots = path.join(root, 'docs/screenshots');
fs.mkdirSync(artifacts, { recursive: true });
fs.mkdirSync(shots, { recursive: true });
const profile = fs.mkdtempSync(path.join(artifacts, 'chrome-'));
const browser = spawn(
  chrome,
  [
    '--headless=new',
    '--disable-gpu',
    '--remote-debugging-port=0',
    '--user-data-dir=' + profile,
    '--no-first-run',
    '--no-default-browser-check',
    '--disable-background-networking',
    '--disable-component-update',
    '--disable-sync',
    '--disable-extensions',
    '--disable-default-apps',
    '--disable-features=MediaRouter,OptimizationHints,AutofillServerCommunication',
    '--host-resolver-rules=MAP * ~NOTFOUND, EXCLUDE localhost, EXCLUDE 127.0.0.1',
    'about:blank',
  ],
  { windowsHide: true, stdio: 'ignore' },
);
const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const checks = [],
  api = [],
  blocked = [],
  runtimeErrors = [],
  screenshots = [];
let socket,
  id = 0,
  handlerError;
const pending = new Map();
const handlers = new Map();
function check(name, condition) {
  assert(condition, name);
  checks.push(name);
}
function send(method, params = {}) {
  return new Promise((resolve, reject) => {
    const requestId = ++id;
    const timeout = setTimeout(() => {
      pending.delete(requestId);
      reject(Error('CDP timeout: ' + method));
    }, 15000);
    pending.set(requestId, { resolve, reject, timeout });
    socket.send(JSON.stringify({ id: requestId, method, params }));
  });
}
async function evaluate(expression) {
  const result = await send('Runtime.evaluate', {
    expression,
    returnByValue: true,
    awaitPromise: true,
  });
  if (result.exceptionDetails) throw Error(JSON.stringify(result.exceptionDetails));
  return result.result.value;
}
async function waitFor(expression, description) {
  for (let attempt = 0; attempt < 120; attempt++) {
    if (handlerError) throw handlerError;
    if (await evaluate(expression)) return;
    await delay(100);
  }
  throw Error('Timeout: ' + description);
}
async function click(selector) {
  await evaluate('document.querySelector(' + JSON.stringify(selector) + ').click()');
}
async function input(selector, value) {
  await evaluate(
    '(()=>{const e=document.querySelector(' +
      JSON.stringify(selector) +
      ');e.value=' +
      JSON.stringify(value) +
      ";e.dispatchEvent(new Event('input',{bubbles:true}));})()",
  );
}
async function navigate(route) {
  await send('Page.navigate', { url: new URL(route, local).href });
  await waitFor("!!document.querySelector('app-root h1')", route);
}
async function screenshot(name) {
  await evaluate('document.fonts.ready.then(()=>true)');
  const layout = await send('Page.getLayoutMetrics');
  const clip = {
    x: 0,
    y: 0,
    width: layout.cssContentSize.width,
    height: layout.cssContentSize.height,
    scale: 1,
  };
  const result = await send('Page.captureScreenshot', {
    format: 'png',
    captureBeyondViewport: true,
    clip,
  });
  const filename = name + '.png';
  fs.writeFileSync(path.join(shots, filename), Buffer.from(result.data, 'base64'));
  screenshots.push(filename);
}
async function layoutCheck(name) {
  const result = await evaluate(String.raw`(()=>{
    const visible=e=>e.getBoundingClientRect().width>0&&e.getBoundingClientRect().height>0;
    const nodes=[...document.querySelectorAll('button,nav a,label.file-button,input[type=text]')].filter(visible);
    return {
      viewport:innerWidth, width:document.documentElement.scrollWidth,
      small:nodes.filter(e=>e.getBoundingClientRect().height<43.9).map(e=>e.outerHTML.slice(0,100)),
      heading:document.querySelectorAll('h1').length,
      current:document.querySelectorAll('nav a[aria-current=page]').length,
      labels:[...document.querySelectorAll('input,textarea')].filter(e=>e.type!=='hidden').every(e=>e.labels.length>0),
      busyLive:[...document.querySelectorAll('[role=alert],[role=status]')].some(e=>e.closest('[aria-busy=true]')),
    };
  })()`);
  check(name + ': no horizontal overflow', result.width <= result.viewport + 1);
  check(name + ': controls at least 44px', result.small.length === 0);
  check(name + ': one h1 and current navigation', result.heading === 1 && result.current === 1);
  check(name + ': labelled fields', result.labels);
  check(name + ': live regions outside busy containers', !result.busyLive);
  return result;
}
const wav = path.join(artifacts, 'recording-for-visual-check-' + 'long-name-'.repeat(10) + '.wav');
const pdf = path.join(artifacts, 'not-an-audio.pdf');
const audio = Buffer.alloc(60);
audio.write('RIFF');
audio.writeUInt32LE(52, 4);
audio.write('WAVEfmt ', 8);
audio.writeUInt32LE(16, 16);
audio.writeUInt16LE(1, 20);
audio.writeUInt16LE(1, 22);
audio.writeUInt32LE(8000, 24);
audio.writeUInt32LE(16000, 28);
audio.writeUInt16LE(2, 32);
audio.writeUInt16LE(16, 34);
audio.write('data', 36);
audio.writeUInt32LE(16, 40);
fs.writeFileSync(wav, audio);
fs.writeFileSync(pdf, '%PDF-1.4 test fixture');
let job = 0,
  approvalMode = 'error';
const polls = new Map();
const noteId = 'mock-note-' + 'reference'.repeat(20);
const transcript =
  'TEST VISUALE CON HTTP MOCK. Il cliente richiede un preventivo e una nuova chiamata. Nessun dato reale.';
async function mockRequest(event) {
  const url = new URL(event.request.url);
  const isApi = url.pathname.startsWith('/api/');
  if (isApi) {
    const record = { method: event.request.method, path: url.pathname };
    if (
      event.request.postData &&
      event.request.method === 'POST' &&
      url.pathname === '/api/hubspot/sync'
    ) {
      record.body = JSON.parse(event.request.postData);
    }
    api.push(record);
    let status = 200,
      body = {};
    if (record.method === 'OPTIONS') status = 204;
    else if (record.method === 'POST' && record.path === '/api/transcriptions/upload') {
      job++;
      polls.set('mock-job-' + job, 0);
      status = 202;
      body = { jobId: 'mock-job-' + job, status: 'processing' };
    } else if (
      record.method === 'GET' &&
      /^\/api\/transcriptions\/mock-job-\d+$/.test(record.path)
    ) {
      const jobId = record.path.split('/').pop();
      const count = polls.get(jobId) + 1;
      polls.set(jobId, count);
      body = {
        jobId,
        status: count === 1 ? 'processing' : 'done',
        transcript: count === 1 ? null : transcript,
      };
    } else if (record.method === 'POST' && record.path === '/api/hubspot/sync') {
      status = approvalMode === 'error' ? 400 : 200;
      body =
        status === 400
          ? { error: 'DEAL_NOT_FOUND', message: 'Mock: Deal non trovato.' }
          : { status: 'success', dealId: 'mock-deal', noteId };
    } else {
      status = 404;
      body = { error: 'Unexpected mocked API request' };
    }
    await delay(180);
    await send('Fetch.fulfillRequest', {
      requestId: event.requestId,
      responseCode: status,
      responseHeaders: [
        { name: 'Content-Type', value: 'application/json' },
        { name: 'Access-Control-Allow-Origin', value: local.origin },
        { name: 'Access-Control-Allow-Methods', value: 'GET, POST, OPTIONS' },
        { name: 'Access-Control-Allow-Headers', value: 'content-type' },
      ],
      body: Buffer.from(status === 204 ? '' : JSON.stringify(body)).toString('base64'),
    });
  } else if (url.origin === local.origin || ['data:', 'blob:'].includes(url.protocol)) {
    await send('Fetch.continueRequest', { requestId: event.requestId });
  } else {
    blocked.push(url.origin + url.pathname);
    await send('Fetch.failRequest', { requestId: event.requestId, errorReason: 'BlockedByClient' });
  }
}
async function selectAudio() {
  const doc = await send('DOM.getDocument');
  const node = await send('DOM.querySelector', {
    nodeId: doc.root.nodeId,
    selector: '#audio-file',
  });
  await send('DOM.setFileInputFiles', { nodeId: node.nodeId, files: [wav] });
  await waitFor(
    "document.querySelector('.file-status')?.textContent.includes('long-name')",
    'selected audio',
  );
}
async function uploadFlow() {
  await selectAudio();
  await click('.upload-actions button');
  await waitFor("!!document.querySelector('.upload-status')", 'upload status');
  check(
    'upload live status remains outside busy regions',
    await evaluate(
      "[...document.querySelectorAll('[role=status],[role=alert]')].every(e=>!e.closest('[aria-busy=true]'))",
    ),
  );
  check(
    'upload disables controls while pending',
    await evaluate(
      "document.querySelector('#audio-file').disabled && document.querySelector('.upload-actions button').disabled",
    ),
  );
  await waitFor(
    "!!document.querySelector('#transcript-text') && location.pathname==='/transcript-review'",
    'transcript review after polling',
  );
}
function contrast(a, b) {
  const lum = (hex) => {
    const values = hex
      .match(/[0-9a-f]{2}/gi)
      .map((v) => parseInt(v, 16) / 255)
      .map((v) => (v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4));
    return 0.2126 * values[0] + 0.7152 * values[1] + 0.0722 * values[2];
  };
  const x = lum(a),
    y = lum(b);
  return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
}
(async () => {
  try {
    const portFile = path.join(profile, 'DevToolsActivePort');
    for (let n = 0; n < 100 && !fs.existsSync(portFile); n++) await delay(100);
    const port = fs.readFileSync(portFile, 'utf8').split('\n')[0];
    const targets = await (await fetch('http://127.0.0.1:' + port + '/json/list')).json();
    socket = new WebSocket(targets.find((t) => t.type === 'page').webSocketDebuggerUrl);
    await new Promise((resolve, reject) => {
      socket.onopen = resolve;
      socket.onerror = reject;
    });
    socket.onmessage = ({ data }) => {
      const event = JSON.parse(data);
      if (event.id) {
        const entry = pending.get(event.id);
        if (!entry) return;
        pending.delete(event.id);
        clearTimeout(entry.timeout);
        if (event.error) entry.reject(Error(JSON.stringify(event.error)));
        else entry.resolve(event.result);
      } else if (handlers.has(event.method)) {
        Promise.resolve(handlers.get(event.method)(event.params)).catch((error) => {
          handlerError = error;
        });
      }
    };
    handlers.set('Fetch.requestPaused', mockRequest);
    handlers.set('Runtime.exceptionThrown', (event) =>
      runtimeErrors.push(event.exceptionDetails.text),
    );
    await send('Page.enable');
    await send('Runtime.enable');
    await send('DOM.enable');
    await send('Fetch.enable', { patterns: [{ urlPattern: '*' }] });
    await send('Emulation.setEmulatedMedia', {
      features: [{ name: 'prefers-reduced-motion', value: 'reduce' }],
    });
    const sizes = [
      [1440, 1000, 'desktop'],
      [768, 1024, 'tablet'],
      [375, 812, 'mobile'],
      [320, 812, 'compact'],
    ];
    for (const [width, height, name] of sizes) {
      await send('Emulation.setDeviceMetricsOverride', {
        width,
        height,
        deviceScaleFactor: 1,
        mobile: false,
      });
      await navigate('/');
      await waitFor("location.pathname==='/audio-upload'", 'root redirect');
      check(
        name + ': heading receives route focus',
        await evaluate("document.activeElement===document.querySelector('h1')"),
      );
      await layoutCheck(name + ' upload');
      if (name === 'desktop') {
        await send('Input.dispatchKeyEvent', {
          type: 'keyDown',
          key: 'Tab',
          code: 'Tab',
          windowsVirtualKeyCode: 9,
        });
        await send('Input.dispatchKeyEvent', {
          type: 'keyUp',
          key: 'Tab',
          code: 'Tab',
          windowsVirtualKeyCode: 9,
        });
        check(
          'keyboard reaches audio selector with visible focus',
          await evaluate(
            "document.activeElement.id==='audio-file' && getComputedStyle(document.querySelector('label.file-button')).outlineStyle!=='none'",
          ),
        );
        await navigate('/not-a-page');
        await waitFor("location.pathname==='/audio-upload'", 'unknown route redirect');
        check('unknown route redirects to upload', true);
        const doc = await send('DOM.getDocument');
        const node = await send('DOM.querySelector', {
          nodeId: doc.root.nodeId,
          selector: '#audio-file',
        });
        await send('DOM.setFileInputFiles', { nodeId: node.nodeId, files: [pdf] });
        await waitFor("!!document.querySelector('#audio-file-error')", 'invalid audio');
        check(
          'non audio rejected and announced',
          await evaluate(
            "document.querySelector('#audio-file').getAttribute('aria-invalid')==='true' && document.querySelector('#audio-file-error').getAttribute('role')==='alert'",
          ),
        );
      }
      await selectAudio();
      await layoutCheck(name + ' long filename');
      await screenshot(name + '-upload');
      await uploadFlow();
      check(
        name + ': review displays mock transcript',
        await evaluate(
          "document.querySelector('#transcript-text').value===" + JSON.stringify(transcript),
        ),
      );
      await layoutCheck(name + ' review');
      await screenshot(name + '-review');
      if (name === 'desktop') {
        const corrected = transcript + ' Correzione manuale conservata.';
        await input('#transcript-text', corrected);
        await click('button[type=submit]');
        await waitFor("!!document.querySelector('#deal-code-error')", 'required Deal Code');
        check(
          'validation shown and no approval HTTP',
          api.filter((r) => r.path === '/api/hubspot/sync').length === 0,
        );
        await input('#review-deal-code', 'TEST-VISUAL');
        await click('button[type=submit]');
        await waitFor("!!document.querySelector('.review-actions .error')", 'approval error');
        check(
          'approval error preserves edited text',
          await evaluate(
            "document.querySelector('#transcript-text').value===" + JSON.stringify(corrected),
          ),
        );
        await screenshot('desktop-approval-error');
        approvalMode = 'success';
        await click('button[type=submit]');
        await waitFor("!!document.querySelector('.success')", 'mock approval success');
        const posts = api.filter((r) => r.path === '/api/hubspot/sync' && r.method === 'POST');
        check(
          'approve sends exact corrected text and Deal Code',
          posts.length === 2 &&
            posts.every((r) => r.body.text === corrected && r.body.dealCode === 'TEST-VISUAL'),
        );
        check(
          'success locks editor and approval',
          await evaluate(
            "document.querySelector('#transcript-text').readOnly && document.querySelector('button[type=submit]').disabled",
          ),
        );
        await layoutCheck('desktop success with long note id');
        await screenshot('desktop-approval-success');
        await click('nav a[href="/audio-upload"]');
        await waitFor("!!document.querySelector('#audio-file')", 'navigate upload');
        await click('nav a[href="/transcript-review"]');
        await waitFor("!!document.querySelector('.success')", 'return to review');
        check(
          'navigation preserves draft and blocks duplicate',
          await evaluate(
            "document.querySelector('#transcript-text').value===" +
              JSON.stringify(corrected) +
              " && document.querySelector('button[type=submit]').disabled",
          ),
        );
        const before = api.length;
        await click('.discard');
        await waitFor("!!document.querySelector('.discarded-state')", 'discarded');
        check('discard makes no HTTP requests', api.length === before);
        await screenshot('desktop-discarded');
      } else if (name === 'mobile') {
        approvalMode = 'success';
        await input('#review-deal-code', 'TEST-MOBILE');
        await click('button[type=submit]');
        await waitFor("!!document.querySelector('.success')", 'mobile approval success');
        await layoutCheck('mobile success with long note id');
        await screenshot('mobile-approval-success');
      }
    }
    await navigate('/transcript-review');
    await waitFor("!!document.querySelector('.empty-state')", 'review empty state after reload');
    check(
      'review has empty state without demo data',
      !(await evaluate("!!document.querySelector('#transcript-text')")),
    );
    await screenshot('compact-review-empty');
    await navigate('/transcript-review/demo');
    await waitFor("!!document.querySelector('#transcript-text')", 'demo editor');
    check(
      'demo clearly labelled and no approval actions',
      await evaluate(
        "!!document.querySelector('.demo-panel') && !document.querySelector('.review-actions')",
      ),
    );
    await input('#transcript-text', 'Bozza demo conservata');
    await click('.demo-actions button');
    check(
      'source update does not erase user edits',
      await evaluate("document.querySelector('#transcript-text').value==='Bozza demo conservata'"),
    );
    await click('.demo-actions button:nth-child(2)');
    await waitFor("!!document.querySelector('.load-error')", 'demo load error');
    check(
      'load error retains draft',
      await evaluate("document.querySelector('#transcript-text').value==='Bozza demo conservata'"),
    );
    await layoutCheck('compact demo with load error');
    await screenshot('compact-demo-error');
    for (const [foreground, background] of [
      ['#192d2a', '#ffffff'],
      ['#53665f', '#f4f6f2'],
      ['#53665f', '#ffffff'],
      ['#ffffff', '#176354'],
      ['#9e3037', '#fff2f1'],
    ]) {
      check(
        'text contrast at least 4.5:1 ' + foreground + '/' + background,
        contrast(foreground, background) >= 4.5,
      );
    }
    check('control border contrast at least 3:1', contrast('#778c82', '#ffffff') >= 3);
    check(
      'reduced motion preference applied',
      await evaluate("matchMedia('(prefers-reduced-motion: reduce)').matches"),
    );
    check('no uncaught browser errors', runtimeErrors.length === 0);
    check('no external resource requests', blocked.length === 0);
    const report = {
      passed: true,
      checkedAt: new Date().toISOString(),
      origin: local.origin,
      viewports: sizes,
      checks,
      screenshots,
      apiRequestsMocked: api,
      realApiRequests: 0,
      externalRequestsBlocked: blocked,
      runtimeErrors,
      scope:
        'CDP mock browser verification; no real backend or HubSpot requests. Targeted accessibility checks, not a full WCAG audit.',
    };
    fs.writeFileSync(
      path.join(artifacts, 'visual-smoke.json'),
      JSON.stringify(report, null, 2) + '\n',
    );
    console.log(
      JSON.stringify({
        passed: true,
        checks: checks.length,
        screenshots: screenshots.length,
        mockedRequests: api.length,
        realApiRequests: 0,
      }),
    );
  } catch (error) {
    fs.writeFileSync(
      path.join(artifacts, 'visual-smoke-failure.json'),
      JSON.stringify({ error: error.stack, checks, api, runtimeErrors, blocked }, null, 2),
    );
    console.error(error.stack);
    process.exitCode = 1;
  } finally {
    if (socket?.readyState === 1) {
      await send('Browser.close').catch(() => {});
      socket.close();
    }
    browser.kill();
  }
})();
