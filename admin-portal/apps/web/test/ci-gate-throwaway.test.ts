import { expect, it } from 'vitest';

it('fails on purpose to check that CI gate turns red', () => {
  expect(1).toBe(2);
});
