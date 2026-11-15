import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';

/// The roles `firestore.rules` knows (custom claim `role`, set by the portal's media-api).
enum StaffRole { platformAdmin, orgAdmin, manager, operator }

StaffRole? _roleNamed(Object? name) {
  for (final role in StaffRole.values) {
    if (role.name == name) return role;
  }
  return null;
}

/// What the signed-in account may operate, read from its custom claims `{role, org, sites}`.
///
/// It mirrors `operates()` in `firestore.rules`: platform admins act on every queue, org
/// admins on their org's queues, managers and operators on the queues of their sites. The
/// rules reject a query that could return a queue outside that scope, so [queuesQuery]
/// always filters by [orgFilter] and [siteFilter].
class OperatorScope {
  const OperatorScope({required this.role, this.orgId, this.sites = const []});

  /// Firestore caps `whereIn` at 30 values.
  static const maxSitesPerQuery = 30;

  final StaffRole role;
  final String? orgId;
  final List<String> sites;

  /// The scope of an ID token's claims, or null when they grant no access (no role, an
  /// unknown role, or a role without the org or sites it needs).
  static OperatorScope? fromClaims(Map<String, dynamic>? claims) {
    if (claims == null) return null;
    final role = _roleNamed(claims['role']);
    if (role == null) return null;
    final org = claims['org'];
    final orgId = org is String && org.isNotEmpty ? org : null;
    final rawSites = claims['sites'];
    final sites = rawSites is List
        ? rawSites.whereType<String>().where((s) => s.isNotEmpty).toList()
        : <String>[];

    switch (role) {
      case StaffRole.platformAdmin:
        return const OperatorScope(role: StaffRole.platformAdmin);
      case StaffRole.orgAdmin:
        return orgId == null ? null : OperatorScope(role: role, orgId: orgId);
      case StaffRole.manager:
      case StaffRole.operator:
        if (orgId == null || sites.isEmpty) return null;
        return OperatorScope(role: role, orgId: orgId, sites: sites);
    }
  }

  /// `orgId ==` filter for the queue query; null for platform admins (every org).
  String? get orgFilter => role == StaffRole.platformAdmin ? null : orgId;

  /// `siteId in` filter for the queue query; null when the whole org (or every org) is in scope.
  List<String>? get siteFilter {
    if (role == StaffRole.platformAdmin || role == StaffRole.orgAdmin) return null;
    return sites.take(maxSitesPerQuery).toList();
  }

  /// The queues this account may see and advance.
  Query<Map<String, dynamic>> queuesQuery(FirebaseFirestore db) {
    Query<Map<String, dynamic>> query = db.collection('queues');
    final org = orgFilter;
    if (org != null) query = query.where('orgId', isEqualTo: org);
    final siteIds = siteFilter;
    if (siteIds != null) query = query.where('siteId', whereIn: siteIds);
    return query;
  }
}

/// The scope of the signed-in user, or null (and signed out) when there is no usable account.
///
/// Builds before issue #6 signed in anonymously. Anonymous users get no access under the
/// org-aware rules, so such a session is dropped and the operator signs in again.
Future<OperatorScope?> loadOperatorScope(FirebaseAuth auth,
    {bool forceRefresh = false}) async {
  final user = auth.currentUser;
  if (user == null) return null;
  if (user.isAnonymous) {
    await auth.signOut();
    return null;
  }
  final token = await user.getIdTokenResult(forceRefresh);
  final scope = OperatorScope.fromClaims(token.claims);
  if (scope == null) await auth.signOut();
  return scope;
}
