package dev.zymekoh.optimatch.transform;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A named set of knob positions.
 *
 * <p>Each preset states a goal and moves only the switches that serve it. Deliberately short: a
 * preset that flips thirty things it cannot justify is a guess wearing a name, and the tab shows the
 * reason for every single change before applying anything.
 */
public record TransformPreset(String id, String name, String tagline, String detail,
							  Map<String, Boolean> desired, boolean restoresDefaults) {

	public static List<TransformPreset> all() {
		return List.of(ultraLowLatency(), maxFps(), faithful(), defaults());
	}

	/**
	 * The headline preset: shortest possible path from a click to a photon.
	 *
	 * <p>The one real lever here is batching. Grouping draw calls raises the frame rate, and it does
	 * it by holding work back to submit it together — which is exactly what you do not want when the
	 * question is how soon the screen reacts. Everything else stays on, because the rest of what
	 * these mods do is finish the frame sooner, and a frame that ends sooner is latency you also save.
	 */
	private static TransformPreset ultraLowLatency() {
		Map<String, Boolean> desired = new LinkedHashMap<>();
		desired.put("immediatelyfast:enhanced_batching", false);
		desired.put("immediatelyfast:experimental_sign_text_buffering", false);
		desired.put("immediatelyfast:skip_text_translucency_sorting", true);
		desired.put("immediatelyfast:fast_text_lookup", true);
		desired.put("immediatelyfast:avoid_redundant_framebuffer_switching", true);
		return new TransformPreset("ultra-latency", "Ultra Mini Latencia",
			"Lo que ves responde antes, aunque cueste FPS",
			"Apaga el agrupado de dibujado: junta las llamadas para mandarlas de golpe, y eso "
				+ "retiene trabajo para enviarlo junto. Sube FPS y retrasa lo que ves. Lo demas se "
				+ "queda encendido, porque acabar el frame antes tambien es latencia que te ahorras.",
			desired, false);
	}

	private static TransformPreset maxFps() {
		Map<String, Boolean> desired = new LinkedHashMap<>();
		desired.put("immediatelyfast:enhanced_batching", true);
		desired.put("immediatelyfast:font_atlas_resizing", true);
		desired.put("immediatelyfast:map_atlas_generation", true);
		desired.put("sodium:mixin.features.textures.animations.tracking", true);
		desired.put("sodium:mixin.features.render.entity.cull", true);
		return new TransformPreset("max-fps", "FPS Maximos",
			"Todo el trabajo que se pueda agrupar, agrupado",
			"El reverso del anterior: agrupa dibujado, reutiliza atlas y descarta pronto lo que no "
				+ "se ve. Da el numero mas alto en pantalla a cambio de algo de retardo en el HUD.",
			desired, false);
	}

	private static TransformPreset faithful() {
		Map<String, Boolean> desired = new LinkedHashMap<>();
		desired.put("sodium:mixin.features.render.world.clouds", false);
		desired.put("sodium:mixin.features.render.world.sky", false);
		desired.put("sodium:mixin.features.gui", false);
		return new TransformPreset("faithful", "Vanilla Fiel",
			"Rendimiento si, cambios visuales no",
			"Devuelve a vanilla lo que Sodium redibuja por su cuenta (cielo, nubes y sus pantallas "
				+ "de opciones) y deja intacto el motor, que es de donde salen los FPS. Util si "
				+ "quieres que se vea exactamente como el juego original.",
			desired, false);
	}

	private static TransformPreset defaults() {
		return new TransformPreset("defaults", "Restaurar valores",
			"Devolver cada mod a lo que el decide",
			"Borra todo lo que hayas escrito aqui. Cada mod vuelve a elegir por si mismo, como si "
				+ "nunca hubieras tocado nada.",
			Map.of(), true);
	}
}
