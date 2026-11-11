import {afterEach, describe, expect, it, vi} from 'vitest';
import {callApi} from './api.js';
import {validateApiBaseUrl} from '../build-config.js';
afterEach(() => {vi.unstubAllGlobals(); vi.useRealTimers();});
describe('Public API client', () => {
  it('handles rate limits with a retry delay', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({status:429,headers:new Headers({'Retry-After':'20'})}));
    await expect(callApi('validate', {})).rejects.toThrow('retry in 20 seconds');
  });
  it('handles Render cold-start HTML safely', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({status:200,ok:true,json:async()=>{throw new SyntaxError('HTML');}}));
    await expect(callApi('validate', {})).rejects.toThrow('may be waking up');
  });
  it('does not expose upstream 500 responses', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({status:500,ok:false,json:async()=>({detail:'database password'})}));
    await expect(callApi('validate', {})).rejects.toThrow('unavailable or waking up');
  });
  it('explains request size errors', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({status:413}));
    await expect(callApi('validate', {})).rejects.toThrow('128 KiB');
  });
  it('aborts a slow service without retrying requests automatically', async () => {
    vi.useFakeTimers();
    const mock=vi.fn().mockImplementation((url, options)=>new Promise((resolve,reject)=>options.signal.addEventListener('abort',()=>reject(new DOMException('Aborted','AbortError')))));
    vi.stubGlobal('fetch',mock);
    const promise=expect(callApi('validate',{})).rejects.toThrow('taking too long');
    await vi.advanceTimersByTimeAsync(90_000); await promise;
    expect(mock).toHaveBeenCalledTimes(1);
  });
});
describe('Production endpoint configuration', () => {
  it('requires HTTPS and disallows credentials and URL suffixes', () => {
    for (const value of ['', 'http://api.example.com', 'https://u:p@api.example.com', 'https://api.example.com/api', 'https://api.example.com?q=secret', 'https://localhost']) {
      expect(()=>validateApiBaseUrl(value,true)).toThrow();
    }
    expect(validateApiBaseUrl('https://api.example.com/',true)).toBe('https://api.example.com');
    expect(validateApiBaseUrl('',false)).toBe('');
  });
});
