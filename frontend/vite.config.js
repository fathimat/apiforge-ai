import { defineConfig } from 'vite';
export default defineConfig({server:{proxy:{'/api':'http://127.0.0.1:8080'}},test:{include:['src/**/*.test.{js,jsx}'],environment:'jsdom',setupFiles:['./src/test-setup.js']}});
