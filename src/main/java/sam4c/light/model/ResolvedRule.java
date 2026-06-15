package sam4c.light.model;

import sam4c.light.model.rule.SecurityRule;
import java.util.List;

/**
 * A security rule with its references fully resolved to the in-memory model.
 *
 *   sctx/tctx/actxComponents -- the actual Component objects each argument matched
 *   paths                    -- the concrete connector paths between sctx and tctx
 *                               (empty when the rule has no target side, or when
 *                               the two sides share no connector)
 *
 * Components answer "who is governed"; paths answer "over which network channel",
 * which is what an IaC generator needs to place a control (TLS on a connector,
 * a deny rule on a path, an auth gate on an ingress link).
 */
public record ResolvedRule(
        SecurityRule rule,
        List<Component> sctxComponents,
        List<Component> tctxComponents,
        List<Component> actxComponents,
        List<ResolvedPath> paths
) {}
