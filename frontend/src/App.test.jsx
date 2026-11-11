import React from 'react';
import {render, screen, fireEvent, waitFor, cleanup} from '@testing-library/react';
import {describe, it, expect, vi, afterEach} from 'vitest';
import App from './App.jsx';
afterEach(() => {cleanup(); vi.unstubAllGlobals();});
describe('Public API workbench', () => {
  it('submits local-only analysis and exposes no shared history and keeps future consent disabled', async () => {
    const mock = vi.fn().mockResolvedValue({ok:true, status:200, json:async()=>({id:null, source:'local', requestValidation:{message:'Valid JSON'}, responseValidation:{message:'Valid JSON'}, suggestions:['Check fields'], scenarios:[]})});
    vi.stubGlobal('fetch', mock);
    render(<App/>);
    expect(screen.getByRole('checkbox')).toBeDisabled();
    expect(screen.getByRole('checkbox')).not.toBeChecked();
    expect(screen.queryByText('Refresh history')).not.toBeInTheDocument();
    fireEvent.click(screen.getByText('Analyze exchange ✦'));
    await screen.findByText('Check fields');
    expect(JSON.parse(mock.mock.calls[0][1].body).useAi).toBe(false);
    expect(JSON.parse(mock.mock.calls[0][1].body).aiConsent).toBe(false);
    expect(mock.mock.calls[0][1].credentials).toBe('omit');
    fireEvent.click(screen.getByText(/Clear exchange/));
    expect(screen.queryByText('Check fields')).not.toBeInTheDocument();
    expect(screen.getByLabelText('Request body')).toHaveValue('');
  });
  it('shows a safe network error and allows retry', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('sensitive internal detail')));
    render(<App/>); fireEvent.click(screen.getByText('Analyze exchange ✦'));
    expect(await screen.findByRole('alert')).toHaveTextContent('Cannot reach the demo service');
    expect(screen.getByRole('alert')).not.toHaveTextContent('sensitive internal detail');
    await waitFor(()=>expect(screen.getByText('Analyze exchange ✦')).toBeEnabled());
  });
  it('validates both JSON bodies', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ok:true,status:200,json:async()=>({valid:false,message:'Invalid JSON'})}));
    render(<App/>);fireEvent.click(screen.getByText('✓ Validate JSON'));
    expect(await screen.findByRole('status')).toHaveTextContent('Request: Invalid JSON · Response: Invalid JSON');
  });
  it('requires explicit per-exchange consent in a mocked future UI and resets it on edits', async () => {
    const mock=vi.fn().mockResolvedValue({ok:true,status:200,json:async()=>({id:null,source:'gemini',requestValidation:{message:'Valid JSON'},responseValidation:{message:'Valid JSON'},suggestions:['Mock AI only'],scenarios:[]})});
    vi.stubGlobal('fetch',mock);
    render(<App aiAvailable/>);
    const consent=screen.getByRole('checkbox');
    expect(consent).not.toBeChecked();
    fireEvent.click(consent);
    fireEvent.click(screen.getByText('Analyze exchange ✦'));
    await screen.findByText('Mock AI only');
    expect(JSON.parse(mock.mock.calls[0][1].body)).toMatchObject({useAi:true,aiConsent:true});
    expect(mock.mock.calls[0][0]).toBe('/api/analyses');
    fireEvent.change(screen.getByLabelText('Request body'),{target:{value:'{}'}});
    expect(consent).not.toBeChecked();
    fireEvent.click(screen.getByText('Analyze exchange ✦'));
    await waitFor(()=>expect(mock).toHaveBeenCalledTimes(2));
    expect(JSON.parse(mock.mock.calls[1][1].body)).toMatchObject({useAi:false,aiConsent:false});
  });
  it('never renders provider text as executable HTML', async () => {
    vi.stubGlobal('fetch',vi.fn().mockResolvedValue({ok:true,status:200,json:async()=>({id:null,source:'gemini',requestValidation:{message:'Valid JSON'},responseValidation:{message:'Valid JSON'},suggestions:['<img src=x onerror=alert(1)>'],scenarios:[]})}));
    const {container}=render(<App/>);
    fireEvent.click(screen.getByText('Analyze exchange ✦'));
    await screen.findByText('<img src=x onerror=alert(1)>');
    expect(container.querySelector('img')).toBeNull();
  });
});
