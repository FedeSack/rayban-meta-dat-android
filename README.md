# rayban-meta-dat-android

App nativa mínima en Kotlin y Jetpack Compose. Stream en vivo de la cámara de Ray-Ban Meta / Meta AI glasses con el [Device Access Toolkit oficial](https://github.com/facebook/meta-wearables-dat-android) y un HUD de latencia en ms.

Hermano de [rayban-meta-dat-ios](https://github.com/FedeSack/rayban-meta-dat-ios). Mismo producto, lado Android.

Solo el SDK oficial. No hay cámara web inventada ni scraping de Meta AI. Una PWA no puede ver la cámara de las lentes.

## Qué hace

Dos pantallas, oscuras y con poco chrome.

1. **Conectar / registrar.** Registro con Meta AI, o Mock Device Kit sin hardware.
2. **Preview.** Surface de frames a pantalla completa. El chrome (HUD, Features, Start/Stop) respeta `WindowInsets` (`statusBars` + `navigationBars` + cutout). Entre Start y el primer frame hay overlay **Encendiendo cámara…**. HUD compacto `N ms` (caption `pipeline` cuando aplica). El panel de analíticas y el de Features se encienden a mano.

El dominio es el del SDK. `DeviceSessionState` es la sesión. `StreamState` es el stream. La app no remapea esas máquinas de estados. El stream lee `videoQuality` / `frameRate` de los feature flags (default `VideoQuality.HIGH` 720×1280 a 24 fps, `compressVideo = true`). El panel Features permite A/B HIGH|MEDIUM|LOW y 15|24|30 sin rebuild. El API acepta 2, 7, 15, 24 o 30; la UI solo expone 15/24/30.

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

`VideoFrame.presentationTimeUs` es el PTS del frame. Si ese valor parece `elapsedRealtime` en microsegundos (edad entre 0 y 10 s), el número es captura → pantalla. Si el PTS es tiempo relativo al stream, el número es llegada → draw. Eso es latencia de pipeline / decode, no un reloj de las lentes. En HEVC se conserva el timestamp de llegada por la cola del decoder; no se usa el clock de present como si fuera receive (eso pintaba `0 ms`).

No inventes un "glass clock" que el SDK no expone.

## Analíticas de sesión (locales)

Capa `StreamSessionAnalytics` + tag Logcat `RaybanDat/Analytics`. Solo señales reales del SDK / timing de la app. No hay bitrate ni reloj de lentes inventado.

Campos expuestos (HUD expandible si `analyticsOverlay` está on, y `adb logcat -s RaybanDat/Analytics`):

| Campo | Origen |
| --- | --- |
| `latencyMs` + `latencyMode` (`GLASS` / `PIPELINE`) | PTS vs `elapsedRealtime`, misma regla que el HUD |
| `interArrivalMs` + `estimatedFps` | Intervalos de llegada de `videoStream` |
| `timeToFirstFrameMs` / `timeToFirstArrivalMs` | Start → primer present / primer arrival |
| `sessionState` / `streamState` + transiciones | `DeviceSessionState` / `StreamState` |
| `width` × `height` + `resolutionChanges` | `VideoFrame` y `MediaCodec` output format |
| `framesArrived` / `framesPresented` | Collect vs `FrameSink` present |
| `decodePath` (`HEVC` / `YUV`) | `VideoFrame.isCompressed` |
| `queueDrops` | Cola HEVC llena en `FrameSink` |
| `decodeErrors` | `MediaCodec.Callback.onError` |
| `configuredQuality` / `configuredFps` / `compressVideo` | Lo que se pasa a `StreamConfiguration` (flags actuales) |

Al Stop se loguea un summary (ring buffer de 128 eventos). `verboseLogcat` agrega snapshots periódicos.

## Feature flags

Botón **Features** en Connect y Live. Persistidos en `SharedPreferences` (`rayban_dat_flags`).

- `videoQuality` — `HIGH` (default) / `MEDIUM` / `LOW`. Se pasa a `StreamConfiguration.videoQuality`.
- `frameRate` — `15` / `24` (default) / `30`. Se pasa a `StreamConfiguration.frameRate`.
- `preferSharpness` — hint persistido (off). Meta: a menor resolución el preview puede verse más nítido si Bluetooth comprime. No cambia solo el stream; Federico elige quality/fps. Se loguea al Start.
- `murdokuHqCapture` — default **off**. Modo Murdoku / HQ capture + wizard in-app de 3 pasos. No es HUD de baja latencia. Ver abajo.
- Si el stream está live, cambiar quality/fps hace stop+restart limpio. Si no hay sesión STARTED, el mensaje es **Stop and Start to apply**.
- `analyticsOverlay` — muestra el panel de stats (tap en el HUD para expandir). Default off.
- `verboseLogcat` — líneas extra `RaybanDat/Analytics`. Default off.
- `gazeBridge` — off. Relay LAN de frames JPEG para el sidecar Windows (cursor glasses→PC). Si el flag está **on** y el DAT stream está live, un WebSocket escucha `0.0.0.0:8765` en **`/frames`**. Features muestra `ws://<wifi-ip>:8765/frames`. Mensajes de texto `{"ts_ms":number,"w":number,"h":number}` y binarios JPEG (~12 fps, quality 70). Se apaga con el flag o al Stop. **`streamConfig()` fuerza `compressVideo=false`** (YUV sin comprimir) para que el pipeline JPEG tenga frames; el preview usa `DecodePath.YUV` (ya existía). Con el flag off el default sigue siendo HEVC (`compressVideo=true`) salvo el perfil propio de `murdokuHqCapture`. Tradeoff: más ancho de banda Bluetooth a cambio de frames para calib. Sin auth ni secrets. `voiceAssist` sigue stub.
- `voiceDevMode` — off. Cuando está on, los intents encolados se POSTean como JSON a una URL de webhook (campo en Features, persistido en los mismos prefs). La cola `IntentQueue` es in-memory. Smoke sin STT: Features → utterance + **Enqueue chat** (`source=chat`). **No hay wake word de Meta**; este es el path Dev hacia nuestro agente de código. Astra / secrets / APK install quedan fuera.

## Intent queue (path Dev)

Spike de egress. El schema de enqueue está cerrado:

```json
{
  "id": "uuid",
  "utterance": "string",
  "source": "dat|mic|chat",
  "ts": "ISO-8601",
  "deviceId": "string",
  "appVersion": "string",
  "flags": { "voiceDevMode": true },
  "prefer": "skill|apk|auto"
}
```

`prefer` default `auto`. Si `voiceDevMode` está off la cola igual acepta, pero no hay POST. La respuesta Dev→Android (`intentId`, `status`, `kind`, …) se guarda si el webhook la devuelve; todavía no se consume.

## Modo Murdoku (HQ capture + wizard)

`murdokuHqCapture` (default off). Captura HQ para un solver externo. Federico usa las lentes para resolver Murdoku; el tablet no necesita preview de baja latencia, necesita el frame más limpio posible.

Cuando el flag está **on**:

- `StreamConfiguration` efectivo: `VideoQuality.HIGH` (720×1280) y `frameRate = 15`, sin mutar los quality/fps guardados. Al apagar el flag, el live vuelve a HIGH/24 (o lo que haya elegido Federico).
- Tradeoff Bluetooth Classic de Meta: la radio comprime por frame. Pedir menor fps (el piso de la escalera automática es 15) suele dar más bits por still y menos blur. HIGH se mantiene porque es el tope del API (`LOW` / `MEDIUM` / `HIGH`). `compressVideo` sigue en `true` para no romper el path HEVC → `Surface` del live.
- Stills (v1): `Stream.capturePhoto()` → `PhotoData.HEIC` o `PhotoData.Bitmap`. Fallback: último `VideoFrame` YUV de `videoStream` (no se inventan APIs). JPEG 95 en MediaStore `Pictures/RaybanDat` o `files/captures` + FileProvider.
- UI: CTA de primer nivel **Modo Murdoku** en Connect (enciende el flag y entra al Live/wizard). En Live, chip de modo **Murdoku** y wizard de 3 pasos (títulos fijos):
  1. **Instrucciones.** Prompt/voz stub: «Andá a la página de instrucciones del libro naranja y mirala con los lentes.» CTA **Listo, capturar**. OK: «Instrucciones guardadas.»
  2. **El Murdoku.** «Ahora andá al Murdoku que querés resolver y encuadrá bien el grid.» CTA **Capturar puzzle**. OK: «Puzzle guardado. Analizando…»
  3. **Próxima jugada** (placeholder hasta que el análisis devuelva `moves`). Share / path de galería / **Encolar análisis**.
- El modo live con el flag **off** no cambia: preview + Start/Stop como antes.

### Handoff HARD (misma `sessionId`, orden fijo)

Nunca se envía `puzzle` sin `instructions` primero. `handoff` / `payload` son vacíos o `null` si falta el still de instrucciones. Orden de `assets` siempre:

1. `kind=instructions` (still HQ)
2. `kind=puzzle` (still HQ)

Meta: `timestamp`, `device=rayban-meta`, `quality=HIGH`.

Respuesta stub (UI): `{sessionId, moves:[{row,col,value,reason}]}` con `row`/`col` **0-index**.

Path real de entrega (v1): stills en `BoardCaptureStore` (MediaStore `Pictures/RaybanDat`) + enqueue stub en `IntentQueue` (`source=dat`, `utterance` = JSON de assets). El schema de IntentQueue **no cambia**; el payload Murdoku viaja en `utterance`. Si `voiceDevMode` está on y hay webhook, se POST-ea ese JSON. Si el body de respuesta matchea el shape de análisis y el mismo `sessionId`, la Guía muestra las jugadas. Si no, queda el placeholder. Sin secrets.

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

`AppState` junta `Phase` (CONNECT / LIVE) con las dos máquinas del SDK, el snapshot de analíticas, los feature flags, la galería Murdoku, el wizard (`MurdokuWizardState`) y el endpoint Gaze LAN. `DatViewModel` es el único dueño de `DeviceSession` y `Camera.stream`. `FrameSink` decodifica el preview. `GazeBridge` + `GazeWsServer` retransmiten JPEG por `ws://<wifi-ip>:8765/frames` si `gazeBridge` está on. `Latency`, `StreamSessionAnalytics`, `BoardCaptureMath` y `MurdokuWizardMath` son cuentas puras (tienen tests). `IntentQueue` + `IntentEgress` son el path Dev (tests de cola, JSON y “POST solo si voiceDevMode”).

## Docs

- [DAT Android](https://github.com/facebook/meta-wearables-dat-android)
- [Integración Android](https://wearables.developer.meta.com/docs/develop/dat/build-integration-android/)
- [Develop / DAT](https://wearables.developer.meta.com/docs/develop/dat)
- [Términos](https://wearables.developer.meta.com/terms)

Al usar DAT aceptás los Meta Wearables Developer Terms.
