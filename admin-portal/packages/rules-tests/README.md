# Firestore rules tests

Emulator tests for the root [`firestore.rules`](../../../firestore.rules) (portal A3, issue #6).
The rules follow "Rules shape" in
[`docs/design/2026-11-15-admin-portal.md`](../../../docs/design/2026-11-15-admin-portal.md).

```bash
pnpm --filter @qms/rules-tests test:emulator
```

This starts a Firestore emulator on port 8180 (so it never clashes with the contract package's
emulator on 8080), loads `firestore.rules` into project `demo-qms-rules`, seeds every contract
fixture plus the Flutter-era legacy queues before each test, and signs in as one persona per
role:

| Persona                                                         | Token                                                          |
| --------------------------------------------------------------- | -------------------------------------------------------------- |
| `platform`, `adminA`, `adminB`, `operatorB`                     | Claims taken from `users.json` (the media-api mirror)          |
| `managerA`, `operatorA`                                         | `manager` / `operator` of org A's site                         |
| `tvA`, `tvB`, `tvUnclaimed`                                     | Anonymous, scoped by their `devices/{uid}` fixture             |
| `anonNoDevice`, `anonForged`, `noRole`, `unknownRole`, unauthed | Outsiders: can read and write nothing (except a box's own doc) |

Every allowed path has a test, and so does every denied one that matters, including cross-org:
org A can't read org B's data, can't point its queues, screens, playlists or boxes at org B's
sites, and can't claim a box into org B. The claim test runs the contract's own
`claimPairingCode` transaction on the web SDK, as the portal will (A5).

CI: [`.github/workflows/firestore-rules.yml`](../../../.github/workflows/firestore-rules.yml)
runs these tests and deploys the rules from `main`.
