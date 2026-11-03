import 'dart:io';

// Keep browser and API on one origin; CORS is no match for this local gateway.
Future<void> main() async {
  final server = await HttpServer.bind(
    InternetAddress.anyIPv4,
    int.parse(Platform.environment['MOBILE_WEB_PORT'] ?? '3000'),
  );
  final client = HttpClient()..autoUncompress = false;
  await for (final request in server) {
    () async {
      var started = false;
      try {
        final api = request.uri.path.startsWith('/api/');
        final target = request.uri.replace(
          scheme: 'http',
          host: api ? 'backend' : '127.0.0.1',
          port: api
              ? int.parse(Platform.environment['BACKEND_PORT'] ?? '8080')
              : 3002,
        );
        final upstream = await client.openUrl(request.method, target);
        request.headers.forEach((name, values) {
          if (name.toLowerCase() != 'host') upstream.headers.set(name, values);
        });
        await upstream.addStream(request);
        final response = await upstream.close();
        request.response.statusCode = response.statusCode;
        response.headers.forEach((name, values) {
          if (name.toLowerCase() != 'transfer-encoding') {
            request.response.headers.set(name, values);
          }
        });
        started = true;
        await request.response.addStream(response);
      } catch (error) {
        stderr.writeln('Development proxy: $error');
        if (!started) {
          request.response.statusCode = HttpStatus.badGateway;
          request.response.write(
            'The mobile app or backend is still starting.',
          );
        }
      } finally {
        await request.response.close();
      }
    }();
  }
}
