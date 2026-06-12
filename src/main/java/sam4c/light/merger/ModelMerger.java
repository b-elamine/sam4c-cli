package sam4c.light.merger;

import sam4c.light.model.*;
import sam4c.light.model.ref.*;
import sam4c.light.model.rule.*;

import java.util.*;
import java.util.stream.Collectors;

public class ModelMerger {

    /**
     * Produces a UnifiedModel where every security rule holds direct Component
     * references -- not names, not IDs, but the actual Java objects from the
     * Architecture. This is the in-memory object graph: navigating a rule gives
     * you the full Component (ports, children, attributes) without any lookup.
     *
     * The JSON serialization uses names as cross-reference handles, exactly as
     * XMI uses object IDs. Generators work on this in-memory graph, not the JSON.
     */
    public static UnifiedModel merge(Architecture arch, SecurityModel sec) {
        List<Component> all = flattenComponents(arch.components());

        Map<String, List<Component>> coverage = buildCoverage(all, sec);

        List<String> unresolved = new ArrayList<>();
        List<ResolvedRule> resolvedRules = sec.rules().stream()
                .map(rule -> resolveRule(rule, coverage, all, unresolved))
                .collect(Collectors.toList());

        return new UnifiedModel(arch, sec, coverage, resolvedRules, unresolved);
    }

    // -------------------------------------------------------------------------
    // Coverage: context name -> actual Component objects that satisfy it
    // -------------------------------------------------------------------------

    private static Map<String, List<Component>> buildCoverage(List<Component> all,
                                                               SecurityModel sec) {
        Map<String, List<Component>> coverage = new LinkedHashMap<>();
        for (NamedContext ctx : sec.contexts()) {
            List<Component> matching = all.stream()
                    .filter(c -> allConditionsMatch(c, ctx.conditions(), all))
                    .collect(Collectors.toList());
            coverage.put(ctx.name(), matching);
        }
        return coverage;
    }

    private static boolean allConditionsMatch(Component c, List<Ref> conditions,
                                               List<Component> all) {
        for (Ref condition : conditions) {
            if (!refMatches(c, condition, all)) return false;
        }
        return true;
    }

    private static boolean refMatches(Component c, Ref ref, List<Component> all) {
        return switch (ref) {
            case ValuedAttrRef r -> r.value().equals(c.attributes().get(r.attribute()));
            case NamedRef r      -> c.name().equals(r.name());
            case ComposedRef r   -> r.conditions().stream()
                    .allMatch(cond -> refMatches(c, cond, all));
        };
    }

    // -------------------------------------------------------------------------
    // Rule resolution: attach actual Component objects to each rule
    // -------------------------------------------------------------------------

    private static ResolvedRule resolveRule(SecurityRule rule,
                                             Map<String, List<Component>> coverage,
                                             List<Component> all,
                                             List<String> unresolved) {
        return switch (rule) {
            case Confidentiality r -> new ResolvedRule(rule,
                    resolveRef(r.sctx(), coverage, all, unresolved),
                    resolveRef(r.tctx(), coverage, all, unresolved),
                    List.of());
            case Integrity r -> new ResolvedRule(rule,
                    resolveRef(r.sctx(), coverage, all, unresolved),
                    resolveRef(r.tctx(), coverage, all, unresolved),
                    List.of());
            case Isolation r -> new ResolvedRule(rule,
                    resolveRef(r.sctx(), coverage, all, unresolved),
                    resolveRef(r.tctx(), coverage, all, unresolved),
                    List.of());
            case Authentication r -> new ResolvedRule(rule,
                    resolveRef(r.sctx(), coverage, all, unresolved),
                    resolveRef(r.tctx(), coverage, all, unresolved),
                    resolveRef(r.actx(), coverage, all, unresolved));
        };
    }

    /**
     * Resolves a Ref to the actual Component objects it points to.
     *
     *   NamedRef("frontendCtx")          -> looks up coverage -> [Component(FrontendVM), Component(Nginx)]
     *   NamedRef("FrontendVM")           -> direct reference  -> [Component(FrontendVM)]
     *   ValuedAttrRef(Domain, frontend)  -> inline predicate  -> [Component(FrontendVM), Component(Nginx)]
     *   ComposedRef([...])               -> inline predicate  -> matching Component objects
     */
    private static List<Component> resolveRef(Ref ref,
                                               Map<String, List<Component>> coverage,
                                               List<Component> all,
                                               List<String> unresolved) {
        if (ref == null) return List.of();

        return switch (ref) {
            case NamedRef r -> {
                if (coverage.containsKey(r.name())) {
                    yield new ArrayList<>(coverage.get(r.name()));
                }
                Optional<Component> direct = all.stream()
                        .filter(c -> c.name().equals(r.name()))
                        .findFirst();
                if (direct.isPresent()) yield List.of(direct.get());
                if (!unresolved.contains(r.name())) unresolved.add(r.name());
                yield List.of();
            }
            case ValuedAttrRef r -> all.stream()
                    .filter(c -> r.value().equals(c.attributes().get(r.attribute())))
                    .collect(Collectors.toList());
            case ComposedRef r -> all.stream()
                    .filter(c -> r.conditions().stream()
                            .allMatch(cond -> refMatches(c, cond, all)))
                    .collect(Collectors.toList());
        };
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static List<Component> flattenComponents(List<Component> components) {
        List<Component> flat = new ArrayList<>();
        for (Component c : components) {
            flat.add(c);
            if (!c.children().isEmpty())
                flat.addAll(flattenComponents(c.children()));
        }
        return flat;
    }
}
