import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

const configuredApi = String.fromEnvironment('API_BASE_URL');
final apiBase = configuredApi.isNotEmpty
    ? configuredApi
    : kIsWeb
    ? '${Uri.base.origin}/api'
    : defaultTargetPlatform == TargetPlatform.android
    ? 'http://10.0.2.2:8088/api'
    : 'http://localhost:8088/api';

class ApiException implements Exception {
  ApiException(this.message, this.status);
  final String message;
  final int status;
  @override
  String toString() => message;
}

class Api {
  Api({http.Client? client}) : client = client ?? http.Client();
  final http.Client client;
  String? token;

  Future<dynamic> request(
    String path, {
    String method = 'GET',
    Object? body,
    String? key,
  }) async {
    final request = http.Request(method, Uri.parse('$apiBase$path'));
    request.headers['Accept'] = 'application/json';
    final requestToken = token;
    if (requestToken != null) {
      request.headers['Authorization'] = 'Bearer $requestToken';
    }
    if (key != null) request.headers['Idempotency-Key'] = key;
    if (body != null) {
      request.headers['Content-Type'] = 'application/json';
      request.body = jsonEncode(body);
    }
    try {
      final response = await http.Response.fromStream(
        await client.send(request),
      );
      // A checkpoint still counts when its message is not valid JSON.
      if (response.statusCode == 401 && token == requestToken) token = null;
      dynamic data;
      try {
        data = response.body.isEmpty ? null : jsonDecode(response.body);
      } on FormatException {
        if (response.statusCode >= 200 && response.statusCode < 300) {
          throw ApiException(
            'The API returned an invalid response.',
            response.statusCode,
          );
        }
      }
      if (response.statusCode < 200 || response.statusCode >= 300) {
        throw ApiException(
          data is Map
              ? (data['detail'] ?? data['title'] ?? 'Request failed').toString()
              : 'Request failed (${response.statusCode}).',
          response.statusCode,
        );
      }
      return data;
    } on ApiException {
      rethrow;
    } catch (_) {
      throw ApiException('Could not reach the API. Please try again.', 0);
    }
  }

  String photo(String id) => '$apiBase/catalog/$id/photo';
}
