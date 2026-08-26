# KoHs OptiMatch

**Empareja mods con tu equipo.** Se abre antes del menú principal de Minecraft, analiza lo que tienes
instalado y el hardware que tienes debajo, y recomienda —e instala— las combinaciones que de verdad te
sirven para FPS, latencia o PvP competitivo.

Para **Minecraft 26.1.2 / Fabric**.

> **No registra ningún mixin propio.** Una herramienta cuyo trabajo es detectar conflictos de mixins
> no debería añadir ninguno. La toma del menú se hace con los eventos de Fabric API.

---

## Qué hace

| Pestaña | Para qué sirve |
|---|---|
| **Mods instalados** | Tus mods con su descripción real, y qué hace el conjunto: efecto sumado en FPS y latencia, qué cubres, qué te falta. Incluye editor de sus archivos de configuración. |
| **Para ti** | Lee tu CPU, GPU, RAM y Hz, y ofrece cuatro objetivos: FPS máximos, latencia mínima, vanilla mejorado y **Competitive Legit**. Cada recomendación dice *por qué* según tu equipo. |
| **Mods** | Buscador de todo Modrinth, filtrado a builds reales para tu versión. |
| **Conflictos** | Qué mods se pelean por el mismo método, con el detalle leído del bytecode. |
| **Diagnóstico** | Lo que está roto en silencio: mixins que ya no aplican, mods desactualizados, memoria mal dimensionada. |
| **Taller de mixins** | Los mixins de todos tus mods, y los que puedes mover. Presets, empezando por **Ultra Mini Latencia**. |
| **Perfiles** | Guarda tu setup completo —mods y configuraciones— para volver a él. |

---

## Las tres cosas que lo distinguen

### Nunca ofrece un mod que no exista para tu versión

Poner en `mods/` un jar sin build para tu Minecraft rompe la instancia. Nada se ofrece hasta que
Modrinth confirma que existe:

```
GET /v2/project/{slug}/version?loaders=["fabric"]&game_versions=["26.1.2"]
```

Un array vacío es un *no* definitivo. **No hay fallback** a una versión cercana. La versión objetivo se
lee del loader en ejecución. Se exige `sha512`: sin hash no se puede verificar, así que no se ofrece.

### Detecta mixins rotos que nadie más ve

Un mixin nombra su método objetivo como texto, resuelto al cargar. Si Minecraft lo renombró o eliminó,
la inyección **simplemente no se aplica**: el mod carga, no da error, y hace menos de lo que promete.
Leyendo la clase real con ASM eso se vuelve visible. En una instancia de prueba encontró 2 inyecciones
muertas en Sodium y 1 en Fabric Permission API.

### Distingue un conflicto real de una coincidencia

Un `@Redirect` reclama **una instrucción**, no el método entero. Dos mods redirigiendo llamadas
distintas dentro del mismo método no se pisan. Comparando el `@At` de cada inyección se evita el ruido
que haría ignorar la pestaña. Medido: 822 inyecciones, 44 métodos compartidos, **0 conflictos reales**.

---

## Sobre el Taller de mixins

**Ver es universal. Cambiar no.** Y la diferencia está medida, no supuesta.

Recorrer los configs que Mixin tiene preparados lista **todos** los mixins de **todos** los mods,
coopere el mod o no. En una instancia de prueba: 71 configs, 600 mixins.

Cambiarlos es otra historia. Lo intenté por la vía directa —vaciar la lista de un config ya
preparado en `preLaunch`— y **no funciona**: ModMenu quedó con sus inyecciones aplicadas igual. El
objeto que se muta no es el que Mixin consulta al transformar. Así que el Taller solo escribe donde
el mod acepta que se le escriba:

| Mecanismo | Quién lo usa | Dónde escribe |
|---|---|---|
| Reglas de paquete | Sodium | `config/sodium-mixins.properties` |
| Opciones booleanas | ImmediatelyFast | `config/immediatelyfast.json` |
| **Ninguno** | los demás | no se puede, y se dice |

