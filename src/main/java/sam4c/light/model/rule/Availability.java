package sam4c.light.model.rule;

import sam4c.light.model.ref.Ref;

// target must hold up to a resilience level (low|medium|high). Single-sided, no other end.
// checked against scale/spread; would map to min replicas + a disruption budget + zone spread.
public record Availability(Ref target, String level) implements SecurityRule {}
