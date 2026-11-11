const baseUrl = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '');

export async function callApi(path, body) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 90_000);
  try {
    const response = await fetch(`${baseUrl}/api/${path}`, {
      method: body === undefined ? 'GET' : 'POST',
      headers: body === undefined ? {} : {'Content-Type': 'application/json'},
      body: body === undefined ? undefined : JSON.stringify(body),
      credentials: 'omit',
      cache: 'no-store',
      signal: controller.signal,
    });
    if (response.status === 429) {
      const retry = Math.min(300, Math.max(1, Number(response.headers.get('Retry-After')) || 60));
      throw new Error(`Demo request limit reached. Please retry in ${retry} seconds.`);
    }
    if (response.status === 413) throw new Error('Request is too large. Reduce the pasted content (128 KiB total maximum).');
    if (response.status >= 500) throw new Error('The demo is unavailable or waking up. Please wait a minute and retry.');
    let data;
    try { data = await response.json(); }
    catch { throw new Error('The demo returned an unexpected response. It may be waking up; please retry shortly.'); }
    if (!response.ok) throw new Error(data.detail || 'The request could not be processed.');
    return data;
  } catch (error) {
    if (error.name === 'AbortError') throw new Error('The demo is taking too long to wake up. Please wait a minute and retry.');
    if (error instanceof TypeError) throw new Error('Cannot reach the demo service. Please wait a minute and retry.');
    throw error;
  } finally {
    clearTimeout(timeout);
  }
}