Lo que separa a unos de otros es declarar un `IMixinConfigPlugin`. Un mod que no lo declara aparece
en la lista con sus mixins y un candado, porque escribir cambios que en silencio no hacen nada es
peor que un no honesto.

Los cambios entran **al reiniciar**, que es cuando cada mod lee su archivo.

---

## Instalación

Descarga el jar de [Releases](https://github.com/kerlycanelita/KoHs-OptiMatch/releases) y ponlo en
`mods/`. Necesita [Fabric API](https://modrinth.com/mod/fabric-api). [Mod Menu](https://modrinth.com/mod/modmenu)
es opcional y sirve para reabrir el selector.

## Compilar

```bash
./gradlew build
```

| Componente | Versión |
|---|---|
| Minecraft | `26.1.2` |
| Fabric Loader | `0.19.3` |
| Fabric API | `0.155.2+26.1.2` |
| Fabric Loom | `1.17.19` |
| Java | `25` (toolchain) |
| Mappings | **Mojang oficiales** — 26.1 es la primera versión sin ofuscar |

---

## Catálogo actualizable

Los datos de rendimiento viven en [`catalog/mods.json`](catalog/mods.json). El mod lo descarga al
arrancar con `If-None-Match`, así que **editar ese archivo actualiza a todos los jugadores en ~5
minutos** sin publicar una versión nueva.

La cascada es remoto → caché → incrustado, y gana siempre la `revision` más alta. Un JSON corrupto o
vacío se rechaza en lugar de dejar a nadie sin catálogo.

Modrinth sabe que un mod está en la categoría `optimization`, pero **no** si sube FPS o si baja el input
lag — y son cosas distintas. Ese razonamiento es lo único que guarda el catálogo. El caso que lo
ilustra: **Exordium** sube FPS limitando el redibujado del HUD, pero añade retardo visible a la
interfaz. Para *FPS máximos* se recomienda; para *Latencia mínima* queda descartado.

---

## Sobre el preset Competitive Legit

Basado en las reglas publicadas de las redes grandes: rendimiento, fidelidad de entrada e información
que ya tienes son aceptables; automatizar, revelar o mover la cámara no lo son.

Es **opt-in**: cada entrada lleva `ALLOWED` / `RISKY` / `BANNED` y lo no clasificado queda fuera. Lo
valioso no es lo que recomienda, sino lo que avisa — si tienes un minimapa o un mod de perspectiva
instalado, sale marcado como prohibido con el motivo.

> Es orientación, no garantía. Cada servidor escribe sus reglas y la responsabilidad última es tuya.

---

## Seguridad

- **SHA-512** verificado en cada descarga. Si no cuadra, se borra el parcial y se aborta.
- **Guarda de duplicados**: se lee el `fabric.mod.json` del jar descargado *antes* de colocarlo. Dos
  jars con el mismo id impiden que Fabric arranque.
- **Nunca se sobrescribe** un jar existente.
- Las URLs de los autores son entrada no confiable: solo `http`/`https`, y el **host real** se muestra
  al pasar el ratón antes de que hagas clic.
- El editor de configuración escribe de forma atómica y deja un `.optimatch.bak` en el primer guardado.

## Por qué hay que reiniciar

No se puede evitar. En `Knot.java:143` el arranque hace `loader.load()` → `loader.freeze()` →
`FabricMixinBootstrap.init()`, todo **antes** de que arranque Minecraft; después, `load()` lanza
`"Frozen - cannot load additional mods!"`. Los mixins se aplican al transformar clases al cargarlas, y
en el menú principal ya hay ~29.000 clases cargadas.

Registrar mixins en caliente *es* técnicamente posible, y por eso no se hace: aplicaría solo a las
clases aún no cargadas, dejando el mod a medias de forma distinta según lo que hubieras hecho antes.
Un fallo limpio es mejor que uno irreproducible.

## Licencia

MIT
