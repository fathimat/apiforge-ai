import {test,expect} from '@playwright/test';
test('validate, analyze, and reload persisted history',async({page,request})=>{
 await expect.poll(async()=>{try{return (await request.get('/api/health')).status();}catch{return 0;}},{timeout:30000}).toBe(200);
 await page.goto('/');
 await expect(page.getByRole('checkbox')).not.toBeChecked();
 await page.getByRole('button',{name:'Validate JSON'}).click();
 await expect(page.getByRole('status')).toContainText('Request: Valid JSON');
 await page.getByRole('button',{name:'Analyze exchange'}).click();
 await expect(page.getByRole('heading',{name:'Troubleshooting suggestions'})).toBeVisible();
 await expect(page.getByRole('heading',{name:'Boundary conditions'})).toBeVisible();
 await page.reload();
 await page.getByRole('button',{name:'Refresh history'}).click();
 await page.getByRole('button',{name:/Analysis #/}).first().click();
 await expect(page.getByRole('heading',{name:'Troubleshooting suggestions'})).toBeVisible();
 await page.setViewportSize({width:390,height:844});
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});
