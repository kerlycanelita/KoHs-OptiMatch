package dev.zymekoh.optimatch.scan;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Plain-language context for the Minecraft classes mods fight over.
 *
 * <p>Mojang ships official <em>mappings</em>, which give real class and method names, but no prose
 * describing behaviour — there is no upstream document to quote. So the exact facts (signature,
 * access, whether the method exists) come from the bytecode via {@link TargetInspector}, and this
 * file supplies the human explanation: what the class is for, and why two mods reaching for it
 * matters.
 *
 * <p>Anything not listed still gets a useful answer from {@link #domainOf}, which reads the package
 * structure — Mojang's packages are consistent enough that the area of the game is unambiguous.
 */
public final class ClassGlossary {
	private static final Map<String, String> CLASSES = new LinkedHashMap<>();
	private static final Map<String, String> METHODS = new LinkedHashMap<>();

	static {
		// The classes that actually show up in conflict reports, roughly by how often.
		CLASSES.put("net/minecraft/client/MouseHandler",
			"Traduce el raton en movimiento de camara y clics. Todo lo que toque aqui afecta a tu punteria "
				+ "y a la sensacion de input, asi que dos mods peleando por esta clase se nota al jugar.");
		CLASSES.put("net/minecraft/client/KeyboardHandler",
			"Recibe las pulsaciones de teclado antes de que el juego las interprete.");
		CLASSES.put("net/minecraft/client/Minecraft",
			"El objeto central del cliente: el bucle principal, la pantalla activa, el nivel cargado. "
				+ "Casi todos los mods lo tocan en algun punto, asi que solaparse aqui suele ser inofensivo.");
		CLASSES.put("net/minecraft/client/renderer/GameRenderer",
			"Orquesta el render de cada fotograma: camara, campo de vision, efectos de pantalla.");
		CLASSES.put("net/minecraft/client/renderer/LevelRenderer",
			"Dibuja el mundo: chunks, entidades, cielo, particulas. Es el territorio de Sodium, y por eso "
				+ "otros mods de render chocan aqui con frecuencia.");
		CLASSES.put("net/minecraft/client/gui/Gui",
			"El HUD del juego: barra de vida, hotbar, experiencia, chat.");
		CLASSES.put("net/minecraft/client/gui/GuiGraphicsExtractor",
			"El sistema de dibujado de interfaz de 26.1. Los mods que aceleran o modifican la GUI trabajan aqui.");
		CLASSES.put("net/minecraft/client/gui/screens/Screen",
			"Clase base de todas las pantallas. Modificarla afecta a cualquier menu del juego.");
		CLASSES.put("net/minecraft/world/entity/Entity",
			"Base de todo lo que existe en el mundo. Un cambio aqui se propaga a mobs, proyectiles y jugadores.");
		CLASSES.put("net/minecraft/world/entity/player/Player",
			"Logica del jugador: movimiento, ataque, inventario, estados.");
		CLASSES.put("net/minecraft/world/entity/LivingEntity",
			"Todo lo que tiene vida: dano, regeneracion, efectos, muerte. Muy disputada en mods de PvP.");
		CLASSES.put("net/minecraft/network/Connection",
			"El canal de red con el servidor. Los mods de latencia y red trabajan aqui.");
		CLASSES.put("net/minecraft/client/multiplayer/ClientPacketListener",
			"Procesa los paquetes que llegan del servidor.");
		CLASSES.put("net/minecraft/client/multiplayer/ClientLevel",
			"El mundo tal y como lo ve el cliente.");
		CLASSES.put("net/minecraft/server/MinecraftServer",
			"El servidor integrado, el que corre en una partida de un jugador.");
		CLASSES.put("net/minecraft/client/renderer/texture/TextureManager",
			"Carga y libera texturas.");
		CLASSES.put("net/minecraft/client/renderer/entity/EntityRenderDispatcher",
			"Decide como se dibuja cada entidad. Los mods de culling actuan aqui.");
		CLASSES.put("net/minecraft/world/level/block/state/BlockState",
			"El estado de un bloque concreto. FerriteCore reduce memoria justo aqui.");
		CLASSES.put("net/minecraft/client/particle/ParticleEngine",
			"Gestiona las particulas del mundo.");
		CLASSES.put("net/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher",
			"Dibuja cofres, carteles y demas entidades de bloque.");
		CLASSES.put("net/minecraft/client/renderer/RenderBuffers",
			"Reserva los buffers de vertices con los que se dibuja el mundo. Su constructor recibe "
				+ "maxSectionBuilders, el numero de hilos que construyen geometria de chunks, y por eso "
				+ "los mods que reescriben el renderizado suelen reclamarlo a la vez.");
		CLASSES.put("net/minecraft/client/renderer/chunk/SectionRenderDispatcher",
			"Reparte entre hilos la construccion de la geometria de cada chunk.");
		CLASSES.put("net/minecraft/client/renderer/culling/Frustum",
			"Decide que hay dentro del campo de vision. Territorio de los mods de culling.");
		CLASSES.put("net/minecraft/client/renderer/RenderStateShard",
			"Piezas de estado de OpenGL reutilizables entre pasadas de dibujado.");
		CLASSES.put("net/minecraft/client/gui/components/AbstractWidget",
			"Base de botones, deslizadores y campos de texto.");
		CLASSES.put("net/minecraft/client/player/LocalPlayer",
			"Tu jugador en el cliente: entrada, prediccion de movimiento y sincronizacion con el servidor. "
				+ "Muy disputada por mods de PvP.");
		CLASSES.put("net/minecraft/client/Options",
			"Los ajustes del juego. Los mods que anaden opciones la tocan.");
		CLASSES.put("net/minecraft/client/Timer",
			"Convierte tiempo real en ticks. Tocarla afecta al ritmo de todo el juego.");
		CLASSES.put("net/minecraft/world/item/ItemStack",
			"Una pila de objetos: cantidad, componentes y durabilidad.");
		CLASSES.put("net/minecraft/client/renderer/texture/TextureAtlas",
			"El atlas donde se empaquetan las texturas de bloques y objetos.");
		CLASSES.put("net/minecraft/client/sounds/SoundEngine",
			"Reproduce y mezcla el sonido del juego.");

		// Methods worth calling out by name.
		METHODS.put("onScroll", "Rueda del raton. Mods de zoom, de scroll de hotbar y de inventario compiten aqui.");
		METHODS.put("turnPlayer", "Convierte el movimiento del raton en giro de camara. Es el corazon de la punteria.");
		METHODS.put("tick", "Se ejecuta una vez por tick del juego. Solaparse aqui es habitual y casi siempre inofensivo.");
		METHODS.put("render", "Dibuja un fotograma o un elemento. Muy disputado entre mods de rendimiento.");
		METHODS.put("init", "Construye una pantalla o subsistema al abrirse.");
		METHODS.put("<init>", "El constructor. Dos mods modificando la construccion del mismo objeto es delicado.");
		METHODS.put("close", "Libera recursos al cerrar.");
		METHODS.put("extractRenderState", "Recoge el estado a dibujar de una pantalla en 26.1. "
			+ "Sustituye al antiguo render y es donde trabajan los mods de interfaz.");
		METHODS.put("turnPlayer", "Aplica el giro de camara a partir del delta del raton.");
		METHODS.put("handleAccumulatedMovement", "Vuelca el movimiento de raton acumulado en el jugador.");
		METHODS.put("shouldRender", "Decide si algo se dibuja. Los mods de culling viven aqui.");
		METHODS.put("setupRender", "Prepara el pase de render de un fotograma.");
		METHODS.put("renderLevel", "Dibuja el mundo entero. Es el metodo mas caro del juego.");
	}

	private ClassGlossary() {
	}

	/** Curated description of a class, or null when there is none. */
	public static String describeClass(String internalName) {
		return CLASSES.get(internalName);
	}

	/** Curated note about a method name, or null. */
	public static String describeMethod(String methodName) {
		return METHODS.get(methodName);
	}

	/**
	 * The area of the game a class belongs to, read from its package. Used when a class has no
	 * curated entry, which is most of them.
	 */
	public static String domainOf(String internalName) {
		if (internalName == null) {
			return "Desconocido";
		}
		if (internalName.startsWith("net/minecraft/client/renderer")
			|| internalName.startsWith("com/mojang/blaze3d")) {
			return "Renderizado";
		}
		if (internalName.startsWith("net/minecraft/client/gui")) {
			return "Interfaz";
		}
		if (internalName.startsWith("net/minecraft/client/particle")) {
			return "Particulas";
		}
		if (internalName.startsWith("net/minecraft/client/multiplayer")
			|| internalName.startsWith("net/minecraft/network")) {
			return "Red";
		}
		if (internalName.startsWith("net/minecraft/client/sounds")
			|| internalName.startsWith("net/minecraft/sounds")) {
			return "Sonido";
		}
		if (internalName.startsWith("net/minecraft/world/entity")) {
			return "Entidades";
		}
		if (internalName.startsWith("net/minecraft/world/level")) {
			return "Mundo y bloques";
		}
		if (internalName.startsWith("net/minecraft/world/item")) {
			return "Objetos";
		}
		if (internalName.startsWith("net/minecraft/server")) {
			return "Servidor";
		}
		if (internalName.startsWith("net/minecraft/client")) {
			return "Cliente";
		}
		return "Motor del juego";
	}

	/**
	 * A guess at what a method does from its name alone.
	 *
	 * <p>Worth doing because 26.1 ships unobfuscated: these are Mojang's own names, chosen to be
	 * descriptive, not machine-generated ones. Returns null when the name says nothing useful.
	 */
	public static String inferFromMethodName(String methodName) {
		if (methodName == null || methodName.isBlank()) {
			return null;
		}
		String name = methodName.toLowerCase(java.util.Locale.ROOT);
		if (name.startsWith("should") || name.startsWith("is") || name.startsWith("can") || name.startsWith("has")) {
			return "Devuelve una decision. Cambiarla altera cuando ocurre algo, no como ocurre.";
		}
		if (name.startsWith("get")) {
			return "Lee un valor. Interceptarlo cambia lo que ve el resto del juego.";
		}
		if (name.startsWith("set")) {
			return "Escribe un valor.";
		}
		if (name.startsWith("on") || name.startsWith("handle")) {
			return "Responde a un evento. Suele ser un buen punto de enganche y varios mods pueden convivir.";
		}
		if (name.startsWith("render") || name.startsWith("draw") || name.startsWith("blit")) {
			return "Dibuja algo. Solaparse aqui afecta a lo que se ve y al rendimiento.";
		}
		if (name.startsWith("create") || name.startsWith("build") || name.startsWith("make")) {
			return "Construye un objeto. Reemplazarlo cambia el objeto para todo el juego.";
		}
		if (name.startsWith("update") || name.startsWith("refresh")) {
			return "Actualiza un estado ya existente.";
		}
		return null;
	}

	/** Best available explanation: the curated one, else a sentence built from the package. */
	public static String explain(String internalName) {
		String curated = describeClass(internalName);
		if (curated != null) {
			return curated;
		}
		int lastSlash = internalName.lastIndexOf('/');
		String simple = lastSlash >= 0 ? internalName.substring(lastSlash + 1) : internalName;
		return simple + " forma parte de " + domainOf(internalName).toLowerCase(java.util.Locale.ROOT)
			+ ". No hay descripcion oficial de Mojang para esta clase: los mappings dan el nombre, no su comportamiento.";
	}
}
