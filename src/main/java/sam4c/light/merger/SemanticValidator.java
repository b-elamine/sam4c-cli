package sam4c.light.merger;

import sam4c.light.model.*;
import sam4c.light.model.rule.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Semantic validation of the MERGED model.
 *
 * Where ConformanceChecker asks "is the model well-formed against M2?", this
 * asks "is the merged model meaningful?" -- it inspects the resolved rules and
 * coverage to find rules and contexts that resolve to nothing.
 *
 * These are warnings, not conformance errors: a rule covering no components is
 * structurally valid, but it governs nothing in this architecture. That is
 * either a mistake (forgot to tag a component, typo in a value) or an
 * unsatisfied requirement (a policy declared ahead of the architecture).
 */
public class SemanticValidator {

    /** Returns a list of human-readable warnings. Empty list = clean. */
    public static List<String> validate(UnifiedModel model) {
        List<String> warnings = new ArrayList<>();

        // 1. Rules that resolve to no components on a required argument
        for (ResolvedRule rr : model.resolvedRules()) {
            String type = rr.rule().getClass().getSimpleName();

            if (rr.sctxComponents().isEmpty())
                warnings.add(type + ": sctx resolves to no components "
                        + "-- unused rule, or a missing/mistyped tag");

            // tctx is optional on Confidentiality/Integrity/Isolation, required on
            // Authentication. Only warn when tctx was actually specified in the rule.
            if (tctxSpecified(rr.rule()) && rr.tctxComponents().isEmpty())
                warnings.add(type + ": tctx was specified but resolves to no components");

            if (rr.rule() instanceof Authentication && rr.actxComponents().isEmpty())
                warnings.add(type + ": actx resolves to no components");
        }

        // 2. Named contexts that match no components at all
        for (Map.Entry<String, List<Component>> e : model.coverage().entrySet()) {
            if (e.getValue().isEmpty())
                warnings.add("context '" + e.getKey() + "' matches no components "
                        + "-- predicate satisfied by nothing in the architecture");
        }

        return warnings;
    }

    /** True if the rule has a tctx argument that was actually given (not null). */
    private static boolean tctxSpecified(SecurityRule rule) {
        return switch (rule) {
            case Confidentiality r -> r.tctx() != null;
            case Integrity r       -> r.tctx() != null;
            case Isolation r       -> r.tctx() != null;
            case Authentication r  -> r.tctx() != null;
            case Authorization r   -> true;   // resource maps to tctx and is always required
        };
    }
}
