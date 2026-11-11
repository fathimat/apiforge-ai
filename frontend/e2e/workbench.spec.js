import {test, expect} from '@playwright/test';
test('public analysis is stateless, isolated between visitors, and mobile friendly', async ({page, request, browser}) => {
  await expect.poll(async()=>{try{return (await request.get('/api/health')).status();}catch{return 0;}},{timeout:30000}).toBe(200);
  const localOnly = route => {
    const host = new URL(route.request().url()).hostname;
    return host === '127.0.0.1' || host === 'localhost' ? route.continue() : route.abort();
  };
  await page.context().route('**/*', localOnly);
  await page.goto('/');
  await expect(page.getByText('Gemini disabled · Local analysis only')).toBeVisible();
  await expect(page.getByRole('checkbox')).toBeDisabled();
  await expect(page.getByRole('checkbox')).not.toBeChecked();
  await expect(page.getByRole('button', {name:'Refresh history'})).toHaveCount(0);
  await page.getByRole('button',{name:'Validate JSON'}).click();
  await expect(page.getByRole('status')).toContainText('Request: Valid JSON');
  await page.getByRole('button',{name:'Analyze exchange'}).click();
  await expect(page.getByRole('heading',{name:'Troubleshooting suggestions'})).toBeVisible();
  await expect(page.getByRole('heading',{name:'Boundary conditions'})).toBeVisible();
  const other = await browser.newContext();
  await other.route('**/*', localOnly);
  try {
    const visitor = await other.newPage(); await visitor.goto('/');
    await expect(visitor.getByRole('heading',{name:'Troubleshooting suggestions'})).toHaveCount(0);
    expect((await visitor.request.get('/api/analyses')).status()).toBe(410);
    expect((await visitor.request.get('/api/analyses/1')).status()).toBe(404);
  } finally {await other.close();}
  await page.reload();
  await expect(page.getByRole('heading',{name:'Troubleshooting suggestions'})).toHaveCount(0);
  await page.setViewportSize({width:390,height:844});
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});
