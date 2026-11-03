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
- **Tableros (CRUD local)**: la sección `Workspaces` muestra la lista de tableros y permite **crear**, **renombrar** y **borrar**. El nombre se recorta y no puede quedar vacío (se rechaza con un error de validación visible); la lista se ordena por fecha de creación y se actualiza de forma reactiva. Al tocar el nombre de un tablero se abre su **tablero kanban**. El borrado es lógico (soft delete): la fila permanece en la base de datos pero el tablero deja de listarse. Toda la funcionalidad es local, sin red.
- **Columnas del tablero (CRUD local)**: al crear un tablero se **siembran tres columnas por defecto** («Por hacer», «En progreso», «Hecho»; la última marcada como columna de cierre) en la misma operación atómica que crea el tablero. En la pantalla de columnas se puede **crear** (se añade al final), **renombrar**, **reordenar** con «subir»/«bajar» y **borrar**. El nombre se recorta y no puede quedar vacío; alta y renombrado se deshabilitan con el campo vacío y los movimientos se deshabilitan en los extremos de la lista. El orden se persiste por posición y se mantiene tras recargar. El borrado es lógico (soft delete) y recompacta las posiciones de las columnas restantes; el dominio rechaza borrar una columna que tiene tareas (también cuentan las borradas lógicamente) y muestra el error en pantalla. Toda la funcionalidad es local, sin red.
- **Tareas del tablero (CRUD local)**: dentro de la pantalla de columnas, cada columna ofrece **Añadir tarea** y lista las tareas que le pertenecen; cada tarea tiene una acción **Editar** que abre el editor. El editor permite fijar **título** (obligatorio: se recorta y no puede quedar vacío; con el campo vacío el botón de guardar queda deshabilitado y el dominio rechaza la escritura con un error de validación visible), **descripción** (opcional), **prioridad** (conjunto cerrado de cuatro valores — Baja, Media, Alta y Urgente — con Media por defecto), **asignado** (el usuario local, «Yo»; el editor ya no ofrece «Sin asignar» y una tarea creada o editada sin asignado explícito queda asignada a «Yo») y **fecha límite** (opcional). La fecha límite se captura con un selector de fecha y otro de hora **en la zona horaria del usuario** y se guarda convertida a **UTC**; al reabrir una tarea existente la fecha se muestra de nuevo en la zona local. La tarea nueva se añade al final de su columna y el editor **no** ofrece selector de columna: la tarea pertenece a la columna donde se crea. El **borrado** es lógico (soft delete): la fila permanece en la base de datos marcada como borrada y deja de listarse, sin renumerar el resto. Toda la funcionalidad es local, sin red.
- **Tablero kanban**: al abrir un tablero se ven sus columnas, en orden, con las tareas de cada una como tarjetas (columna vacía con su indicación). Cada tarjeta muestra el título, la **prioridad**, la **fecha límite** (en la zona horaria del usuario), el **asignado** («Yo» para el usuario local) y una **estrella** (☆/★) pulsable que marca o desmarca la tarea en «Mi día» (el toque no inicia arrastre ni abre el editor). Con una **pulsación larga y arrastre** una tarjeta se **mueve a otra columna** (entre tarjetas o en una columna vacía) o se **reordena dentro de su columna**; soltar en el mismo sitio o fuera de toda columna no cambia nada. El movimiento se persiste de forma atómica con posiciones contiguas (se renumeran origen y destino, normalizando huecos) y se mantiene al reabrir la app. Mientras se guarda, la tarjeta se ve ya en destino; si el movimiento falla (tarea o columna inexistente o borrada, columna de otro tablero, índice fuera de rango) se revierte al orden guardado y se muestra el error. Pulsar una tarjeta abre el editor, «Añadir tarea» crea la tarea en esa columna y el botón «Columnas» abre la gestión de columnas. El botón «Añadir columna» abre un diálogo para crear una columna nueva (al final) sin salir del tablero, con el mismo recorte y validación del nombre que la pantalla de columnas. La pantalla de columnas incluye un botón «Volver» que regresa al tablero. Toda la funcionalidad es local, sin red. El gesto de arrastre está cubierto por tests de la lógica de destino, pero no por una prueba instrumentada.
- **Subtareas (CRUD local)**: el editor de una tarea existente incluye una sección de **subtareas** donde se puede **crear**, **editar** el título, **marcar como completada** (y desmarcar) y **borrar**. En una tarea nueva la sección solo muestra una indicación: las subtareas se gestionan tras guardar la tarea. El borrado es lógico (soft delete) y **borrar una tarea borra también sus subtareas** en la misma operación. Cada subtarea admite además una **fecha límite** y un **asignado** opcionales (sin asignar o el usuario local; las subtareas conservan «Sin asignar»), editables desde la propia sección. El repositorio expone el **progreso** de las subtareas de una tarea (total y completadas) como flujo reactivo. Toda la funcionalidad es local, sin red.
- **Límites de validación**: el dominio rechaza con error de validación nombres de tablero de menos de 2 o más de 150 caracteres, nombres de columna de más de 100, títulos de tarea y de subtarea de más de 200 y descripciones de tarea de más de 5000 (tras recortar espacios).
- **Límite WIP y columna de completadas**: cada columna puede tener un límite WIP opcional, que se fija o quita desde la pantalla de columnas (campo «Límite WIP» y botón «Fijar»; vacío lo quita; el dominio rechaza valores menores que 1 con un error visible). Una columna con límite no admite más tareas activas que ese límite: **crear** una tarea en ella o **mover** una tarea desde otra columna se rechaza con un conflicto (el editor permanece abierto con el error; en el tablero la tarjeta vuelve a su sitio y se muestra el error). Reordenar dentro de la columna y sacar tareas de una columna sobrepasada siempre se permite, y las tareas borradas no cuentan. En el tablero cada columna con límite muestra un contador «n/límite», la columna de cierre se marca con un ✓ y las tareas con fecha límite vencida (salvo en la columna de completadas) se muestran en color de error con descripción «Vencida: <fecha>». El dibujo del ✓, el contador y la fecha vencida no están cubiertos por pruebas instrumentadas.
- **Mi día**: la sección `Mi día` muestra una **lista unificada** de tareas, sin bloques fijos. Reúne las tareas marcadas con la estrella del tablero y las asignadas al usuario local con fecha límite (las tareas creadas sin asignado explícito cuentan como asignadas a «Yo»); una tarea que cumple ambas condiciones aparece una sola vez. Cada tarjeta muestra tablero, prioridad y fecha límite en la zona del usuario. Unos chips permiten **ordenar** por fecha límite (por defecto; sin fecha al final), prioridad o marcado reciente, y **filtrar** por todas, marcadas, asignadas o vencidas; orden y filtro se combinan y un filtro sin resultados se distingue de una lista vacía. La cabecera muestra siempre el **contador de tareas vencidas** (asignadas al usuario local, con fecha pasada, sin contar la columna de completadas), exacto y sin tope, y se recalcula al cambiar los datos. **Tocar la tarjeta abre el editor de tarea** (el mismo del tablero) y la **estrella** marca o desmarca la tarea sin abrir el editor. Las tareas en la columna de completadas no aparecen en la lista, pero conservan su marca. La marca se persiste en Room y sobrevive a reiniciar la app. Recordatorios e invitaciones no se muestran. Toda la funcionalidad es local, sin red. El dibujo de la pantalla, los chips y el toque de tarjeta y estrella no están cubiertos por pruebas instrumentadas.
- **Datos locales**: preferencias de tema/origen de color con DataStore y tableros, columnas, tareas y subtareas persistidos en Room.
- **Capa de datos local (Room)**: `AppDatabase` (`taskeer.db`, versión 8) con migraciones explícitas no destructivas (la migración a la versión 2 añade la fecha de creación del tablero, la de la versión 3 crea la tabla de columnas y la de la versión 4 crea la tabla de tareas con su índice por columna y posición la de la versión 5 crea la tabla de subtareas y la de la versión 6 añade a subtareas la fecha límite y el asignado, y a columnas, tareas y subtareas la fecha de creación `createdAt`, inicializada con el valor de `updatedAt` en las filas existentes, la de la versión 7 crea la tabla de marcas de «Mi día» y la de la versión 8 asigna «Yo» a las tareas activas que no tenían asignado) y esquema exportado (versiones 1 a 8) en `app/schemas/`. `BoardDao`, `ColumnDao`, `TaskDao` y el DAO de subtareas siguen las convenciones del proyecto: observación reactiva con `Flow`, lecturas puntuales y escrituras `suspend`, exclusión de filas borradas en los listados, orden estable por posición con desempate por id y borrado lógico (soft delete) sin borrado físico. La siembra de columnas al crear un tablero se hace en una sola transacción (tablero + columnas). La base está cableada a la UI mediante `BoardRepository`, `ColumnRepository`, `TaskRepository`, `SubtaskRepository` y `MyDayRepository`, que definen operaciones con tipos de dominio (`Flow<List<…>>` y `suspend`). El listado de tareas de un tablero se resuelve en el DAO con una unión con la tabla de columnas, de modo que se excluyen tanto las tareas borradas como las columnas borradas.
- **Metadatos de sincronización**: `SyncableEntity` fija las columnas comunes de toda entidad local (id UUID de cliente, `updatedAt` en epoch ms UTC, marca `pending` y `deletedAt`) y `domain/model/SyncMetadata.kt` modela esos metadatos con sus transiciones (creación, cambio local y borrado local). `data/local/` convierte entre entidad y metadatos; los tipos de Room no salen de esa capa.
- **Dominio**: `DomainError` (tipado por código; variantes de validación, no encontrado, no autenticado, prohibido, conflicto, red, límite de peticiones, servidor y desconocido, con `fromApi` para mapear los códigos de error de la API a su variante) + `DomainResult`, conversión UTC↔zona local con `java.time` (Instant en dominio, conversión solo en presentación). El modelo `Board` y sus reglas (crear, renombrar, borrado lógico, validación del nombre), el modelo `Column` con sus reglas (crear, renombrar, reordenar con renumeración densa de posiciones y borrado lógico rechazado si la columna tiene tareas) y el modelo `Task` con sus reglas (crear, editar, borrado lógico, título obligatorio y campos opcionales normalizados) y el modelo `Subtask` con sus reglas de validación son Kotlin puro. La prioridad de una tarea es un conjunto cerrado (`LOW`, `MEDIUM`, `HIGH`, `URGENT`) y un valor desconocido almacenado degrada a `MEDIUM`.

