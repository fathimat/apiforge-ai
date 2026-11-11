import React from 'react';
import {render,screen,fireEvent,waitFor,cleanup} from '@testing-library/react';
import {describe,it,expect,vi,afterEach} from 'vitest';
import App from './App.jsx';
afterEach(()=>{cleanup();vi.unstubAllGlobals();});
describe('API workbench',()=>{
 it('defaults to local analysis and submits pasted exchange',async()=>{
 const mock=vi.fn().mockResolvedValue({ok:true,json:async()=>({id:1,source:'local',requestValidation:{message:'Valid JSON'},responseValidation:{message:'Valid JSON'},suggestions:['Check fields'],scenarios:[]})});vi.stubGlobal('fetch',mock);
 render(<App/>);expect(screen.getByRole('checkbox')).not.toBeChecked();fireEvent.click(screen.getByText('Analyze exchange ✦'));
 await screen.findByText('Check fields');expect(JSON.parse(mock.mock.calls[0][1].body).useAi).toBe(false);
 });
 it('shows backend failures and allows retry',async()=>{vi.stubGlobal('fetch',vi.fn().mockRejectedValue(new Error('Backend unavailable')));render(<App/>);fireEvent.click(screen.getByText('Analyze exchange ✦'));expect(await screen.findByRole('alert')).toHaveTextContent('Backend unavailable');await waitFor(()=>expect(screen.getByText('Analyze exchange ✦')).toBeEnabled());});
 it('validates both JSON bodies',async()=>{vi.stubGlobal('fetch',vi.fn().mockResolvedValue({ok:true,json:async()=>({valid:false,message:'Invalid JSON'})}));render(<App/>);fireEvent.click(screen.getByText('✓ Validate JSON'));expect(await screen.findByRole('status')).toHaveTextContent('Request: Invalid JSON · Response: Invalid JSON');});
});
