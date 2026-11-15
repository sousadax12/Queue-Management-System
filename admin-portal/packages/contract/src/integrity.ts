import { parseDocPath } from './collections.js';
import type { DeviceDoc } from './schemas/devices.js';
import type { MediaDoc } from './schemas/media.js';
import type { PairingCodeDoc } from './schemas/pairing-codes.js';
import type { PlaylistDoc } from './schemas/playlists.js';
import type { QueueDoc } from './schemas/queues.js';
import type { ScreenDoc } from './schemas/screens.js';
import type { SiteDoc } from './schemas/sites.js';
import type { UserDoc } from './schemas/users.js';

/**
 * Cross-document rules the Firestore rules cannot enforce and the portal must
 * (TV invariants 7, 8, 10; admin-portal "Rules shape"). Takes already-validated docs
 * keyed by path; returns human-readable problems (empty = consistent).
 */
export function checkReferences(docs: Readonly<Record<string, unknown>>): string[] {
  const problems: string[] = [];
  const orgs = new Set<string>();
  const sites = new Map<string, SiteDoc>();
  const screens = new Map<string, { siteId: string; doc: ScreenDoc }>();
  const queues = new Map<string, QueueDoc>();
  const playlists = new Map<string, PlaylistDoc>();
  const media = new Map<string, MediaDoc>();
  const devices = new Map<string, DeviceDoc>();
  const codes = new Map<string, PairingCodeDoc>();
  const users = new Map<string, UserDoc>();

  for (const [path, doc] of Object.entries(docs)) {
    const p = parseDocPath(path);
    switch (p.collection) {
      case 'orgs':
        orgs.add(p.id);
        break;
      case 'sites':
        sites.set(p.id, doc as SiteDoc);
        break;
      case 'screens':
        screens.set(p.id, { siteId: p.parentId ?? '', doc: doc as ScreenDoc });
        break;
      case 'queues':
        queues.set(p.id, doc as QueueDoc);
        break;
      case 'playlists':
        playlists.set(p.id, doc as PlaylistDoc);
        break;
      case 'media':
        media.set(p.id, doc as MediaDoc);
        break;
      case 'devices':
        devices.set(p.id, doc as DeviceDoc);
        break;
      case 'pairingCodes':
        codes.set(p.id, doc as PairingCodeDoc);
        break;
      case 'users':
        users.set(p.id, doc as UserDoc);
        break;
    }
  }

  const ids = [sites, screens, queues, playlists, media].flatMap((m) => [...m.keys()]);
  const dupes = ids.filter((id, i) => ids.indexOf(id) !== i);
  if (dupes.length)
    problems.push(`ids must be globally unique (C10): ${[...new Set(dupes)].join(', ')}`);

  const needOrg = (where: string, orgId: string) => {
    if (!orgs.has(orgId)) problems.push(`${where}: orgId ${orgId} has no orgs doc`);
  };
  const siteInOrg = (where: string, siteId: string, orgId: string) => {
    const site = sites.get(siteId);
    if (!site) problems.push(`${where}: site ${siteId} does not exist`);
    else if (site.orgId !== orgId)
      problems.push(`${where}: site ${siteId} is in ${site.orgId}, not ${orgId}`);
  };

  for (const [id, site] of sites) {
    needOrg(`sites/${id}`, site.orgId);
    const def = site.defaultScreenId;
    if (def !== null && screens.get(def)?.siteId !== id) {
      problems.push(`sites/${id}: defaultScreenId ${def} is not a screen of this site`);
    }
  }
  for (const [id, { siteId, doc }] of screens) {
    const where = `sites/${siteId}/screens/${id}`;
    siteInOrg(where, siteId, doc.orgId);
    for (const q of doc.zones.queues?.queueIds ?? []) {
      if (queues.get(q)?.siteId !== siteId)
        problems.push(`${where}: queue ${q} is not in site ${siteId}`);
    }
    const pl = doc.zones.media?.playlistId;
    if (pl) {
      const playlist = playlists.get(pl);
      if (!playlist) problems.push(`${where}: playlist ${pl} does not exist`);
      else if (
        playlist.orgId !== doc.orgId ||
        (playlist.siteId !== null && playlist.siteId !== siteId)
      ) {
        problems.push(`${where}: playlist ${pl} is not in this site or org-wide in ${doc.orgId}`);
      }
    }
  }
  for (const [id, q] of queues) siteInOrg(`queues/${id}`, q.siteId, q.orgId);
  for (const [id, p] of playlists) {
    needOrg(`playlists/${id}`, p.orgId);
    if (p.siteId !== null) siteInOrg(`playlists/${id}`, p.siteId, p.orgId);
    p.items.forEach((item, i) => {
      const m = media.get(item.mediaId);
      if (!m) return; // media docs are optional in fixtures; the portal checks it at publish time
      const where = `playlists/${id}.items[${i}]`;
      if (m.orgId !== p.orgId) problems.push(`${where}: media ${item.mediaId} is in another org`);
      if (m.status !== 'ready') problems.push(`${where}: media ${item.mediaId} is not ready`);
      if (JSON.stringify(m.renditions) !== JSON.stringify(item.renditions)) {
        problems.push(`${where}: renditions differ from media ${item.mediaId}`);
      }
    });
  }
  for (const [id, m] of media) {
    needOrg(`media/${id}`, m.orgId);
    if (m.siteId !== null) siteInOrg(`media/${id}`, m.siteId, m.orgId);
  }
  for (const [uid, d] of devices) {
    const where = `devices/${uid}`;
    if (d.orgId && d.siteId) {
      siteInOrg(where, d.siteId, d.orgId);
      if (d.screenId && screens.get(d.screenId)?.siteId !== d.siteId) {
        problems.push(
          `${where}: screen ${d.screenId} is not under site ${d.siteId} (TV invariant 8)`,
        );
      }
    }
  }
  for (const [code, c] of codes) {
    const d = devices.get(c.uid);
    if (d && (d.orgId ?? null) !== null)
      problems.push(`pairingCodes/${code}: device ${c.uid} is already claimed`);
  }
  for (const [uid, u] of users) {
    if (u.orgId !== null) {
      needOrg(`users/${uid}`, u.orgId);
      for (const s of u.sites) siteInOrg(`users/${uid}`, s, u.orgId);
    }
  }
  return problems;
}