## Estructura del proyecto

```
app/src/main/java/com/simposio/taskeer/
  TaskeerApplication.kt
  MainActivity.kt
  data/local/            # AppDatabase, DAO y entidades Room + mapeo de metadatos
  data/repository/        # Contratos e implementación de preferencias (DataStore), tableros, columnas, tareas, subtareas y Mi día (Room)
  di/                    # Contenedor manual + fábricas
  domain/board/          # Reglas del tablero (crear, renombrar, borrado lógico)
  domain/column/         # Reglas de columnas (crear, renombrar, reordenar, borrado lógico)
  domain/task/           # Reglas de tareas (crear, editar, borrado lógico)
  domain/subtask/        # Reglas de subtareas (validación, crear, editar, completar, borrado lógico)
  domain/model/          # Board, Column, Task, TaskDraft, Subtask, SubtaskDraft, TaskPriority, LocalUser, ThemeMode, DomainError, DomainResult, SyncMetadata, modelos de Mi día
  domain/myday/          # Reglas de Mi día (fusión de orígenes, orden y filtro)
  domain/theme/          # ThemeResolution, ColorOrigin
  domain/date/           # TimeZoneProvider, DateTimeConverter (UTC en dominio)
  ui/app/                # TaskeerApp, AppViewModel
  ui/boards/             # Pantalla de tableros (lista + crear/renombrar/borrar/abrir)
  ui/board/              # Tablero kanban (tarjetas, arrastre para mover/reordenar tareas)
  ui/columns/            # Pantalla de columnas (lista + crear/renombrar/reordenar/borrar + tareas por columna)
  ui/tasks/              # Editor de tareas (diálogo, compartido por tablero y Mi día), sección de subtareas y estado de las tareas del tablero
  ui/home/               # Pantalla Inicio (placeholder)
  ui/myday/              # Pantalla Mi día (lista unificada, chips de orden y filtro, conteo de vencidas)
  ui/settings/           # Pantalla Configuración (placeholder)
  ui/navigation/         # AppDestination, NavigationPolicy, AppNavHost
  ui/shell/              # AppShell
  ui/theme/              # Theme, Color (dos capas: escala invariante + semánticos)
```

## Tests

Suite JVM: `app/src/test/java/**` con 486 tests (0 fallos), incluyendo guardianes de arquitectura que verifican las capas y el aislamiento de la UI. `ExampleUnitTest` no cuenta como cobertura.
