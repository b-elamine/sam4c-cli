package sam4c.light.model;

import sam4c.light.model.rule.SecurityRule;
import java.util.List;

public record ResolvedRule(
        SecurityRule rule,
        List<Component> sctxComponents,
        List<Component> tctxComponents,
        List<Component> actxComponents
) {}
