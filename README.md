# Taskeer

Aplicación nativa Android (Kotlin + Jetpack Compose) para gestionar tareas kanban. Esta versión es offline-first con datos locales; no incluye red ni sincronización en esta fase.

## Requisitos

- JDK 25 (Android Studio JBR o compatible)
- Android SDK (resuelto vía `local.properties`)
- Android Studio / Gradle

## Cómo compilar y ejecutar tests

```bash
./init.sh            # Comprueba entorno (JDK/SDK) y ejecuta tests JVM
./init.sh --full     # Además: lintDebug y assembleDebug
```

También puedes usar Gradle directamente:

```bash
./gradlew :app:testDebugUnitTest
```

La puerta de verificación son los tests JVM en `app/src/test/java/**` (JUnit 4). Los tests instrumentados (`app/src/androidTest/**`) requieren emulador y no forman parte de la puerta de verificación.

## Stack

- Kotlin 2.2.10
- AGP 9.4.1
- Gradle 9.6
- minSdk 24, compileSdk/targetSdk 37
- Jetpack Compose + Material 3
- Navigation Compose 2.10.2
- DataStore Preferences 1.2.1
- Room 2.8.5 con KSP 2.2.10-2.0.2 (esquema exportado en `app/schemas/`)
- Desugaring de `java.time` (compatibilidad con minSdk 24)

## Funcionalidades actuales (verificadas)

- **Arquitectura limpia**: capas `domain/`, `data/`, `di`, `ui/`. `domain/` es Kotlin puro (sin Android). `ui/` no accede a almacenamiento.
- **Contenedor de dependencias manual**: `di/AppContainer` creado por `TaskeerApplication`.
- **Navegación**: entre 4 secciones principales (`Inicio`, `Mi día`, `Workspaces`, `Configuración`), con restauración/recorte de estado según política de navegación.
- **Tema**: claro/oscuro/automático (`LIGHT`/`DARK`/`AUTO`). Origen de color a elección del usuario: **paleta de marca** o **Material You** (color dinámico). En API < 31 no se ofrece la opción dinámica; por defecto se usa Material You si el dispositivo lo soporta, si no se usa la paleta de marca. Las preferencias del tema se persisten con DataStore.
- **Tableros (CRUD local)**: la sección `Workspaces` muestra la lista de tableros y permite **crear**, **renombrar** y **borrar**. El nombre se recorta y no puede quedar vacío (se rechaza con un error de validación visible); la lista se ordena por fecha de creación y se actualiza de forma reactiva. El borrado es lógico (soft delete): la fila permanece en la base de datos pero el tablero deja de listarse. Toda la funcionalidad es local, sin red.
- **Datos locales**: preferencias de tema/origen de color con DataStore y tableros persistidos en Room.
- **Capa de datos local (Room)**: `AppDatabase` (`taskeer.db`, versión 2) con migraciones explícitas no destructivas (la migración de esquema añade la fecha de creación) y esquema exportado en `app/schemas/`. `BoardDao` sigue las convenciones del proyecto: observación reactiva con `Flow`, lecturas puntuales y escrituras `suspend`, exclusión de filas borradas en las consultas y borrado lógico (soft delete) sin borrado físico. La base está cableada a la UI mediante `BoardRepository`, que define operaciones con tipos de dominio (`Flow<List<Board>>` y `suspend`).
- **Metadatos de sincronización**: `SyncableEntity` fija las columnas comunes de toda entidad local (id UUID de cliente, `updatedAt` en epoch ms UTC, marca `pending` y `deletedAt`) y `domain/model/SyncMetadata.kt` modela esos metadatos con sus transiciones (creación, cambio local y borrado local). `data/local/` convierte entre entidad y metadatos; los tipos de Room no salen de esa capa.
- **Dominio**: `DomainError` (tipado por código) + `DomainResult`, conversión UTC↔zona local con `java.time` (Instant en dominio, conversión solo en presentación). El modelo `Board` y sus reglas (crear, renombrar, borrado lógico, validación del nombre) son Kotlin puro.

## Estructura del proyecto

```
app/src/main/java/com/simposio/taskeer/
  TaskeerApplication.kt
  MainActivity.kt
  data/local/            # AppDatabase, DAO y entidades Room + mapeo de metadatos
  data/repository/        # Contratos e implementación de preferencias (DataStore) y tableros (Room)
  di/                    # Contenedor manual + fábricas
  domain/board/          # Reglas del tablero (crear, renombrar, borrado lógico)
  domain/model/          # Board, ThemeMode, DomainError, DomainResult, SyncMetadata
  domain/theme/          # ThemeResolution, ColorOrigin
  domain/date/           # TimeZoneProvider, DateTimeConverter (UTC en dominio)
  ui/app/                # TaskeerApp, AppViewModel
  ui/boards/             # Pantalla de tableros (lista + crear/renombrar/borrar)
  ui/home/               # Pantalla Inicio (placeholder)
  ui/myday/              # Pantalla Mi día (placeholder)
  ui/settings/           # Pantalla Configuración (placeholder)
  ui/navigation/         # AppDestination, NavigationPolicy, AppNavHost
  ui/shell/              # AppShell
  ui/theme/              # Theme, Color (dos capas: escala invariante + semánticos)
```

## Tests

Suite JVM: `app/src/test/java/**` con 94 tests (0 fallos), incluyendo guardianes de arquitectura que verifican las capas y el aislamiento de la UI. `ExampleUnitTest` no cuenta como cobertura.
