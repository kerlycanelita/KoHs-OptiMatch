package dev.zymekoh.optimatch.scan;

import dev.zymekoh.optimatch.OptiMatchClient;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Reads the real Minecraft class a mixin targets, so a conflict can be explained with facts instead
 * of guesses.
 *
 * <p>A note on sourcing: Mojang publishes <em>obfuscation maps</em>, not prose documentation. There
 * is no official text describing what {@code MouseHandler.onScroll} does. What the official mappings
 * give us are the real names, and the compiled class gives the exact signature, access flags and
 * whether the method exists at all. Everything reported here comes from that bytecode — the only
 * authoritative source — with {@link ClassGlossary} adding plain-language context on top.
 */
public final class TargetInspector {
	private TargetInspector() {
	}

	/**
	 * What a mixin is actually pointing at.
	 *
	 * @param exists       false when no such method is in the class — a mixin that would fail to apply
	 * @param descriptor   the raw JVM descriptor, e.g. {@code (JDD)V}
	 * @param parameters   decoded parameter types, in order
	 * @param returnType   decoded return type
	 */
	public record MethodInfo(
		String owner,
		String name,
		String descriptor,
		List<String> parameters,
		String returnType,
		String access,
		boolean exists
	) {
		/** Readable signature, e.g. {@code private void onScroll(long, double, double)}. */
		public String signature() {
			return (this.access.isBlank() ? "" : this.access + " ")
				+ this.returnType + " " + this.name + "(" + String.join(", ", this.parameters) + ")";
		}
	}

	/**
	 * Looks up a method on a Minecraft class by reading the class file off the runtime classpath.
	 *
	 * @param internalName class in internal form, e.g. {@code net/minecraft/client/MouseHandler}
	 * @param methodName   plain method name; the first match wins when overloads exist
	 */
	public static MethodInfo inspect(String internalName, String methodName) {
		try (InputStream stream = TargetInspector.class.getClassLoader()
			.getResourceAsStream(internalName + ".class")) {

			if (stream == null) {
				return missing(internalName, methodName);
			}

			ClassNode node = new ClassNode();
			new ClassReader(stream).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

			if (node.methods != null) {
				for (MethodNode method : node.methods) {
					if (!method.name.equals(methodName)) {
						continue;
					}
					List<String> parameters = new ArrayList<>();
					for (Type argument : Type.getArgumentTypes(method.desc)) {
						parameters.add(simpleName(argument));
					}
					return new MethodInfo(
						internalName.replace('/', '.'),
						method.name,
						method.desc,
						List.copyOf(parameters),
						simpleName(Type.getReturnType(method.desc)),
						describeAccess(method.access),
						true
					);
				}
			}
			return missing(internalName, methodName);
		} catch (Exception exception) {
			OptiMatchClient.LOGGER.debug("Could not inspect {}#{}", internalName, methodName, exception);
			return missing(internalName, methodName);
		}
	}

	/** Every method on the target class that shares this name, for showing overloads. */
	public static List<String> overloadsOf(String internalName, String methodName) {
		List<String> found = new ArrayList<>();
		try (InputStream stream = TargetInspector.class.getClassLoader()
			.getResourceAsStream(internalName + ".class")) {
			if (stream == null) {
				return found;
			}
			ClassNode node = new ClassNode();
			new ClassReader(stream).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
			if (node.methods != null) {
				for (MethodNode method : node.methods) {
					if (method.name.equals(methodName)) {
						found.add(method.desc);
					}
				}
			}
		} catch (Exception ignored) {
			// An unreadable class simply reports no overloads.
		}
		return found;
	}

	private static MethodInfo missing(String internalName, String methodName) {
		return new MethodInfo(internalName.replace('/', '.'), methodName, "", List.of(), "?", "", false);
	}

	/** Short type name: {@code net.minecraft.client.Minecraft} becomes {@code Minecraft}. */
	private static String simpleName(Type type) {
		String name = type.getClassName();
		int lastDot = name.lastIndexOf('.');
		return lastDot >= 0 ? name.substring(lastDot + 1) : name;
	}

	private static String describeAccess(int access) {
		List<String> modifiers = new ArrayList<>();
		if ((access & Opcodes.ACC_PUBLIC) != 0) {
			modifiers.add("public");
		}
		if ((access & Opcodes.ACC_PROTECTED) != 0) {
			modifiers.add("protected");
		}
		if ((access & Opcodes.ACC_PRIVATE) != 0) {
			modifiers.add("private");
		}
		if ((access & Opcodes.ACC_STATIC) != 0) {
			modifiers.add("static");
		}
		if ((access & Opcodes.ACC_FINAL) != 0) {
			modifiers.add("final");
		}
		if ((access & Opcodes.ACC_SYNTHETIC) != 0) {
			modifiers.add("synthetic");
		}
		return String.join(" ", modifiers).toLowerCase(Locale.ROOT);
	}
}
