# rayban-meta-dat-android

App nativa mínima en Kotlin y Jetpack Compose. Stream en vivo de la cámara de Ray-Ban Meta / Meta AI glasses con el [Device Access Toolkit oficial](https://github.com/facebook/meta-wearables-dat-android) y un HUD de latencia en ms.

Hermano de [rayban-meta-dat-ios](https://github.com/FedeSack/rayban-meta-dat-ios). Mismo producto, lado Android.

Solo el SDK oficial. No hay cámara web inventada ni scraping de Meta AI. Una PWA no puede ver la cámara de las lentes.

## Qué hace

Dos pantallas, oscuras y con poco chrome.

1. **Conectar / registrar.** Registro con Meta AI, o Mock Device Kit sin hardware.
2. **Preview.** Surface de frames + HUD `N ms` (con caption `pipeline` cuando aplica) + Iniciar / Detener.

El dominio es el del SDK. `DeviceSessionState` es la sesión. `StreamState` es el stream. La app no remapea esas máquinas de estados. Configuración pedida: `VideoQuality.HIGH` (720×1280) a 24 fps, `compressVideo = true`. El API acepta 2, 7, 15, 24 o 30.

El preview HEVC va a un `Surface` via `MediaCodec`. Compose no copia cada frame. El fallback YUV (frames sin comprimir) dibuja sobre el mismo Surface.

## Límites de Developer Preview

DAT está en developer preview. Eso no es un disclaimer decorativo. Cambia cómo se prueba y cómo se reparte la app.

- **No hay publicación pública.** Las integraciones DAT no se publican todavía en Play Store ni en App Store. Meta lo dice en el [README del SDK](https://github.com/facebook/meta-wearables-dat-android) y en [set-up-release-channels](https://wearables.developer.meta.com/docs/develop/dat/set-up-release-channels). Los canales públicos no están abiertos.
- **Release channels invite-only.** En Wearables Developer Center creás una organización, un proyecto, una versión y un canal de prueba. Invitás testers que ya tengan cuenta Meta. Cada plataforma (iOS / Android) es una app distinta en el Center. Ver [FAQ](https://developers.meta.com/wearables/faq/) y [known issues](https://wearables.developer.meta.com/docs/develop/dat/knownissues/).
- **Developer Mode en Meta AI.** Para builds locales, `mwdat_application_id` y `mwdat_client_token` van en `0`. En el teléfono, Meta AI → Settings → App Info → tocá el número de versión cinco veces → Developer Mode. La app aparece en App connections → Developer mode apps. Guía: [getting-started-toolkit](https://wearables.developer.meta.com/docs/develop/dat/getting-started-toolkit).
- **Mock Device Kit.** Sin lentes, usá el botón **Usar Mock Device**. El SDK simula pairing, permisos y un feed (cámara del teléfono o video HEVC). El video mock tiene que ser h.264 o h.265. Docs: [Mock Device Kit](https://wearables.developer.meta.com/docs/develop/dat/mock-device-kit).
- **Lentes soportados.** Ray-Ban Meta Gen 1 y Gen 2, Ray-Ban Meta Optics, Meta Ray-Ban Display. Versiones de firmware y de Meta AI en [version-dependencies](https://wearables.developer.meta.com/docs/develop/dat/version-dependencies/).

Cuando Meta abra publicación, reemplazá los placeholders `0` por el Application ID y el Client Token del Wearables Developer Center.

## Cómo se mide la latencia

El HUD muestra el número en ms. Si el PTS no es un reloj `elapsedRealtime` de las lentes (el caso habitual), el chip agrega la etiqueta `pipeline`.

`VideoFrame.presentationTimeUs` es el PTS del frame. Si ese valor parece `elapsedRealtime` en microsegundos (edad entre 0 y 10 s), el número es captura → pantalla. Si el PTS es tiempo relativo al stream, el número es llegada → draw. Eso es latencia de pipeline / decode, no un reloj de las lentes.

No inventes un "glass clock" que el SDK no expone.

## Cómo buildear el APK debug

Hace falta Android Studio Narwhal o más nuevo, JDK 17+, Android SDK 36, y un [classic PAT](https://github.com/settings/tokens) con scope `read:packages` para bajar `com.meta.wearable:mwdat-*` 0.9.0 desde GitHub Packages.

1. Copiá `local.properties.example` a `local.properties`.
2. Poné `sdk.dir` y `github_token`.
3. Abrí este repo en Android Studio. Sync Gradle.
4. Run → app, o:

```bash
./gradlew :app:assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/`. El `applicationId` debug es `com.fedesack.raybanmetadat.debug`.

En el teléfono: Meta AI con Developer Mode, lentes pareados, o el camino Mock.

El scheme de callback es `raybanmetadat`. Meta AI vuelve a la app por ese scheme.

## Layout

`AppState` junta `Phase` (CONNECT / LIVE) con las dos máquinas del SDK. `DatViewModel` es el único dueño de `DeviceSession` y `Camera.stream`. `FrameSink` decodifica. `Latency` es la única cuenta del HUD.

## Docs

- [DAT Android](https://github.com/facebook/meta-wearables-dat-android)
- [Integración Android](https://wearables.developer.meta.com/docs/develop/dat/build-integration-android/)
- [Develop / DAT](https://wearables.developer.meta.com/docs/develop/dat)
- [Términos](https://wearables.developer.meta.com/terms)

Al usar DAT aceptás los Meta Wearables Developer Terms.
