import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:loopmarket/api.dart';
import 'package:loopmarket/main.dart';

void main() {
  test('sends the bearer session and surfaces API errors', () async {
    final api = Api(
      client: MockClient((request) async {
        expect(request.headers['Authorization'], 'Bearer opaque-session');
        return http.Response(jsonEncode({'detail': 'Session expired'}), 401);
      }),
    )..token = 'opaque-session';
    await expectLater(
      api.request('/users/me'),
      throwsA(isA<ApiException>().having((e) => e.status, 'status', 401)),
    );
    expect(api.token, isNull);
  });

  test(
    'preserves HTTP status and revokes an expired session for non-JSON errors',
    () async {
      final api = Api(
        client: MockClient((_) async => http.Response('Unauthorized', 401)),
      )..token = 'expired';
      await expectLater(
        api.request('/users/me'),
        throwsA(isA<ApiException>().having((e) => e.status, 'status', 401)),
      );
      expect(api.token, isNull);

      final gateway = Api(
        client: MockClient((_) async => http.Response('Unavailable', 503)),
      );
      await expectLater(
        gateway.request('/catalog'),
        throwsA(isA<ApiException>().having((e) => e.status, 'status', 503)),
      );
    },
  );

  test('an old unauthorized response cannot revoke a new session', () async {
    final pending = Completer<http.Response>();
    final api = Api(client: MockClient((_) => pending.future))..token = 'old';
    final response = api.request('/users/me');
    api.token = 'new';
    pending.complete(http.Response('Unauthorized', 401));
    await expectLater(response, throwsA(isA<ApiException>()));
    expect(api.token, 'new');
  });

  testWidgets('browses and filters the marketplace', (tester) async {
    final paths = <String>[];
    final api = Api(
      client: MockClient((request) async {
        paths.add(request.url.toString());
        return http.Response(
          jsonEncode({'content': [], 'page': 1, 'size': 10, 'total': 0}),
          200,
        );
      }),
    );
    await tester.pumpWidget(LoopMarket(api: api));
    await tester.pumpAndSettle();
    expect(find.text('Browse Catalog'), findsOneWidget);
    expect(find.text('No listings found.'), findsOneWidget);
    await tester.tap(find.text('electronics'));
    await tester.pumpAndSettle();
    expect(paths.last, contains('category=ELECTRONICS'));
    await tester.tap(find.byTooltip('Filters'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Any condition'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('good').last);
    await tester.pumpAndSettle();
    await tester.enterText(
      find.widgetWithText(TextFormField, 'City'),
      'Toronto',
    );
    await tester.enterText(
      find.widgetWithText(TextFormField, 'Min price'),
      '25',
    );
    await tester.enterText(
      find.widgetWithText(TextFormField, 'Max price'),
      '10',
    );
    await tester.tap(find.text('Apply filters'));
    await tester.pumpAndSettle();
    expect(find.textContaining('Minimum price'), findsOneWidget);
    await tester.enterText(
      find.widgetWithText(TextFormField, 'Max price'),
      '100',
    );
    await tester.tap(find.text('Apply filters'));
    await tester.pumpAndSettle();
    final query = Uri.parse(paths.last).queryParameters;
    expect(query['city'], 'Toronto');
    expect(query['condition'], 'GOOD');
    expect(query['minPrice'], '25');
    expect(query['maxPrice'], '100');
    expect(query['category'], 'ELECTRONICS');
  });

  testWidgets('fills empty fields with the selected model and preserves edits', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(800, 1800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final requests = <http.Request>[];
    final pendingSave = Completer<http.Response>();
    final api = Api(
      client: MockClient((request) async {
        requests.add(request);
        if (request.url.path.endsWith('/generations/models')) {
          return http.Response(jsonEncode({'models': ['vision:one', 'vision:two'], 'defaultModel': 'vision:one'}), 200);
        }
        if (request.method == 'PUT') return pendingSave.future;
        if (request.url.path.endsWith('/listings')) {
          return http.Response(
            jsonEncode({'id': 'draft-1', 'version': 0}),
            200,
          );
        }
        if (request.url.path.endsWith('/generations')) {
          return http.Response(jsonEncode({'id': 'generation-1', 'status': 'PENDING'}), 200);
        }
        if (request.url.path.endsWith('/execute')) {
          expect(request.url.queryParameters['model'], 'vision:two');
          return http.Response(
            jsonEncode({
              'status': 'COMPLETED',
              'suggestions': {
                'title': 'Training saber',
                'description': 'A well-used saber',
                'condition': 'VERY_BAD',
                'price': '15.50',
              },
            }),
            200,
          );
        }
        return http.Response('{}', 200);
      }),
    )..token = 'seller-session';
    await tester.pumpWidget(MaterialApp(home: Editor(api: api)));
    await tester.pumpAndSettle();
    await tester.tap(find.text('vision:one'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('vision:two').last);
    await tester.pumpAndSettle();
    await tester.enterText(find.widgetWithText(TextField, 'Title'), 'Saber');
    await tester.tap(find.text('Generate suggestions'));
    await tester.pumpAndSettle();
    expect(requests.firstWhere((r) => r.url.path.endsWith('/listings')).headers['Idempotency-Key'], isNotEmpty);
    expect(find.text('Saber'), findsOneWidget);
    expect(find.text('Training saber'), findsNothing);
    expect(find.text('A well-used saber'), findsOneWidget);
    expect(find.text('15.50'), findsOneWidget);
    expect(find.text('Apply'), findsNothing);
    await tester.ensureVisible(find.text('Save draft'));
    await tester.tap(find.text('Save draft'));
    await tester.pump();
    pendingSave.complete(
      http.Response(jsonEncode({'id': 'draft-1', 'version': 1}), 200),
    );
    await tester.pumpAndSettle();
  });

  testWidgets('uses the photo revision when opening a saved listing', (
    tester,
  ) async {
    final api = Api(
      client: MockClient((request) async {
        if (request.url.path.endsWith('/photos')) {
          return http.Response(
            jsonEncode({
              'content': [
                {
                  'id': 'photo-1',
                  'listingId': 'listing-1',
                  'validationStatus': 'VALID',
                  'updatedAt': '2026-11-02T10:30:00Z',
                },
              ],
              'size': 50,
              'total': 1,
            }),
            200,
          );
        }
        return http.Response(
          jsonEncode({
            'id': 'listing-1',
            'title': 'Saber',
            'version': 1,
            'status': 'DRAFT',
          }),
          200,
        );
      }),
    )..token = 'seller-session';
    await tester.pumpWidget(
      MaterialApp(
        home: Editor(api: api, initial: {'id': 'listing-1'}),
      ),
    );
    await tester.pumpAndSettle();
    final preview = tester.widget<Image>(find.byType(Image).first);
    expect(
      (preview.image as NetworkImage).url,
      contains('v=2026-11-02T10%3A30%3A00Z'),
    );
  });

  testWidgets('adding a photo keeps the previous photo and shows the new one', (tester) async {
    const picker = MethodChannel('plugins.flutter.io/image_picker');
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(picker, (
      call,
    ) async {
      expect(call.method, 'pickImage');
      return '${Directory.current.path}/web/icons/Icon-192.png';
    });
    addTearDown(
      () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        picker,
        null,
      ),
    );
    var uploads = 0;
    final api = Api(
      client: MockClient((request) async {
        if (request.method == 'PUT' && request.url.path.endsWith('/content')) {
          uploads++;
          expect((jsonDecode(request.body) as Map)['base64'], isNotEmpty);
          return http.Response(
            jsonEncode({
              'id': 'photo-2',
              'listingId': 'listing-1',
              'validationStatus': 'VALID',
              'updatedAt': '2026-11-02T11:00:00Z',
            }),
            200,
          );
        }
        if (request.url.path.endsWith('/photos')) {
          if (request.method == 'POST') return http.Response(jsonEncode({'id': 'photo-2', 'listingId': 'listing-1', 'validationStatus': 'PENDING'}), 200);
          return http.Response(
            jsonEncode({
              'content': [
                {
                  'id': 'photo-1',
                  'listingId': 'listing-1',
                  'validationStatus': 'VALID',
                  'updatedAt': '2026-11-02T10:30:00Z',
                },
              ],
              'size': 50,
              'total': 1,
            }),
            200,
          );
        }
        return http.Response(
          jsonEncode({
            'id': 'listing-1',
            'title': 'Saber',
            'version': 1,
            'status': 'DRAFT',
          }),
          200,
        );
      }),
    )..token = 'seller-session';
    await tester.pumpWidget(
      MaterialApp(
        home: Editor(api: api, initial: {'id': 'listing-1'}),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Add photo (1/5)'));
    for (var i = 0; i < 20 && uploads == 0; i++) {
      await tester.pump(const Duration(milliseconds: 50));
      await tester.runAsync(
        () => Future<void>.delayed(const Duration(milliseconds: 50)),
      );
    }
    await tester.pump();
    expect(
      uploads,
      1,
      reason: tester
          .widgetList<Text>(find.byType(Text))
          .map((t) => t.data)
          .join(' | '),
    );
    expect(
      (tester.widget<Image>(find.byType(Image).first).image as NetworkImage)
          .url,
      contains('v=2026-11-02T11%3A00%3A00Z'),
    );
  });

  testWidgets('signs in and shows a seller the interested buyer', (
    tester,
  ) async {
    final api = Api(
      client: MockClient((request) async {
        final path = request.url.path;
        if (path.endsWith('/sessions')) {
          return http.Response(
            jsonEncode({'accessToken': 'seller-session'}),
            200,
          );
        }
        if (path.endsWith('/users/me')) {
          return http.Response(jsonEncode({'displayName': 'Luke'}), 200);
        }
        if (path.endsWith('/interests')) {
          return http.Response(
            jsonEncode([
              {
                'buyerDisplayName': 'Vader',
                'createdAt': '2026-01-01T00:00:00Z',
              },
            ]),
            200,
          );
        }
        if (path.endsWith('/users/me/listings')) {
          return http.Response(
            jsonEncode({
              'content': [
                {
                  'id': '1',
                  'title': 'Saber',
                  'status': 'PUBLISHED',
                  'version': 1,
                  'price': 20,
                  'interestCount': 1,
                },
              ],
              'page': 1,
              'size': 10,
              'total': 1,
            }),
            200,
          );
        }
        return http.Response(
          jsonEncode({'content': [], 'page': 1, 'size': 10, 'total': 0}),
          200,
        );
      }),
    );
    await tester.pumpWidget(LoopMarket(api: api));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Activity'));
    await tester.pumpAndSettle();
    await tester.enterText(find.widgetWithText(TextField, 'Username'), 'luke');
    await tester.enterText(
      find.widgetWithText(TextField, 'Password'),
      'secret',
    );
    await tester.tap(find.widgetWithText(FilledButton, 'Sign in'));
    await tester.pumpAndSettle();
    expect(find.text('Saber'), findsOneWidget);
    await tester.tap(find.text('Interests'));
    await tester.pumpAndSettle();
    expect(find.text('Vader'), findsOneWidget);
  });
}
