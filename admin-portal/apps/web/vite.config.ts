import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

export default defineConfig({
  plugins: [react()],
  server: { port: 5173, strictPort: true },
  build: {
    sourcemap: true,
    // One bundle is fine for an internal tool until the CRUD screens add route-level splitting.
    chunkSizeWarningLimit: 1000,
  },
});
