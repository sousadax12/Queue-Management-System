/**
 * Node loader for the golden fixtures (fixtures/README.md). The TV's Kotlin loader mirrors it.
 */
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { COLLECTION_NAMES, type CollectionName, parseDocPath } from './collections.js';
import { parseEncodedJson } from './encoding.js';
import { type TemplateSpec, templatesFileSchema } from './schemas/templates.js';

export const FIXTURES_DIR = fileURLToPath(new URL('../fixtures/', import.meta.url));
export const TEMPLATES_JSON_PATH = fileURLToPath(
  new URL('../../../../android-tv/contract/templates.json', import.meta.url),
);

/** Flutter-era `queues` docs: valid for readers, not yet backfilled. Never seeded. */
export const LEGACY_QUEUES_FILE = 'queues.legacy.json';

export const fixtureFileOf = (c: CollectionName): string => `${c}.json`;

export type FixtureDocs = Record<string, unknown>;

/** Read one fixture file: `{ "<doc path>": { ...doc } }`. */
export function loadFixtureFile(file: string): FixtureDocs {
  const text = readFileSync(join(FIXTURES_DIR, file), 'utf8');
  const parsed = parseEncodedJson(text, file);
  if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error(`${file}: expected an object keyed by document path`);
  }
  return parsed as FixtureDocs;
}

/** Fixtures of one collection; checks every key is a path in that collection. */
export function loadCollectionFixtures(collection: CollectionName): FixtureDocs {
  const docs = loadFixtureFile(fixtureFileOf(collection));
  for (const path of Object.keys(docs)) {
    const p = parseDocPath(path);
    if (p.collection !== collection) {
      throw new Error(`${fixtureFileOf(collection)}: ${path} belongs to ${p.collection}`);
    }
  }
  return docs;
}

/** All contract fixtures (every collection), keyed by document path. */
export function loadContractFixtures(): FixtureDocs {
  return Object.assign({}, ...COLLECTION_NAMES.map(loadCollectionFixtures)) as FixtureDocs;
}

export function loadLegacyQueueFixtures(): FixtureDocs {
  const docs = loadFixtureFile(LEGACY_QUEUES_FILE);
  for (const path of Object.keys(docs)) {
    if (parseDocPath(path).collection !== 'queues')
      throw new Error(`${LEGACY_QUEUES_FILE}: ${path}`);
  }
  return docs;
}

/** `android-tv/contract/templates.json` (C6), validated. */
export function loadTemplates(): TemplateSpec[] {
  const text = readFileSync(TEMPLATES_JSON_PATH, 'utf8');
  return templatesFileSchema.parse(parseEncodedJson(text, 'templates.json'));
}
