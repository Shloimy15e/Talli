const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
(async () => {
  const browser = await chromium.launch({ headless: true, channel: 'chrome' });
  const context = await browser.newContext({ viewport: { width: 440, height: 650 } });
  const projects = [{id:1,name:'Website refresh',clientName:'Acme Studio',lastDescription:'Homepage layout'}, {id:2,name:'Monthly support',clientName:'North & Co',lastDescription:null}, {id:3,name:'Brand guidelines',clientName:'Acme Studio',lastDescription:null}];
  let timer = null, failStart = false, failedProjects = false, startCount = 0, stopCount = 0, expenseCount = 0, projectCount = 0;
  let lastExpense;
  const root = path.resolve(__dirname, '..');
  const artifacts = path.join(__dirname, 'artifacts'); fs.mkdirSync(artifacts, {recursive:true});
  await context.addInitScript(() => {
    window.saved = {serverUrl:'https://talli.mock',apiToken:'mock-token'};
    window.requestedOrigins = [];
    window.chrome = {
      storage:{local:{get:async keys => Object.fromEntries(keys.map(k=>[k,window.saved[k]])),set:async values=>Object.assign(window.saved,values),remove:async keys=>keys.forEach(k=>delete window.saved[k])}},
      runtime:{sendMessage:async()=>{}},
      permissions:{request:async value=>{window.requestedOrigins.push(value.origins);return true;},remove:async()=>true}
    };
  });
  await context.route('https://extension.mock/**', route => {
    const relative = new URL(route.request().url()).pathname.slice(1);
    const file = path.join(root, relative);
    const ext = path.extname(file);
    const types = {'.html':'text/html','.js':'application/javascript','.css':'text/css','.svg':'image/svg+xml','.woff2':'font/woff2'};
    return route.fulfill({body:fs.readFileSync(file),contentType:types[ext]});
  });
  await context.route('https://talli.mock/**', async route => {
    const url = new URL(route.request().url()).pathname, method = route.request().method();
    let status=200, body;
    if (url.endsWith('/projects') && method === 'GET') {
      if(failedProjects) { failedProjects=false; status=503;body={error:'Temporarily unavailable'}; } else body=projects;
    } else if (url.endsWith('/current')) {body=timer; if(!timer)status=204;}
    else if(url.endsWith('/start')) {
      startCount++; await new Promise(r=>setTimeout(r,120));
      if(failStart) {status=500;body={error:'Timer could not start'};} else {
        const request=route.request().postDataJSON(), project=projects.find(p=>p.id===request.projectId);
        timer={id:42,projectId:project.id,projectName:project.name,description:request.description,elapsedSeconds:0};body=timer;
      }
    } else if(url.endsWith('/stop')) {stopCount++;await new Promise(r=>setTimeout(r,120));body={id:42};timer=null;}
    else if(url.endsWith('/description')) {body={id:42};}
    else if(url.endsWith('/expenses')) {expenseCount++;lastExpense=route.request().postDataJSON();await new Promise(r=>setTimeout(r,120));body={id:5};}
    else if(url.endsWith('/clients')) body=[{id:1,name:'Acme Studio'}];
    else if(url.endsWith('/projects') && method === 'POST') {projectCount++;await new Promise(r=>setTimeout(r,120));body={id:4,...route.request().postDataJSON(),clientName:'Acme Studio'};}
    else throw new Error('Unexpected mock route '+url);
    return route.fulfill({status, contentType:'application/json',body:status===204?'':JSON.stringify(body)});
  });
  const page = await context.newPage();
  const errors=[]; page.on('pageerror', error=>errors.push(error.message));
  await page.goto('https://extension.mock/popup.html');
  await page.locator('#app').waitFor({state:'visible'});
  await page.screenshot({path:path.join(artifacts,'quick-capture.png')});
  await page.locator('#recentSearch').fill('North'); assert.equal(await page.locator('.recent-entry').count(),1);
  await page.locator('#recentSearch').fill('');
  await page.getByRole('button',{name:'Select Website refresh',exact:true}).click();
  await page.locator('#timerDesc').fill('Homepage layout');
  failStart=true;
  await page.locator('#startBtn').click();
  await page.locator('#actionFeedback.error').waitFor(); assert.match(await page.locator('#actionFeedback').textContent(),/could not start/);
  failStart=false;
  await page.evaluate(()=>{document.getElementById('startBtn').click();document.getElementById('startBtn').click();});
  await page.locator('#timerRunning').waitFor({state:'visible'});
  assert.equal(startCount,2);
  assert.equal(await page.locator('.recent-entry-play:not(.running):disabled').count(),2);
  assert.equal(await page.locator('#runningProject').textContent(),'Website refresh');
  assert.equal(await page.locator('#runningClient').textContent(),'Acme Studio');
  await page.screenshot({path:path.join(artifacts,'running-timer.png')});
  await page.evaluate(()=>{projects.find(project=>project.id===1).clientName=null;showTimerRunning();});
  assert.equal(await page.locator('#runningClient').textContent(),'No client assigned');
  await page.screenshot({path:path.join(artifacts,'running-no-client.png')});
  await page.evaluate(()=>{projects.find(project=>project.id===1).clientName='Acme Studio';showTimerRunning();});
  await page.evaluate(()=>{document.getElementById('stopBtn').click();document.getElementById('stopBtn').click();});
  await page.locator('#timerStopped').waitFor({state:'visible'}); assert.equal(stopCount,1);
  // Repeated connection retry must retain one handler per control.
  failedProjects=true; await page.locator('#refreshBtn').click(); await page.locator('#errorState').waitFor({state:'visible'});
  await page.locator('#retryBtn').click(); await page.locator('#app').waitFor({state:'visible'});
  await page.getByRole('button',{name:'Expense',exact:true}).click();
  await page.locator('#expAmount').fill('42.50'); await page.getByRole('combobox', {name:'Currency',exact:true}).click();
  await page.screenshot({path:path.join(artifacts,'currency-picker.png')});
  await page.getByRole('combobox', {name:'Currency',exact:true}).press('ArrowDown');
  await page.getByRole('combobox', {name:'Currency',exact:true}).press('Enter');
  assert.equal(await page.locator('#expCurrency').inputValue(),'ILS'); await page.locator('#expBillable').check();
  await page.getByRole('combobox',{name:'Category',exact:true}).click();
  await page.screenshot({path:path.join(artifacts,'category-picker.png')});
  await page.getByRole('combobox',{name:'Category',exact:true}).press('Escape');
  await page.screenshot({path:path.join(artifacts,'expense.png')});
  await page.evaluate(()=>{const form=document.getElementById('expenseForm');form.requestSubmit();form.requestSubmit();});
  await page.locator('#expenseFeedback.success').waitFor();assert.equal(expenseCount,1);assert.equal(lastExpense.currency,'ILS');assert.equal(lastExpense.billable,true);assert.match(lastExpense.incurredOn,/^\d{4}-\d{2}-\d{2}$/);
  await page.getByRole('button',{name:'Timer',exact:true}).click();await page.locator('#projectPickerBtn').click(); await page.locator('#openCreateProject').click();
  await page.locator('#projectCreateView').waitFor({state:'visible'}); await page.locator('#newProjectName').fill('Landing page');await page.getByRole('button',{name:'Client',exact:true}).click();
  await page.getByRole('combobox',{name:'Search clients',exact:true}).fill('Acme');
  await page.getByRole('option',{name:'Acme Studio',exact:true}).click();await page.locator('#newProjectRate').fill('125');
  await page.screenshot({path:path.join(artifacts,'new-project.png')});
  await page.evaluate(()=>{document.getElementById('submitNewProject').click();document.getElementById('submitNewProject').click();});
  await page.locator('#projectDropdown').waitFor({state:'hidden'});assert.equal(projectCount,1);
  await page.locator('#settingsBtn').click(); await page.locator('#serverUrl').waitFor();await page.screenshot({path:path.join(artifacts,'connection.png')});
  await page.locator('#testBtn').click();await page.waitForFunction(()=>document.getElementById('connectionStatus').textContent.includes('Connected'));
  assert.deepEqual(await page.evaluate(()=>window.requestedOrigins),[['https://talli.mock/*']]);
  // In-flight settings response cannot save credentials after input changes.
  await page.route('https://talli.mock/api/v1/projects',async route=>{await new Promise(r=>setTimeout(r,200));await route.fulfill({json:projects});});
  await page.locator('#saveBtn').click(); await page.locator('#apiToken').fill('changed-token');
  await page.waitForFunction(()=>document.getElementById('feedback').textContent.includes('Details changed'));
  assert.equal(await page.evaluate(()=>window.saved.apiToken),'mock-token');
  await page.locator('#disconnectBtn').click();await page.waitForFunction(()=>!window.saved.apiToken);assert.equal(await page.locator('#apiToken').inputValue(),'');
  assert.deepEqual(errors,[]);
  console.log('PASS: search, timer error recovery/duplicate prevention, stop, refresh/retry listener safety, expense contract/duplicates, project creation/duplicates, origin permissions, stale settings response, disconnect.');
  console.log('Screenshots: '+artifacts);
  await browser.close();
})().catch(error=>{console.error(error);process.exit(1);});
