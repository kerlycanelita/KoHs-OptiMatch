package dev.zymekoh.optimatch.scan;

import java.nio.file.Path;
import java.util.List;

/**
 * A mod discovered in the running instance, enriched with the raw {@code fabric.mod.json} details
 * that {@link net.fabricmc.loader.api.metadata.ModMetadata} does not expose (notably mixin configs).
 *
 * @param mixinConfigs mixin config file names declared by the mod, used by the conflict scanner
 * @param kind         how the mod got into the instance
 */
public record InstalledMod(
	String id,
	String name,
	String version,
	String description,
	String authors,
	String iconPath,
	List<Path> rootPaths,
	List<String> mixinConfigs,
	List<String> dependencyIds,
	Kind kind,
	String sourceFile
) {
	public enum Kind {
		/** A real mod jar the user dropped in {@code mods/}. */
		USER,
		/** A library jar nested inside another mod (jar-in-jar). */
		NESTED,
		/** Provided by the loader itself: {@code minecraft}, {@code java}, {@code fabricloader}. */
		BUILTIN,
		/** A module of the Fabric API. */
		FABRIC_API
	}

	/** Mods the player actually chose to install — what tab 1 lists by default. */
	public boolean isUserFacing() {
		return this.kind == Kind.USER;
	}

	public boolean hasMixins() {
		return !this.mixinConfigs.isEmpty();
	}

	public String displayName() {
		return this.name == null || this.name.isBlank() ? this.id : this.name;
	}
}
