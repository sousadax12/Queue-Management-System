import 'package:flutter_test/flutter_test.dart';
import 'package:queue_management_system/auth/operator_scope.dart';

// Claims as the portal's media-api sets them; the same shapes as
// admin-portal/packages/contract/fixtures/users.json.
const _org = 'org_clinica_K9xP';
const _site = 'site_lisboa_Wd2e';

void main() {
  group('OperatorScope.fromClaims', () {
    test('an operator acts on the queues of its sites in its org', () {
      final scope = OperatorScope.fromClaims({
        'role': 'operator',
        'org': _org,
        'sites': [_site],
      })!;
      expect(scope.role, StaffRole.operator);
      expect(scope.orgFilter, _org);
      expect(scope.siteFilter, [_site]);
    });

    test('a manager is scoped like an operator', () {
      final scope = OperatorScope.fromClaims({
        'role': 'manager',
        'org': _org,
        'sites': [_site, 'site_other_1'],
      })!;
      expect(scope.orgFilter, _org);
      expect(scope.siteFilter, [_site, 'site_other_1']);
    });

    test('an org admin sees its whole org', () {
      final scope = OperatorScope.fromClaims({
        'role': 'orgAdmin',
        'org': _org,
        'sites': <String>[],
      })!;
      expect(scope.orgFilter, _org);
      expect(scope.siteFilter, isNull);
    });

    test('a platform admin sees every org', () {
      final scope = OperatorScope.fromClaims({
        'role': 'platformAdmin',
        'org': null,
        'sites': <String>[],
      })!;
      expect(scope.orgFilter, isNull);
      expect(scope.siteFilter, isNull);
    });

    test('no claims, no role or an unknown role grant nothing', () {
      expect(OperatorScope.fromClaims(null), isNull);
      expect(OperatorScope.fromClaims({}), isNull);
      expect(OperatorScope.fromClaims({'role': ''}), isNull);
      expect(
        OperatorScope.fromClaims({
          'role': 'superuser',
          'org': _org,
          'sites': [_site],
        }),
        isNull,
      );
    });

    test('a role without the org or sites it needs grants nothing', () {
      expect(OperatorScope.fromClaims({'role': 'orgAdmin'}), isNull);
      expect(
        OperatorScope.fromClaims({
          'role': 'operator',
          'org': _org,
          'sites': <String>[],
        }),
        isNull,
      );
      expect(
        OperatorScope.fromClaims({
          'role': 'operator',
          'sites': [_site],
        }),
        isNull,
      );
      expect(
        OperatorScope.fromClaims({
          'role': 'operator',
          'org': _org,
          'sites': 'not-a-list',
        }),
        isNull,
      );
    });

    test('ignores malformed site entries', () {
      final scope = OperatorScope.fromClaims({
        'role': 'operator',
        'org': _org,
        'sites': [_site, 7, '', null],
      })!;
      expect(scope.siteFilter, [_site]);
    });

    test('caps the site filter at the whereIn limit', () {
      final sites = List.generate(40, (i) => 'site_$i');
      final scope = OperatorScope.fromClaims({
        'role': 'operator',
        'org': _org,
        'sites': sites,
      })!;
      expect(scope.siteFilter, sites.take(OperatorScope.maxSitesPerQuery).toList());
    });
  });
}
