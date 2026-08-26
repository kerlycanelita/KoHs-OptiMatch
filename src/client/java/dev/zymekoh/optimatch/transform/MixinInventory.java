package dev.zymekoh.optimatch.transform;

import dev.zymekoh.optimatch.OptiMatchClient;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.spongepowered.asm.mixin.MixinEnvironment;

/**
 * A read-only census of every mixin every mod registered, taken during {@code preLaunch}.
 *
 * <p>This is the one part of mixin machinery that is genuinely universal. Mixin keeps its prepared
 * configs on the active transformer's processor, and walking them lists every mixin of every mod —
 * whether or not that mod wanted to be inspected. Nothing here writes: measurements on a running
 * instance showed that emptying a prepared config does <em>not</em> stop its mixins from applying, so
 * this class only ever reads. Changing behaviour goes through {@link KnobRegistry}, which uses each
 * mod's own supported switches.
 *
 * <p>The snapshot is taken at {@code preLaunch} because that is the moment the configs are known to be
 * reachable and complete.
 */
public final class MixinInventory implements PreLaunchEntrypoint {
	/** One mixin class and the game classes it injects into. */
	public record Mixin(String configName, String mixinClass, List<String> targets) {
		/** Trailing package segment, which is how cooperative mods name their rules. */
		public String simpleName() {
			int dot = this.mixinClass.lastIndexOf('.');
			return dot < 0 ? this.mixinClass : this.mixinClass.substring(dot + 1);
		}
	}

	/** One {@code *.mixins.json} and whether its owner allows switching pieces of it off. */
	public record Config(String name, String plugin, List<Mixin> mixins) {
		/** True when the mod declares an {@code IMixinConfigPlugin}, the hook that permits opting out. */
		public boolean cooperative() {
			return this.plugin != null && !this.plugin.isBlank();
		}
	}

	private static volatile List<Config> snapshot = List.of();
	private static volatile String failure;

	@Override
	public void onPreLaunch() {
		try {
			snapshot = capture();
			int mixins = snapshot.stream().mapToInt(c -> c.mixins().size()).sum();
			long open = snapshot.stream().filter(Config::cooperative).count();
			OptiMatchClient.LOGGER.info("Mixin census: {} configs, {} mixins, {} adjustable by their owner",
				snapshot.size(), mixins, open);
		} catch (Throwable t) {
			// Never let a census failure stop the game from starting.
			failure = t.toString();
			OptiMatchClient.LOGGER.warn("Mixin census unavailable: {}", failure);
		}
	}

	public static List<Config> configs() {
		return snapshot;
	}

	public static String failure() {
		return failure;
	}

	public static int totalMixins() {
		return snapshot.stream().mapToInt(c -> c.mixins().size()).sum();
	}

	/** All mixins, from any mod, that inject into {@code target}. */
	public static List<Mixin> touching(String target) {
		List<Mixin> hits = new ArrayList<>();
		for (Config config : snapshot) {
			for (Mixin mixin : config.mixins()) {
				if (mixin.targets().contains(target)) {
					hits.add(mixin);
				}
			}
		}
		return hits;
	}

	private static List<Config> capture() throws Exception {
		MixinEnvironment env = MixinEnvironment.getCurrentEnvironment();
		Object transformer = env.getActiveTransformer();
		if (transformer == null) {
			return List.of();
		}
		Object processor = read(transformer, "processor");
		List<Config> out = new ArrayList<>();
		for (String field : new String[]{"configs", "pendingConfigs"}) {
			Object value = readOrNull(processor, field);
			if (!(value instanceof List<?> list)) {
				continue;
			}
			for (Object config : list) {
				Config parsed = parse(config);
				if (parsed != null) {
					out.add(parsed);
				}
			}
		}
		return List.copyOf(out);
	}

	private static Config parse(Object config) {
		try {
			String name = String.valueOf(invoke(config, "getName"));
			Object plugin = readOrNull(config, "pluginClassName");
			List<Mixin> mixins = new ArrayList<>();
			if (readOrNull(config, "mixins") instanceof List<?> prepared) {
				for (Object info : prepared) {
					mixins.add(new Mixin(name, String.valueOf(invoke(info, "getClassName")),
						targetsOf(info)));
				}
			}
			return new Config(name, plugin == null ? null : String.valueOf(plugin), List.copyOf(mixins));
		} catch (Exception e) {
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	private static List<String> targetsOf(Object mixinInfo) {
		try {
			Object value = invoke(mixinInfo, "getTargetClasses");
			return value instanceof List<?> l ? List.copyOf((List<String>) l) : List.of();
		} catch (Exception e) {
			return List.of();
		}
	}

	private static Object readOrNull(Object target, String name) {
		try {
			return read(target, name);
		} catch (Exception e) {
			return null;
		}
	}

	private static Object read(Object target, String name) throws Exception {
		for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
			try {
				Field f = c.getDeclaredField(name);
				f.setAccessible(true);
				return f.get(target);
			} catch (NoSuchFieldException ignored) {
				// keep walking up
			}
		}
		throw new NoSuchFieldException(name);
	}

	private static Object invoke(Object target, String name) throws Exception {
		for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
			try {
				Method m = c.getDeclaredMethod(name);
				m.setAccessible(true);
				return m.invoke(target);
			} catch (NoSuchMethodException ignored) {
				// keep walking up
			}
		}
		throw new NoSuchMethodException(name);
	}
}
