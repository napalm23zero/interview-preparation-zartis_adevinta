# LoopMarket mobile

Flutter client for Android and iOS, with a browser preview for development. Browse and filter the catalog by category, city, condition and price, sign in, express interest, and manage drafts and listings. The editor uploads up to five images and uses a selected local model to fill empty fields. Review estimated price and condition before publishing.

From the repository root, start the preview and backend:

```sh
./.docker/server.sh dev start backend mobile
```

Open http://localhost:3001. The mobile container proxies `/api` to the backend on the shared Docker network. Startup compiles the app and can take a minute; see `./.docker/server.sh dev logs mobile backend`.

To build the Android debug APK inside the development container, with an Android emulator already running on the host:

```sh
./.docker/server.sh dev start backend nginx mobile
docker compose --project-directory "$PWD" --env-file .env-dev -f .docker/docker-compose.yaml \
  --profile mobile exec mobile flutter build apk --debug
adb install -r mobile/build/app/outputs/flutter-apk/app-debug.apk
adb shell am start -n dev.hustletech.loopmarket/.MainActivity
```

The APK is a debug build for local use. The container does not provide an iOS toolchain: native iOS builds require Flutter, Xcode and CocoaPods on macOS. The app targets iOS 15 or later. Open Device Hub on Xcode 27 (Simulator on earlier Xcode versions), start an iOS simulator, and select it with `flutter devices` before running `flutter run -d <device-id>`.

For Android or iOS, install Flutter 3.35.5 and the platform toolchain on the host, then run:

```sh
cd mobile
flutter pub get
flutter run
```

The default API URL is `http://10.0.2.2:8088/api` on the Android emulator and `http://localhost:8088/api` on the iOS simulator. Start `backend` and `nginx` first. For a physical device, expose the development gateway on a network interface reachable from that device and provide its URL with `flutter run --dart-define=API_BASE_URL=http://<host-address>:8088/api`. Use HTTPS and production identity settings outside local development. The session is kept in memory and ends when the app closes.

Run `flutter analyze` and `flutter test` in this directory. The gallery accepts JPEG or PNG images; the API enforces its configured photo size limit (20 MiB by default). iOS builds require macOS and Xcode.
