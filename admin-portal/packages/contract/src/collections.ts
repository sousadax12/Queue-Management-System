import type { z } from 'zod';
import { deviceSchema } from './schemas/devices.js';
import { mediaSchema } from './schemas/media.js';
import { orgSchema } from './schemas/orgs.js';
import { pairingCodeIdSchema, pairingCodeSchema } from './schemas/pairing-codes.js';
import { playlistSchema } from './schemas/playlists.js';
import { queueSchema } from './schemas/queues.js';
import { screenSchema } from './schemas/screens.js';
import { siteSchema } from './schemas/sites.js';
import { userSchema } from './schemas/users.js';

/** Every collection in the contract, with its path pattern and the schema of what its writer stores. */
export const COLLECTIONS = {
  orgs: { pattern: /^orgs\/([^/]+)$/, schema: orgSchema },
  sites: { pattern: /^sites\/([^/]+)$/, schema: siteSchema },
  screens: { pattern: /^sites\/([^/]+)\/screens\/([^/]+)$/, schema: screenSchema },
  devices: { pattern: /^devices\/([^/]+)$/, schema: deviceSchema },
  pairingCodes: { pattern: /^pairingCodes\/([^/]+)$/, schema: pairingCodeSchema },
  playlists: { pattern: /^playlists\/([^/]+)$/, schema: playlistSchema },
  media: { pattern: /^media\/([^/]+)$/, schema: mediaSchema },
  queues: { pattern: /^queues\/([^/]+)$/, schema: queueSchema },
  users: { pattern: /^users\/([^/]+)$/, schema: userSchema },
} as const satisfies Record<string, { pattern: RegExp; schema: z.ZodType }>;

export type CollectionName = keyof typeof COLLECTIONS;
export const COLLECTION_NAMES = Object.keys(COLLECTIONS) as CollectionName[];

export type DocOf<C extends CollectionName> = z.infer<(typeof COLLECTIONS)[C]['schema']>;

export interface DocPath {
  collection: CollectionName;
  path: string;
  /** The document id (last segment). */
  id: string;
  /** For screens, the parent site id. */
  parentId?: string;
}

export function parseDocPath(path: string): DocPath {
  for (const collection of COLLECTION_NAMES) {
    const m = COLLECTIONS[collection].pattern.exec(path);
    if (!m) continue;
    if (collection === 'screens') {
      return { collection, path, parentId: m[1] ?? '', id: m[2] ?? '' };
    }
    const id = m[1] ?? '';
    if (collection === 'pairingCodes') pairingCodeIdSchema.parse(id);
    return { collection, path, id };
  }
  throw new Error(`Not a contract document path: ${path}`);
}

export const paths = {
  org: (id: string) => `orgs/${id}`,
  site: (id: string) => `sites/${id}`,
  screen: (siteId: string, id: string) => `sites/${siteId}/screens/${id}`,
  device: (uid: string) => `devices/${uid}`,
  pairingCode: (code: string) => `pairingCodes/${code}`,
  playlist: (id: string) => `playlists/${id}`,
  media: (id: string) => `media/${id}`,
  queue: (id: string) => `queues/${id}`,
  user: (uid: string) => `users/${uid}`,
};
