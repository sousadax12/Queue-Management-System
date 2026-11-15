import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// Run through `pnpm test:emulator` (firebase emulators:exec sets FIREBASE_AUTH_EMULATOR_HOST).
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    setupFiles: ['test/setup.ts'],
    include: ['test/emulator/**/*.test.{ts,tsx}'],
    fileParallelism: false,
    testTimeout: 30_000,
    hookTimeout: 30_000,
  },
});
