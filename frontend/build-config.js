export function validateApiBaseUrl(value, production) {
  const input = (value || '').trim();
  if (!input && !production) return '';
  let url;
  try { url = new URL(input); } catch { throw new Error('Set VITE_API_BASE_URL to the backend HTTPS origin.'); }
  if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash
      || url.pathname !== '/' || url.hostname === 'localhost' || url.hostname === '127.0.0.1') {
    throw new Error('VITE_API_BASE_URL must be an HTTPS origin without credentials, paths, or query parameters.');
  }
  return url.origin;
}
