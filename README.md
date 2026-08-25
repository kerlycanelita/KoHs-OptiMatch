# KoHs OptiMatch

Selector inteligente de mods para **Minecraft 26.1.2 / Fabric**. Aparece **antes** del menú principal
de Minecraft, analiza lo que tienes instalado y tu hardware, recomienda combinaciones para FPS,
latencia o PvP, y los instala.

> **Este mod no registra ningún mixin propio.** Una herramienta cuyo trabajo es detectar conflictos de
> mixins no debe añadir ninguno. La toma del menú principal se hace con los eventos de Fabric API.

---

## Entorno

Todo verificado contra el jar real de 26.1.2 y el código de Fabric Loader 0.19.3, no de memoria.

| Componente | Versión |
|---|---|
| Minecraft | `26.1.2` |
| Fabric Loader | `0.19.3` |
| Fabric API | `0.155.2+26.1.2` |
| Fabric Loom | `1.17.19` |
| Gradle | `9.5.1` |
| Java | `25` (vía toolchain) |
| Mappings | **Mojang oficiales** (Loom 1.17 las usa por defecto en 26.x, sin línea `mappings`) |

```bash
./gradlew build
```

```bash
./gradlew runClient
```

> `JAVA_HOME` en este equipo apunta a JDK 21. El `build.gradle` declara un **toolchain de Java 25**,
> así que Gradle localiza `C:\Program Files\Java\jdk-25.0.2` solo. No hace falta tocar nada.

---

## Nunca recomendar un mod que no exista para tu versión

Poner en `mods/` un jar sin build para 26.1.2 rompe la instancia. Por eso **nada se ofrece como
instalable hasta que Modrinth confirma que existe** para la versión que está corriendo:

```
GET /v2/project/{slug}/version?loaders=["fabric"]&game_versions=["26.1.2"]
```

- Un array vacío es un **"no hay build para esta versión"** definitivo y la entrada se descarta.
- **No hay fallback** a una versión cercana. Nunca.
- La versión objetivo se lee del loader en ejecución, no está escrita a fuego.
- Se exige `sha512`: sin hash no se puede verificar, así que no se ofrece.

Comprobado en ejecución sobre las 34 entradas del catálogo:

```
GATE: 34 installable, 0 blocked
GATE-NEG: modernfix       -> installable=false  NO_BUILD_FOR_VERSION
GATE-NEG: indium          -> installable=false  NO_BUILD_FOR_VERSION
GATE-NEG: threadtweak     -> installable=false  NO_BUILD_FOR_VERSION
GATE-NEG: (slug inventado)-> installable=false  NOT_FOUND
```

### Estado release / beta / alpha

La API lo da: cada versión trae `version_type`. Se modela en `ModrinthVersion.Channel` y se muestra
como etiqueta de color (**estable** verde, **beta** ámbar, **alpha** rojo). No se filtra por canal —
en una versión de Minecraft reciente muchos mods solo tienen alpha o beta, y descartarlos dejaría al
jugador sin nada. Se prefiere `release > beta > alpha` al elegir y se avisa de lo que es.

---

## Instalador

Al pulsar **Instalar** aparece un popup con la ficha (qué hace, versión y canal, tamaño,
cliente/servidor, dependencias), la documentación de Modrinth en markdown scrollable, y cuatro
botones: **Instalar**, **Abrir en Modrinth**, **Descarga directa** y **Cancelar**. La lista exacta de
jars se ve antes de descargar nada.

Tres guardas lo hacen seguro contra una instancia viva:

- **SHA-512** verificado contra el hash de Modrinth. Si no cuadra, se borra el `.part` y se aborta.
- **Guarda de duplicados**: se lee el `fabric.mod.json` del jar descargado *antes* de ponerlo en su
  sitio. Si ese mod id ya está cargado, se descarta — dos jars con el mismo id hacen que Fabric no
  arranque, que es justo lo que este mod existe para evitar.
- **Nunca se sobrescribe** un jar existente. En Windows el loader mantiene handles abiertos sobre los
  jars de `mods/`; escribir uno nuevo es seguro, reemplazarlo no.

Las dependencias requeridas se resuelven recursivamente y **cada una pasa por la misma verja de
versión**. Si a alguna le falta build, no se descarga nada y se explica cuál falla.

### Por qué hay que reiniciar

