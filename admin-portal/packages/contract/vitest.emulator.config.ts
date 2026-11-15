import { defineConfig } from 'vitest/config';

// Run through `pnpm test:emulator` (firebase emulators:exec sets FIRESTORE_EMULATOR_HOST).
export default defineConfig({
  test: {
    include: ['test/emulator/**/*.test.ts'],
    fileParallelism: false,
    testTimeout: 20_000,
    hookTimeout: 20_000,
  },
});
