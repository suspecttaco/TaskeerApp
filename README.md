# Taskeer

Aplicación nativa Android (Kotlin + Jetpack Compose) para gestionar tareas kanban. Esta versión es offline-first con datos locales; no incluye red ni sincronización en esta fase.

## Requisitos

- JDK 25 (Android Studio JBR o compatible)
- Android SDK con la API 37 disponible; la ruta del SDK se define en la configuración local del entorno, que no se versiona
- Android Studio / Gradle

## Cómo compilar y ejecutar tests

```bash
./gradlew :app:testDebugUnitTest              # tests JVM
./gradlew :app:lintDebug :app:assembleDebug   # lint + build de depuración
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
- **Tableros (CRUD local)**: la sección `Workspaces` muestra la lista de tableros y permite **crear**, **renombrar** y **borrar**. El nombre se recorta y no puede quedar vacío (se rechaza con un error de validación visible); la lista se ordena por fecha de creación y se actualiza de forma reactiva. Al tocar el nombre de un tablero se abre su pantalla de **columnas**. El borrado es lógico (soft delete): la fila permanece en la base de datos pero el tablero deja de listarse. Toda la funcionalidad es local, sin red.
- **Columnas del tablero (CRUD local)**: al crear un tablero se **siembran tres columnas por defecto** («Por hacer», «En progreso», «Hecho»; la última marcada como columna de cierre) en la misma operación atómica que crea el tablero. En la pantalla de columnas se puede **crear** (se añade al final), **renombrar**, **reordenar** con «subir»/«bajar» y **borrar**. El nombre se recorta y no puede quedar vacío; alta y renombrado se deshabilitan con el campo vacío y los movimientos se deshabilitan en los extremos de la lista. El orden se persiste por posición y se mantiene tras recargar. El borrado es lógico (soft delete) y recompacta las posiciones de las columnas restantes; el dominio rechaza borrar una columna que tiene tareas (también cuentan las borradas lógicamente) y muestra el error en pantalla. Toda la funcionalidad es local, sin red.
- **Tareas del tablero (CRUD local)**: dentro de la pantalla de columnas, cada columna ofrece **Añadir tarea** y lista las tareas que le pertenecen; cada tarea tiene una acción **Editar** que abre el editor. El editor permite fijar **título** (obligatorio: se recorta y no puede quedar vacío; con el campo vacío el botón de guardar queda deshabilitado y el dominio rechaza la escritura con un error de validación visible), **descripción** (opcional), **prioridad** (conjunto cerrado de cuatro valores — Baja, Media, Alta y Urgente — con Media por defecto), **asignado** (opcional: sin asignar o el usuario local) y **fecha límite** (opcional). La fecha límite se captura con un selector de fecha y otro de hora **en la zona horaria del usuario** y se guarda convertida a **UTC**; al reabrir una tarea existente la fecha se muestra de nuevo en la zona local. La tarea nueva se añade al final de su columna y el editor **no** ofrece selector de columna: la tarea pertenece a la columna donde se crea. El **borrado** es lógico (soft delete): la fila permanece en la base de datos marcada como borrada y deja de listarse, sin renumerar el resto. Toda la funcionalidad es local, sin red.
- **Datos locales**: preferencias de tema/origen de color con DataStore y tableros, columnas y tareas persistidos en Room.
- **Capa de datos local (Room)**: `AppDatabase` (`taskeer.db`, versión 4) con migraciones explícitas no destructivas (la migración a la versión 2 añade la fecha de creación del tablero, la de la versión 3 crea la tabla de columnas y la de la versión 4 crea la tabla de tareas con su índice por columna y posición) y esquema exportado (versiones 1, 2, 3 y 4) en `app/schemas/`. `BoardDao`, `ColumnDao` y `TaskDao` siguen las convenciones del proyecto: observación reactiva con `Flow`, lecturas puntuales y escrituras `suspend`, exclusión de filas borradas en los listados, orden estable por posición con desempate por id y borrado lógico (soft delete) sin borrado físico. La siembra de columnas al crear un tablero se hace en una sola transacción (tablero + columnas). La base está cableada a la UI mediante `BoardRepository`, `ColumnRepository` y `TaskRepository`, que definen operaciones con tipos de dominio (`Flow<List<…>>` y `suspend`). El listado de tareas de un tablero se resuelve en el DAO con una unión con la tabla de columnas, de modo que se excluyen tanto las tareas borradas como las columnas borradas.
- **Metadatos de sincronización**: `SyncableEntity` fija las columnas comunes de toda entidad local (id UUID de cliente, `updatedAt` en epoch ms UTC, marca `pending` y `deletedAt`) y `domain/model/SyncMetadata.kt` modela esos metadatos con sus transiciones (creación, cambio local y borrado local). `data/local/` convierte entre entidad y metadatos; los tipos de Room no salen de esa capa.
- **Dominio**: `DomainError` (tipado por código) + `DomainResult`, conversión UTC↔zona local con `java.time` (Instant en dominio, conversión solo en presentación). El modelo `Board` y sus reglas (crear, renombrar, borrado lógico, validación del nombre), el modelo `Column` con sus reglas (crear, renombrar, reordenar con renumeración densa de posiciones y borrado lógico rechazado si la columna tiene tareas) y el modelo `Task` con sus reglas (crear, editar, borrado lógico, título obligatorio y campos opcionales normalizados) son Kotlin puro. La prioridad de una tarea es un conjunto cerrado (`LOW`, `MEDIUM`, `HIGH`, `URGENT`) y un valor desconocido almacenado degrada a `MEDIUM`.

## Estructura del proyecto

```
app/src/main/java/com/simposio/taskeer/
  TaskeerApplication.kt
  MainActivity.kt
  data/local/            # AppDatabase, DAO y entidades Room + mapeo de metadatos
  data/repository/        # Contratos e implementación de preferencias (DataStore), tableros, columnas y tareas (Room)
  di/                    # Contenedor manual + fábricas
  domain/board/          # Reglas del tablero (crear, renombrar, borrado lógico)
  domain/column/         # Reglas de columnas (crear, renombrar, reordenar, borrado lógico)
  domain/task/           # Reglas de tareas (crear, editar, borrado lógico)
  domain/model/          # Board, Column, Task, TaskDraft, TaskPriority, LocalUser, ThemeMode, DomainError, DomainResult, SyncMetadata
  domain/theme/          # ThemeResolution, ColorOrigin
  domain/date/           # TimeZoneProvider, DateTimeConverter (UTC en dominio)
  ui/app/                # TaskeerApp, AppViewModel
  ui/boards/             # Pantalla de tableros (lista + crear/renombrar/borrar/abrir)
  ui/columns/            # Pantalla de columnas (lista + crear/renombrar/reordenar/borrar + tareas por columna)
  ui/tasks/              # Editor de tareas (diálogo) y estado de las tareas del tablero
  ui/home/               # Pantalla Inicio (placeholder)
  ui/myday/              # Pantalla Mi día (placeholder)
  ui/settings/           # Pantalla Configuración (placeholder)
  ui/navigation/         # AppDestination, NavigationPolicy, AppNavHost
  ui/shell/              # AppShell
  ui/theme/              # Theme, Color (dos capas: escala invariante + semánticos)
```

## Tests

Suite JVM: `app/src/test/java/**` con 188 tests (0 fallos), incluyendo guardianes de arquitectura que verifican las capas y el aislamiento de la UI. `ExampleUnitTest` no cuenta como cobertura.
