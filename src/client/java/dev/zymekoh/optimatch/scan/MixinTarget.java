package dev.zymekoh.optimatch.scan;

/**
 * One injection performed by one mod against one Minecraft method, recovered by reading the mod's
 * compiled mixin classes.
 *
 * @param modId       the mod that owns the mixin
 * @param mixinClass  the mixin class doing the injecting
 * @param targetClass the Minecraft class being modified, in internal form
 * @param targetMethod the method selector as written in the annotation, or the overwritten name
 * @param kind        which annotation was used
 * @param priority    the effective mixin priority; higher wins when two mixins collide
 */
public record MixinTarget(
	String modId,
	String mixinClass,
	String targetClass,
	String targetMethod,
	Kind kind,
	int priority
) {
	/**
	 * Injection flavour, ordered roughly by how aggressively it claims the target. Anything at
	 * {@link #EXCLUSIVE} severity cannot share a target with another mod without one of them losing.
	 */
	public enum Kind {
		/** Replaces the method body outright. Nothing else can coexist with it. */
		OVERWRITE("@Overwrite", Severity.EXCLUSIVE),
		/** Replaces a specific call inside the method. Two redirects on one call fight. */
		REDIRECT("@Redirect", Severity.EXCLUSIVE),
		/** MixinExtras' cooperative replacement for @Redirect: several can chain safely. */
		WRAP_OPERATION("@WrapOperation", Severity.COOPERATIVE),
		/** Conditionally skips a call. Cooperative but order-sensitive. */
		WRAP_WITH_CONDITION("@WrapWithCondition", Severity.COOPERATIVE),
		/** Rewrites a constant. Two mods changing the same constant disagree. */
		MODIFY_CONSTANT("@ModifyConstant", Severity.EXCLUSIVE),
		/** Rewrites an argument. Cooperative but order matters. */
		MODIFY_ARG("@ModifyArg", Severity.COOPERATIVE),
		/** Rewrites a local variable. */
		MODIFY_VARIABLE("@ModifyVariable", Severity.COOPERATIVE),
		/** Rewrites the return value; chains cleanly. */
		MODIFY_RETURN("@ModifyReturnValue", Severity.COOPERATIVE),
		/** Adds code at a point in the method. Many mods can inject side by side. */
		INJECT("@Inject", Severity.ADDITIVE);

		private final String label;
		private final Severity severity;

		Kind(String label, Severity severity) {
			this.label = label;
			this.severity = severity;
		}

		public String label() {
			return this.label;
		}

		public Severity severity() {
			return this.severity;
		}
	}

	/** How well a given injection kind tolerates company on the same target. */
	public enum Severity {
		/** Claims the target: a second mod doing the same thing loses. */
		EXCLUSIVE,
		/** Designed to chain, but the result depends on load order. */
		COOPERATIVE,
		/** Purely additive, effectively always safe. */
		ADDITIVE
	}

	/** Human-readable target, e.g. {@code TitleScreen#init}. */
	public String prettyTarget() {
		int lastSlash = this.targetClass.lastIndexOf('/');
		String simpleClass = lastSlash >= 0 ? this.targetClass.substring(lastSlash + 1) : this.targetClass;
		int descriptor = this.targetMethod.indexOf('(');
		String methodName = descriptor > 0 ? this.targetMethod.substring(0, descriptor) : this.targetMethod;
		return simpleClass + "#" + methodName;
	}
}
