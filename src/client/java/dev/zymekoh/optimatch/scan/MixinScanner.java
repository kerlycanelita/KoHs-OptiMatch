package dev.zymekoh.optimatch.scan;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.zymekoh.optimatch.OptiMatchClient;
import java.io.BufferedReader;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Reads every installed mod's compiled mixin classes and recovers what each one actually injects
 * into. This is the only reliable source: mixin configs name the classes but not their targets, and
 * the loader keeps its parsed config data internal.
 *
 * <p>ASM is provided by Fabric Loader at runtime, so the mod declares it {@code compileOnly}.
 */
public final class MixinScanner {
	private static final String MIXIN_ANNOTATION = "Lorg/spongepowered/asm/mixin/Mixin;";
	private static final int DEFAULT_PRIORITY = 1000;

	/** Annotation descriptor to injection kind. Covers both Sponge Mixin and MixinExtras. */
	private static final Map<String, MixinTarget.Kind> INJECTION_ANNOTATIONS = Map.ofEntries(
		Map.entry("Lorg/spongepowered/asm/mixin/Overwrite;", MixinTarget.Kind.OVERWRITE),
		Map.entry("Lorg/spongepowered/asm/mixin/injection/Inject;", MixinTarget.Kind.INJECT),
		Map.entry("Lorg/spongepowered/asm/mixin/injection/Redirect;", MixinTarget.Kind.REDIRECT),
		Map.entry("Lorg/spongepowered/asm/mixin/injection/ModifyArg;", MixinTarget.Kind.MODIFY_ARG),
		Map.entry("Lorg/spongepowered/asm/mixin/injection/ModifyArgs;", MixinTarget.Kind.MODIFY_ARG),
		Map.entry("Lorg/spongepowered/asm/mixin/injection/ModifyVariable;", MixinTarget.Kind.MODIFY_VARIABLE),
		Map.entry("Lorg/spongepowered/asm/mixin/injection/ModifyConstant;", MixinTarget.Kind.MODIFY_CONSTANT),
		Map.entry("Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;", MixinTarget.Kind.WRAP_OPERATION),
		Map.entry("Lcom/llamalad7/mixinextras/injector/WrapWithCondition;", MixinTarget.Kind.WRAP_WITH_CONDITION),
		Map.entry("Lcom/llamalad7/mixinextras/injector/ModifyReturnValue;", MixinTarget.Kind.MODIFY_RETURN)
	);

	private MixinScanner() {
	}

	/** Scans every mod that declares mixin configs. Never throws: a bad jar yields fewer results. */
	public static List<MixinTarget> scan(List<InstalledMod> mods) {
		List<MixinTarget> targets = new ArrayList<>();

		for (InstalledMod mod : mods) {
			if (!mod.hasMixins() || mod.rootPaths().isEmpty()) {
				continue;
			}
			for (String config : mod.mixinConfigs()) {
				try {
					scanConfig(mod, config, targets);
				} catch (Exception exception) {
					OptiMatchClient.LOGGER.debug("Could not scan mixin config {} of {}", config, mod.id(), exception);
				}
			}
		}

		OptiMatchClient.LOGGER.info("Mixin scan finished: {} injections across {} mods", targets.size(), mods.size());
		return List.copyOf(targets);
	}

	private static void scanConfig(InstalledMod mod, String configName, List<MixinTarget> out) {
		Path configPath = resolve(mod, configName);
		if (configPath == null) {
			return;
		}

		JsonObject config;
		try (BufferedReader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
			JsonElement parsed = JsonParser.parseReader(reader);
			if (!parsed.isJsonObject()) {
				return;
			}
			config = parsed.getAsJsonObject();
		} catch (Exception exception) {
			return;
		}

		String mixinPackage = config.has("package") ? config.get("package").getAsString() : "";
		int configPriority = config.has("priority") ? config.get("priority").getAsInt() : DEFAULT_PRIORITY;

		List<String> classNames = new ArrayList<>();
		// "server" is skipped: those mixins never apply on a client instance.
		collect(config, "mixins", classNames);
		collect(config, "client", classNames);

		for (String className : classNames) {
			String relative = (mixinPackage.isEmpty() ? className : mixinPackage + "." + className)
				.replace('.', '/') + ".class";
			Path classPath = resolve(mod, relative);
			if (classPath == null) {
				continue;
			}
			try (InputStream stream = Files.newInputStream(classPath)) {
				readMixinClass(mod, stream, configPriority, out);
			} catch (Exception exception) {
				OptiMatchClient.LOGGER.debug("Could not read mixin class {} of {}", relative, mod.id(), exception);
			}
		}
	}

