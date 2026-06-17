package sam4c.light.model.rule;

import sam4c.light.model.ref.Ref;

/**
 * Availability: the {@code target} context must meet a minimum resilience {@code level}.
 *
 * Unlike the directional rules (sctx -> tctx), Availability constrains a single context:
 * there is no "other side". {@code level} is one of high | medium | low. It reads the
 * architecture's scale/placement and drives generator output such as a minimum replica
 * count, a PodDisruptionBudget, or multi-zone spreading.
 *
 * Example: Availability(backendCtx, high).
 */
public record Availability(Ref target, String level) implements SecurityRule {}