No se puede evitar, y no es una limitación de este mod. En `Knot.java:143` el arranque hace
`loader.load()` → `loader.freeze()` → `FabricMixinBootstrap.init()` → `initializeTransformers()`, todo
**antes** de que arranque Minecraft. Después, `FabricLoaderImpl.load()` lanza literalmente
`"Frozen - cannot load additional mods!"`, y `FabricMixinBootstrap.init()` lanza si se llama dos
veces. Los mixins se aplican al transformar clases al cargarlas; una clase ya cargada no se
re-transforma.

Lo que sí se hace: la descarga es instantánea y aparece una barra **"N mods listos para el próximo
arranque"** con un botón **"Cerrar Minecraft ahora"** (con confirmación), para que reiniciar sea un
solo clic.

---

## Diseño responsivo y lienzo virtual

El problema no era el 4x en sí, sino el **área lógica**: una ventana de 854x480 a escala 2, o una de
1080p a escala 4, dejan ambas unos `430x240` px lógicos — insuficiente para una consola de cinco
pestañas con panel de detalle.

`UiScale` dibuja en un **lienzo propio**: encoge el dibujado vía `pose().scale()` hasta alcanzar al
menos `640x360` px virtuales, sin bajar nunca de 1 px real por px virtual (la densidad que usa vanilla
a escala 1). Medido en el dev env: `427x240 → factor 0.667 → 641x360`, que sube el breakpoint de
COMPACT a REGULAR.

`enableScissor` aplica el `pose()` actual (llama a `ScreenRectangle.transformAxisAligned`), así que el
recorte funciona con coordenadas virtuales sin conversión manual. El ratón se convierte una sola vez,
en los puntos de entrada.

Encima siguen los tres breakpoints, para ventanas realmente estrechas:

| | COMPACT (<430) | REGULAR (430–760) | WIDE (>760) |
|---|---|---|---|
| Cabecera | marca sola | marca + versión | marca + versión |
| Pestañas | etiquetas cortas | completas | completas |
| Mods instalados | paneles **apilados** | 2 columnas | 2 columnas anchas |
| Botones de preset | **apilados** en 3 filas | 3 columnas | 3 columnas |
| Aviso de Modrinth | texto corto | texto completo | texto completo |
| Botón inferior | "Jugar" | "Continuar a Minecraft" | idem |

---

## Arquitectura

```
dev.zymekoh.optimatch
├── OptiMatchClient        Entrypoint. Toma el TitleScreen cuando el overlay de carga desaparece.
├── ui/
│   ├── OptiMatchScreen    Ventana de 5 tabs, lienzo virtual, diálogo modal y barra de reinicio.
│   ├── UiScale            El lienzo virtual.
│   ├── Breakpoint         COMPACT / REGULAR / WIDE.
│   ├── ParticleField      Campo denso de partículas, sin asignaciones por frame.
│   ├── ModIcons           Iconos locales (del jar) y remotos (Modrinth).
│   ├── MarkdownView       Visor de markdown simplificado para los README.
│   ├── Theme / Draw       Paleta morado oscuro y primitivas.
│   ├── dialog/            Diálogo modal de instalación.
│   └── tab/               Las 5 pestañas.
├── scan/
│   ├── ModScanner         Enumera mods y relee cada fabric.mod.json.
│   ├── MixinScanner       Escaneo ASM: abre los .class y extrae los targets reales.
│   └── ConflictAnalyzer   Agrupa por método y decide qué solapamientos importan.
├── hardware/              OSHI + GPU + refresco + detección de PojavLauncher.
├── catalog/               Catálogo curado, cliente de Modrinth y los 3 presets.
├── install/               Plan, descargador con SHA-512 y cambios pendientes.
└── profile/               Perfiles en config/kohs_optimatch/profiles.json.
```

### Reabrir el menú

El selector se lanza solo una vez por arranque, antes del menú principal. Para volver a él está la
integración con **Mod Menu**: entrypoint `modmenu` → `ModMenuIntegration`, que devuelve
`OptiMatchScreen::new` como `ConfigScreenFactory`. Mod Menu es opcional (`compileOnly` + `suggests`),
así que en una instancia sin él la clase nunca se carga.

`isPauseScreen()` devuelve `true` solo si hay mundo cargado: en el menú principal el panorama sigue
animándose detrás, y abierto desde el menú de pausa el mundo se queda congelado mientras lees.

### Las 5 pestañas

1. **Mods instalados** — lista con icono y, al lado, qué hace ese conjunto: efecto sumado en FPS y
   latencia, qué ya cubres, qué te falta y qué problemas hay.
