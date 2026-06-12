package sam4c.light.model;

import java.util.List;
import java.util.Map;

public record UnifiedModel(
        Architecture architecture,
        SecurityModel security,
        Map<String, List<Component>> coverage,
        List<ResolvedRule> resolvedRules,
        List<String> unresolved
) {}