	private static void readMixinClass(InstalledMod mod, InputStream stream, int configPriority, List<MixinTarget> out)
		throws Exception {
		ClassNode node = new ClassNode();
		new ClassReader(stream).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

		AnnotationNode mixinAnnotation = findAnnotation(node.visibleAnnotations, MIXIN_ANNOTATION);
		if (mixinAnnotation == null) {
			mixinAnnotation = findAnnotation(node.invisibleAnnotations, MIXIN_ANNOTATION);
		}
		if (mixinAnnotation == null) {
			return;
		}

		List<String> targetClasses = readTargetClasses(mixinAnnotation);
		if (targetClasses.isEmpty()) {
			return;
		}

		Object rawPriority = annotationValue(mixinAnnotation, "priority");
		int priority = rawPriority instanceof Integer value ? value : configPriority;

		if (node.methods == null) {
			return;
		}

		for (MethodNode method : node.methods) {
			List<AnnotationNode> annotations = new ArrayList<>();
			if (method.visibleAnnotations != null) {
				annotations.addAll(method.visibleAnnotations);
			}
			if (method.invisibleAnnotations != null) {
				annotations.addAll(method.invisibleAnnotations);
			}

			for (AnnotationNode annotation : annotations) {
				MixinTarget.Kind kind = INJECTION_ANNOTATIONS.get(annotation.desc);
				if (kind == null) {
					continue;
				}

				for (String selector : readMethodSelectors(annotation, method, kind)) {
					for (String targetClass : targetClasses) {
						out.add(new MixinTarget(mod.id(), node.name, targetClass, selector, kind, priority));
					}
				}
			}
		}
	}

	/** {@code @Mixin} carries targets either as class literals ({@code value}) or strings ({@code targets}). */
	private static List<String> readTargetClasses(AnnotationNode annotation) {
		List<String> targets = new ArrayList<>();

		Object value = annotationValue(annotation, "value");
		if (value instanceof List<?> list) {
			for (Object element : list) {
				if (element instanceof Type type) {
					targets.add(type.getInternalName());
				}
			}
		}

		Object named = annotationValue(annotation, "targets");
		if (named instanceof List<?> list) {
			for (Object element : list) {
				if (element instanceof String string) {
					targets.add(string.replace('.', '/'));
				}
			}
		}

		return targets;
	}

	/**
	 * {@code @Overwrite} takes its target from the annotated method's own name; every other
	 * annotation names its target explicitly in a {@code method} array.
	 */
	private static List<String> readMethodSelectors(AnnotationNode annotation, MethodNode method, MixinTarget.Kind kind) {
		if (kind == MixinTarget.Kind.OVERWRITE) {
			return List.of(method.name + method.desc);
		}

		Object raw = annotationValue(annotation, "method");
		List<String> selectors = new ArrayList<>();
		if (raw instanceof List<?> list) {
			for (Object element : list) {
				if (element instanceof String string) {
					selectors.add(string);
				}
			}
		} else if (raw instanceof String string) {
			selectors.add(string);
		}

		return selectors.isEmpty() ? List.of(method.name) : selectors;
	}

	private static AnnotationNode findAnnotation(List<AnnotationNode> annotations, String descriptor) {
		if (annotations == null) {
			return null;
		}
		for (AnnotationNode annotation : annotations) {
			if (descriptor.equals(annotation.desc)) {
				return annotation;
			}
		}
		return null;
	}

	/** ASM stores annotation members as a flat name/value alternating list. */
	private static Object annotationValue(AnnotationNode annotation, String name) {
		if (annotation.values == null) {
			return null;
		}
		for (int index = 0; index + 1 < annotation.values.size(); index += 2) {
			if (name.equals(annotation.values.get(index))) {
				return annotation.values.get(index + 1);
			}
		}
		return null;
	}

	private static void collect(JsonObject config, String key, List<String> out) {
		JsonElement element = config.get(key);
		if (element == null || !element.isJsonArray()) {
			return;
		}
		JsonArray array = element.getAsJsonArray();
		for (JsonElement item : array) {
			if (item.isJsonPrimitive()) {
				out.add(item.getAsString());
			}
		}
	}

	private static Path resolve(InstalledMod mod, String relative) {
		for (Path root : mod.rootPaths()) {
			try {
				Path candidate = root.resolve(relative.replace("/", root.getFileSystem().getSeparator()));
				if (Files.exists(candidate)) {
					return candidate;
				}
			} catch (Exception ignored) {
				// A jar filesystem can reject odd separators; just try the next root.
			}
		}
		return null;
	}
}
