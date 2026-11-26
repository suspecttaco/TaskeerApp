# Publicar una versión de Taskeer

## Versionado

- `versionName` sigue SemVer (`MAJOR.MINOR.PATCH`) y vive solo en `gradle.properties`
  (`taskeer.versionName`).
- `versionCode` se deriva en `app/build.gradle.kts`:
  `MAJOR*10000 + MINOR*100 + PATCH` (1.0.0 → 10000, 1.2.3 → 10203). Así nunca diverge
  de `versionName` y crece siempre, que es lo que exige Google Play. Limitación: `MINOR` y
  `PATCH` deben ser menores que 100.
- Para publicar: sube `taskeer.versionName`, haz commit y crea la etiqueta `vX.Y.Z`.

## Crear el keystore (una sola vez, lo hace la persona que publica)

```bash
keytool -genkeypair -v -keystore release.jks -alias taskeer \
  -keyalg RSA -keysize 4096 -validity 10000
```

Guarda `release.jks` y sus contraseñas fuera de Git (gestor de contraseñas y copia de
seguridad cifrada): si se pierde, no se podrán publicar actualizaciones de la misma app.
`.gitignore` ya excluye `keystore.properties`, `*.jks` y `*.keystore`.

## Configurar la firma

Copia `keystore.properties.example` a `keystore.properties` (raíz, no versionado) y rellena
`storeFile`, `storePassword`, `keyAlias` y `keyPassword`. Alternativa sin fichero, por
variables de entorno: `TASKEER_KEYSTORE_FILE`, `TASKEER_KEYSTORE_PASSWORD`,
`TASKEER_KEY_ALIAS`, `TASKEER_KEY_PASSWORD`. Si existe el fichero, tiene prioridad por
clave. Sin credenciales completas el release se construye **sin firmar** (caso de CI).

## Construir

```bash
./gradlew :app:assembleRelease :app:bundleRelease
```

- APK: `app/build/outputs/apk/release/` (`app-release.apk` firmado, `app-release-unsigned.apk` si no)
- AAB: `app/build/outputs/bundle/release/app-release.aab`

Verificar la firma del APK:

```bash
$ANDROID_HOME/build-tools/<versión>/apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

## Integración continua

`.github/workflows/ci.yml` ejecuta pruebas, lint y construye APK y AAB **sin firmar**,
que sube como artefactos. No usa secretos.

## Antes de publicar

Instala el APK firmado en un dispositivo y prueba a mano los flujos clave (crear tableros,
columnas y tareas, completar, repetir, recordatorios, búsqueda): el build de release
aplica R8 y los fallos de ofuscación solo aparecen en ejecución.
