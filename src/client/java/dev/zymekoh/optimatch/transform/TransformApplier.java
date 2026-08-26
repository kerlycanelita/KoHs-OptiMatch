package dev.zymekoh.optimatch.transform;

import dev.zymekoh.optimatch.OptiMatchClient;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Writes a plan to disk.
 *
 * <p>Every change lands in the file the owning mod already reads at startup, so it takes effect on
 * the next launch and not before. Nothing here touches Mixin at runtime: a measurement on a live
 * instance showed that emptying an already-prepared mixin config leaves its injections applied
 * anyway, so the only honest way to change what a mixin does is to ask its mod, in writing, before
 * the game starts.
 */
public final class TransformApplier {
	/** Enough time for the animation to read as work rather than a flicker. */
	public static final long MIN_MILLIS = 3000L;

	public record Result(List<String> applied, List<String> failed) {
		public boolean ok() {
			return this.failed.isEmpty();
		}

		public int total() {
			return this.applied.size() + this.failed.size();
		}
	}

	private TransformApplier() {
	}

	/**
	 * Applies the plan off the render thread, never returning sooner than {@link #MIN_MILLIS}.
	 *
	 * <p>The floor is deliberate. Writing three lines to a properties file is instant, and a screen
	 * that flashes and says "done" reads as though nothing happened. Holding the animation for the
	 * same three seconds the selector uses on startup makes the change feel like the deliberate act
	 * it is, and gives the summary time to be read.
	 */
	public static CompletableFuture<Result> apply(TransformPlan plan) {
		long startedAt = System.currentTimeMillis();
		return CompletableFuture.supplyAsync(() -> {
			List<String> applied = new ArrayList<>();
			List<String> failed = new ArrayList<>();

			for (TransformPlan.Change change : plan.applicable()) {
				try {
					if (plan.preset().restoresDefaults()) {
						KnobStore.reset(change.knob());
					} else {
						KnobStore.write(change.knob(), change.to());
					}
					applied.add(change.knob().label());
				} catch (Exception e) {
					OptiMatchClient.LOGGER.warn("Could not write {}", change.knob().key(), e);
					failed.add(change.knob().label() + " — " + e.getMessage());
				}
			}

			long elapsed = System.currentTimeMillis() - startedAt;
			if (elapsed < MIN_MILLIS) {
				try {
					Thread.sleep(MIN_MILLIS - elapsed);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}

			KnobRegistry.invalidate();
			return new Result(List.copyOf(applied), List.copyOf(failed));
		});
	}
}
