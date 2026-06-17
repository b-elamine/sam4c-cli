package sam4c.light.merger;

import sam4c.light.model.*;
import sam4c.light.model.rule.*;

import java.util.ArrayList;
import java.util.HashMap;
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

        // 3. Availability satisfiability: a medium/high requirement must be backed by the
        // architecture, or it is not actually delivered.
        //   medium -> redundancy: >= 2 effective copies
        //   high   -> redundancy AND spread across zones (survives a whole-zone outage)
        Map<String, Integer> replicas = effectiveReplicas(model.architecture());
        Map<String, String>  spread   = effectiveSpread(model.architecture());
        for (ResolvedRule rr : model.resolvedRules()) {
            if (!(rr.rule() instanceof Availability av)) continue;
            String level = av.level() == null ? "" : av.level().toLowerCase();
            if (level.equals("low")) continue;   // low = no availability requirement
            for (Component c : rr.sctxComponents()) {
                if (!isWorkload(c)) continue;     // only workloads run as replicated copies
                Integer eff = replicas.get(c.name());
                int n = (eff == null) ? 1 : eff;  // no scale declared anywhere = a single copy
                if (n < 2)
                    warnings.add("Availability=" + level + " but '" + c.name() + "' has "
                            + n + " copy" + (n == 1 ? "" : "ies") + " (needs >= 2) "
                            + "-- single point of failure, requirement not satisfied");
                if (level.equals("high") && !"zone".equalsIgnoreCase(spread.get(c.name())))
                    warnings.add("Availability=high but '" + c.name() + "' is not spread across zones "
                            + "(set spread: zone on the workload or its Colocation) "
                            + "-- a whole-zone outage would take it down");
            }
        }

        return warnings;
    }

    private static boolean isWorkload(Component c) {
        return c.type().equals("App") || c.type().equals("Data");
    }

    /**
     * Effective replica count per component name: a workload's own scale.replicas, or the
     * replicas of its enclosing Colocation (scale attaches to the deployable unit, R-F5).
     * Null = no scale declared anywhere (treated as a single copy by the caller).
     */
    private static Map<String, Integer> effectiveReplicas(Architecture arch) {
        Map<String, Integer> out = new HashMap<>();
        walkReplicas(arch.components(), null, out);
        return out;
    }

    private static void walkReplicas(List<Component> comps, Integer enclosingColo,
                                     Map<String, Integer> out) {
        for (Component c : comps) {
            Integer own = replicasOf(c);
            out.put(c.name(), own != null ? own : enclosingColo);
            // a Colocation scales as one unit -> its members inherit its replica count
            Integer childCtx = c.type().equals("Colocation") && own != null ? own : enclosingColo;
            walkReplicas(c.children(), childCtx, out);
        }
    }

    private static Integer replicasOf(Component c) {
        if (c.properties().get("scale") instanceof Map<?, ?> sm) {
            Object r = sm.get("replicas");
            if (r instanceof Number n) return n.intValue();
            if (r != null) try { return Integer.valueOf(r.toString()); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    /**
     * Effective spread per component name: a workload's own `spread`, or that of its
     * enclosing Colocation (the spread, like scale, attaches to the deployable unit).
     * Null = none declared.
     */
    private static Map<String, String> effectiveSpread(Architecture arch) {
        Map<String, String> out = new HashMap<>();
        walkSpread(arch.components(), null, out);
        return out;
    }

    private static void walkSpread(List<Component> comps, String enclosingColo,
                                   Map<String, String> out) {
        for (Component c : comps) {
            String own = spreadOf(c);
            out.put(c.name(), own != null ? own : enclosingColo);
            String childCtx = c.type().equals("Colocation") && own != null ? own : enclosingColo;
            walkSpread(c.children(), childCtx, out);
        }
    }

    private static String spreadOf(Component c) {
        Object s = c.properties().get("spread");
        return s == null ? null : s.toString();
    }

    /** True if the rule has a tctx argument that was actually given (not null). */
    private static boolean tctxSpecified(SecurityRule rule) {
        return switch (rule) {
            case Confidentiality r -> r.tctx() != null;
            case Integrity r       -> r.tctx() != null;
            case Isolation r       -> r.tctx() != null;
            case Authentication r  -> r.tctx() != null;
            case Authorization r   -> true;   // resource maps to tctx and is always required
            case Availability r    -> false;  // single context (target -> sctx); no tctx side
        };
    }
}
