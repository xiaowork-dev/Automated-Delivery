// Real browser smoke test. Requires the running frontend/backend and a dedicated test admin.
// npm --prefix frontend run test:e2e; set BROWSER_CHANNEL=msedge for installed Windows Edge.
const { chromium } = require('../frontend/node_modules/@playwright/test');
const { mkdirSync, writeFileSync } = require('node:fs');
const { randomBytes } = require('node:crypto');
const assert = require('node:assert/strict');

const base = process.env.TEST_WEB_URL || 'http://127.0.0.1:5173';
const out = process.env.TEST_BROWSER_REPORT_DIR || 'artifacts/browser';
const run = randomBytes(5).toString('hex');
const checks = [];
const errors = [];
function pass(name) { checks.push(name); process.stdout.write('PASS ' + name + '\n'); }

async function main() {
  assert(process.env.ADMIN_PASSWORD, 'ADMIN_PASSWORD must be configured for browser acceptance');
  mkdirSync(out, { recursive: true });
  const browser = await chromium.launch({ headless: true, ...(process.env.BROWSER_CHANNEL ? { channel: process.env.BROWSER_CHANNEL } : {}) });
  try {
    const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, timezoneId: 'Asia/Shanghai' });
    const page = await context.newPage();
    page.on('pageerror', e => errors.push(e.message));
    await page.goto(base);
    await page.locator('.product-card').first().waitFor();
    assert(await page.getByText('所有交易均为模拟支付', { exact: false }).count());
    await page.screenshot({ path: out + '/store-desktop.png', fullPage: true, animations: 'disabled' });
    pass('desktop catalogue and explicit mock-payment label');
    await page.goto(base + '/orders');
    await page.waitForURL(/\/login/);
    pass('unauthenticated route redirects to login');
    await page.goto(base + '/register');
    const buyer = 'browser_' + run;
    const password = randomBytes(18).toString('hex');
    await page.locator('#username').fill(buyer);
    await page.locator('#password').fill(password);
    await page.locator('#confirm').fill(password);
    await page.getByRole('button', { name: /注册并登录/ }).click();
    await page.waitForURL(base + '/');
    await page.locator('.product-card').first().click();
    await page.getByRole('button', { name: /立即购买/ }).waitFor();
    await page.getByRole('button', { name: /立即购买/ }).click();
    await page.waitForURL(/\/checkout\//);
    await page.getByRole('button', { name: /模拟支付成功/ }).waitFor();
    assert(!/NaN/.test(await page.locator('.countdown').innerText()));
    pass('register, create order and valid payment countdown');
    await page.getByRole('button', { name: /模拟支付成功/ }).click();
    await page.waitForURL(/\/success$/);
    await page.locator('.revealed-code').waitFor();
    pass('payment automatically delivers a real bound code');
    await page.getByRole('button', { name: /前往自助兑换/ }).click();
    await page.waitForURL(base + '/redeem');
    assert(!page.url().includes('?'), 'Code must not be exposed in URL');
    // The redeem input is the only main form text input; history filters are selects.
    const input = page.locator('#redeem-code');
    assert((await input.inputValue()).length > 4, 'Redeem draft should prefill');
    await page.getByRole('button', { name: /确认兑换|立即兑换|兑换并完成/ }).click();
    await page.getByText('兑换成功', { exact: false }).first().waitFor();
    pass('redeem uses ephemeral prefill and succeeds without code in URL');
    await page.goto(base + '/orders');
    await page.getByText('已完成', { exact: true }).first().waitFor();
    await page.reload();
    await page.getByText('已完成', { exact: true }).first().waitFor();
    pass('order completed and login persists on page reload');
    await page.goto(base + '/admin');
    await page.waitForURL(base + '/');
    pass('buyer blocked from admin routes');
    await context.close();

    const adminContext = await browser.newContext({ viewport: { width: 1440, height: 1000 }, timezoneId: 'Asia/Shanghai' });
    const admin = await adminContext.newPage();
    admin.on('pageerror', e => errors.push(e.message));
    await admin.goto(base + '/login');
    await admin.locator('#username').fill(process.env.ADMIN_USERNAME || 'admin');
    await admin.locator('#password').fill(process.env.ADMIN_PASSWORD);
    await admin.getByRole('button', { name: /^登录/ }).click();
    await admin.waitForURL(base + '/');
    await admin.goto(base + '/admin');
    await admin.getByRole('heading', { name: /运营概览|后台概览|概览/ }).first().waitFor();
    await admin.locator('.metric-card strong').first().waitFor();
    if (await admin.locator('.el-table__row').count()) await admin.locator('.el-table__row .el-tag').first().waitFor();
    await admin.screenshot({ path: out + '/admin-desktop.png', fullPage: true, animations: 'disabled' });
    pass('admin dashboard real data');
    for (const route of ['products', 'codes', 'orders', 'users', 'logs']) {
      await admin.goto(base + '/admin/' + route);
      await admin.locator('.admin-table-panel').waitFor();
      await admin.waitForFunction(() => !document.querySelector('.el-loading-mask'));
      assert(!(await admin.getByText('服务暂时无法连接', { exact: false }).count()));
      pass('admin ' + route + ' renders');
    }
    await admin.goto(base + '/admin/products');
    await admin.getByRole('button', { name: /新增商品/ }).click();
    const dialog = admin.getByRole('dialog');
    await dialog.locator('input').nth(0).fill('浏览器验收-' + run);
    await dialog.locator('.el-input-number input').fill('9.90');
    await dialog.getByRole('button', { name: '保存商品' }).click();
    await admin.getByText('商品已保存', { exact: true }).waitFor();
    await admin.getByRole('cell', { name: '浏览器验收-' + run, exact: true }).waitFor();
    pass('admin creates product through UI');
    await adminContext.close();

    const mobile = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, deviceScaleFactor: 1 });
    const phone = await mobile.newPage();
    phone.on('pageerror', e => errors.push(e.message));
    await phone.goto(base);
    await phone.locator('.product-card').first().waitFor();
    const overflow = await phone.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 1);
    assert(!overflow, 'Mobile page must not overflow horizontally');
    await phone.screenshot({ path: out + '/store-mobile.png', fullPage: true, animations: 'disabled' });
    await phone.locator('.product-card').first().click();
    await phone.getByRole('button', { name: /立即购买/ }).waitFor();
    assert(!(await phone.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 1)));
    pass('mobile catalogue and product usable without horizontal overflow');
    await mobile.close();
    assert.equal(errors.length, 0, 'Browser runtime errors: ' + errors.join('; '));
    pass('no browser JavaScript runtime errors');
    writeFileSync(out + '/report.json', JSON.stringify({ passed: checks.length, checks, run }, null, 2));
  } finally { await browser.close(); }
}
main().catch(e => { process.stderr.write(e.stack + '\n'); process.exitCode = 1; });
