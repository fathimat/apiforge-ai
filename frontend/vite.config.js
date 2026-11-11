import {defineConfig, loadEnv} from 'vite';
import {validateApiBaseUrl} from './build-config.js';

export default defineConfig(({command, mode}) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_');
  const baseUrl = validateApiBaseUrl(env.VITE_API_BASE_URL, command === 'build');
  return {
    define: {'import.meta.env.VITE_API_BASE_URL': JSON.stringify(baseUrl)},
    server: {proxy: {'/api': 'http://127.0.0.1:8080'}},
    test: {include: ['src/**/*.test.{js,jsx}'], environment: 'jsdom', setupFiles: ['./src/test-setup.js']},
  };
});
