import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import UnoCSS from 'unocss/vite';

export default defineConfig({
  plugins: [UnoCSS(), react()],
  server: { port: 5178, strictPort: true, host: '127.0.0.1' },
  resolve: { alias: { '@': '/src' } },
});