2. **Para ti** — CPU / GPU / RAM / Hz / plataforma, puntuación de potencia, y los tres botones:
   **FPS máximos**, **Latencia mínima** y **Vanilla mejorado**. Todo pasa por la verja.
3. **Mods** — buscador global de Modrinth, filtrado en servidor a Fabric + tu versión, con aviso
   permanente de que los mods alojados fuera de Modrinth no aparecen.
4. **Conflictos** — escaneo ASM en segundo plano; separa *preocupantes*, *a vigilar* y *sin problema*.
5. **Perfiles** — guarda y compara combinaciones de mods.

### Por qué hace falta un catálogo curado

Modrinth sabe que un mod está en la categoría `optimization`, pero **no** sabe si sube FPS o si baja el
input lag — y son cosas distintas. `assets/kohs_optimatch/catalog/mods.json` guarda solo ese
razonamiento; la disponibilidad la resuelve Modrinth.

El caso que mejor lo ilustra: **Exordium** sube FPS limitando el redibujado del HUD, pero **añade
retardo visible a la interfaz**. Para *FPS máximos* se recomienda; para *Latencia mínima* queda
descartado y aparece como advertencia. Ninguna categoría de Modrinth expresa eso.

Para latencia real el catálogo apunta a lo que de verdad la ataca: **Raw Input Buffer** e **Ixeris**
(entrada cruda con buffer y sondeo en hilo aparte), **Packet Fixer** y **Krypton** en red.

### Cómo se detectan los conflictos

`MixinScanner` abre cada `.class` de mixin con ASM y lee las anotaciones: `@Mixin` da la clase objetivo
y la prioridad; `@Inject`, `@Redirect`, `@Overwrite`, `@ModifyConstant`, `@WrapOperation`… dan el
método y el tipo de inyección. `ConflictAnalyzer` agrupa por método y aplica las reglas reales de
resolución de Mixin:

- Dos `@Overwrite` de mods distintos → **preocupante**, uno pierde siempre.
- `@Overwrite` + cualquier otra inyección → **preocupante**, las demás desaparecen.
- Varios `@Redirect` / `@ModifyConstant` en el mismo punto → **preocupante** si comparten prioridad,
  **a vigilar** si no (gana el de mayor prioridad).
- Varios `@WrapOperation` → **a vigilar**, se encadenan pero el orden importa.
- Varios `@Inject` → **sin problema**, es lo normal.

Verificado sobre una instancia real: **655 inyecciones en 53 mods, 30 targets solapados**, clasificando
`MouseHandler#onScroll` (disputado entre dos módulos de Fabric API) como *a vigilar* y los `@Inject`
múltiples como *sin problema*.

ASM lo aporta Fabric Loader en runtime, por eso se declara `compileOnly` y no se empaqueta.

---

## Estado

Implementado, compilando y verificado en el juego:

- Entorno Gradle completo y build verde (`kohs_optimatch-0.1.0+mc26.1.2.jar`).
- Toma del menú principal con animación de entrada y partículas.
- Las 5 pestañas con datos reales, lienzo virtual e iconos locales y remotos.
- Escaneo ASM de mixins y analizador de conflictos.
- Detección de hardware (OSHI) y de PojavLauncher.
- Verja de compatibilidad contra Modrinth con canal release/beta/alpha.
- Instalador con SHA-512, dependencias recursivas y guarda de duplicados.
- Buscador global de Modrinth con visor de documentación.
- Perfiles guardados en disco.

Comprobado en ejecución:

```
INSTALL: plan files=[Ksyxis-1.4.3.jar] blockers=[]
INSTALL: Ksyxis-1.4.3.jar DOWNLOADING 26968/26968 -> VERIFYING -> DONE
INSTALL: DUP modmenu-18.0.0.jar            -> SKIPPED_ALREADY_INSTALLED
INSTALL: DUP fabric-api-0.155.2+26.1.2.jar -> SKIPPED_ALREADY_INSTALLED
CANVAS:  guiScale=2 real=427x240 factor=0.667 virtual=641x360 breakpoint=REGULAR
```

Pendiente:

- Renombrar y borrar perfiles desde la GUI (ahora se nombran por fecha).
- Paginación visible en el buscador (la API ya se consulta con `offset`).
- Mover los textos a ficheros de idioma (están en castellano en el código).

## Licencia

MIT
