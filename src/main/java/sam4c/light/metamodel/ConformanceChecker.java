package sam4c.light.metamodel;

import sam4c.light.model.*;
import sam4c.light.model.ref.*;
import sam4c.light.model.rule.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates M1 model instances against the M2 Sam4cMetamodel.
 *
 * Every check here corresponds to a constraint declared in Sam4cMetamodel:
 * required attributes (lowerBound=1), required references (lowerBound=1),
 * multiplicity (lowerBound=2 on ComposedRef.conditions), and type validity.
 *
 * This is what EMF does automatically via its EValidator framework.
 * Here it is explicit so every rule is traceable to its M2 source.
 */
public class ConformanceChecker {

    private final MPackage metamodel;

    public ConformanceChecker(MPackage metamodel) {
        this.metamodel = metamodel;
    }

    public List<String> check(Architecture arch) {
        List<String> errors = new ArrayList<>();

        // M2: Architecture.name [0..1] STRING -- optional, no error
        // M2: Architecture -> components [0..*] containment
        for (Component c : arch.components())
            errors.addAll(checkComponent(c, "Architecture.components"));

        // M2: Architecture -> connectors [0..*] containment
        java.util.Set<String> connectorNames = new java.util.HashSet<>();
        for (Connector c : arch.connectors()) {
            if (blank(c.name()))
                errors.add("Connector: name is required (M2: Connector.name [1..1])");
            else connectorNames.add(c.name());
            if (c.protocol() != null
                    && !java.util.Set.of("tcp", "udp", "http", "grpc").contains(c.protocol().toLowerCase()))
                errors.add("Connector '" + c.name() + "': protocol '" + c.protocol()
                        + "' invalid (M2: Connector.protocol ∈ tcp|udp|http|grpc)");
        }

        // Referential integrity: collect every component name and its ports so we
        // can verify links point at things that actually exist. A dangling link
        // (typo'd port or connector) would otherwise survive to generation and
        // produce a broken or phantom deployment artifact.
        java.util.Map<String, java.util.Set<String>> componentPorts = new java.util.HashMap<>();
        collectPorts(arch.components(), componentPorts);

        // M2: Architecture -> links [0..*] containment + referential integrity
        for (Link l : arch.links()) {
            if (blank(l.portRef())) {
                errors.add("Link: portRef is required (M2: Link.portRef [1..1])");
            } else {
                // portRef is "Component.port" (or a bare "Component")
                int dot = l.portRef().indexOf('.');
                String comp = dot < 0 ? l.portRef() : l.portRef().substring(0, dot);
                String port = dot < 0 ? null : l.portRef().substring(dot + 1);
                if (!componentPorts.containsKey(comp))
                    errors.add("Link.portRef '" + l.portRef()
                            + "' references unknown component '" + comp + "'");
                else if (port != null && !componentPorts.get(comp).contains(port))
                    errors.add("Link.portRef '" + l.portRef()
                            + "': component '" + comp + "' has no port '" + port + "'");
            }

            if (blank(l.connectorName()))
                errors.add("Link: connectorName is required (M2: Link.connectorName [1..1])");
            else if (!connectorNames.contains(l.connectorName()))
                errors.add("Link.connectorName '" + l.connectorName()
                        + "' is not a declared connector");
        }

        // Workload.deployedOn must reference an existing Host-typed component
        java.util.Map<String, String> componentTypes = new java.util.HashMap<>();
        collectTypes(arch.components(), componentTypes);
        java.util.Set<String> hostNames = new java.util.HashSet<>();
        componentTypes.forEach((n, t) -> { if (metamodel.isA(t, "Host")) hostNames.add(n); });
        checkDeployedOn(arch.components(), hostNames, errors);

        return errors;
    }

    /** Recursively collect each component's name -> set of its port names. */
    private void collectPorts(List<Component> components,
                              java.util.Map<String, java.util.Set<String>> out) {
        for (Component c : components) {
            java.util.Set<String> ports = new java.util.HashSet<>();
            for (Port p : c.ports()) ports.add(p.name());
            out.put(c.name(), ports);
            if (!c.children().isEmpty()) collectPorts(c.children(), out);
        }
    }

    /** Recursively collect each component's name -> type. */
    private void collectTypes(List<Component> components, java.util.Map<String, String> out) {
        for (Component c : components) {
            out.put(c.name(), c.type());
            if (!c.children().isEmpty()) collectTypes(c.children(), out);
        }
    }

    /** Recursively verify `deployedOn` (in the properties map) points at a Host. */
    private void checkDeployedOn(List<Component> components,
                                 java.util.Set<String> hostNames, List<String> errors) {
        for (Component c : components) {
            Object target = c.properties().get("deployedOn");
            if (target != null && !hostNames.contains(target.toString()))
                errors.add("Component(" + c.name() + "): deployedOn '" + target
                        + "' is not a Host (M2: Workload.deployedOn -> Host)");
            if (!c.children().isEmpty()) checkDeployedOn(c.children(), hostNames, errors);
        }
    }

    public List<String> check(SecurityModel sec) {
        List<String> errors = new ArrayList<>();

        // M2: AttributeType.name [1..1]
        for (AttributeType a : sec.attributes()) {
            if (blank(a.name()))
                errors.add("AttributeType: name is required (M2: AttributeType.name [1..1])");
        }

        // M2: NamedContext.name [1..1], NamedContext -> conditions [1..*]
        for (NamedContext ctx : sec.contexts()) {
            if (blank(ctx.name()))
                errors.add("NamedContext: name is required (M2: NamedContext.name [1..1])");
            if (ctx.conditions().isEmpty())
                errors.add("NamedContext '" + ctx.name()
                        + "': at least one condition required (M2: NamedContext.conditions [1..*])");
            for (Ref r : ctx.conditions())
                errors.addAll(checkRef(r, "NamedContext(" + ctx.name() + ").conditions"));
        }

        // M2: SecurityRule subclass constraints
        for (SecurityRule rule : sec.rules())
            errors.addAll(checkRule(rule));

        return errors;
    }

    // -------------------------------------------------------------------------
    // Component
    // -------------------------------------------------------------------------

    private List<String> checkComponent(Component c, String location) {
        List<String> errors = new ArrayList<>();
        String ctx = location + " > Component(" + c.name() + ")";

        // M2: Component.name [0..1] -- optional, skip
        // M2: Component.type [1..1]
        if (blank(c.type()))
            errors.add(ctx + ": type is required (M2: Component.type [1..1])");

        // M2: Component -> ports [0..*] -- each port needs a name; protocol from allowed set
        for (Port p : c.ports()) {
            if (blank(p.name()))
                errors.add(ctx + ": Port.name is required (M2: Port.name [1..1])");
            if (p.protocol() != null
                    && !java.util.Set.of("tcp", "udp", "http", "grpc").contains(p.protocol().toLowerCase()))
                errors.add(ctx + ": Port '" + p.name() + "' protocol '" + p.protocol()
                        + "' invalid (M2: Port.protocol ∈ tcp|udp|http|grpc)");
            if (p.number() != null && (p.number() < 1 || p.number() > 65535))
                errors.add(ctx + ": Port '" + p.name() + "' number " + p.number()
                        + " out of range (1..65535)");
        }

        // M2: Workload features (carried in the properties map) -- value sets
        Object runtime = c.properties().get("runtime");
        if (runtime != null
                && !java.util.Set.of("container", "process", "function").contains(runtime.toString().toLowerCase()))
            errors.add(ctx + ": runtime '" + runtime + "' invalid (M2: Workload.runtime ∈ container|process|function)");
        Object exposure = c.properties().get("exposure");
        if (exposure != null
                && !java.util.Set.of("none", "internal", "external").contains(exposure.toString().toLowerCase()))
            errors.add(ctx + ": exposure '" + exposure + "' invalid (M2: Workload.exposure ∈ none|internal|external)");
        Object lifecycle = c.properties().get("lifecycle");
        if (lifecycle != null
                && !java.util.Set.of("continuous", "batch", "scheduled").contains(lifecycle.toString().toLowerCase()))
            errors.add(ctx + ": lifecycle '" + lifecycle + "' invalid (M2: Workload.lifecycle ∈ continuous|batch|scheduled)");
        Object trigger = c.properties().get("trigger");
        if (trigger instanceof java.util.Map<?, ?> tm && tm.get("kind") != null
                && !java.util.Set.of("http", "event", "schedule").contains(tm.get("kind").toString().toLowerCase()))
            errors.add(ctx + ": trigger.kind '" + tm.get("kind") + "' invalid (∈ http|event|schedule)");

        // Well-formedness invariants (C4)
        // (6) an exposed workload must have at least one port
        if (exposure != null && !exposure.toString().equalsIgnoreCase("none") && c.ports().isEmpty())
            errors.add(ctx + ": exposure '" + exposure + "' but the workload has no ports");
        // (7) a persistent Data workload must declare storage
        if (Boolean.TRUE.equals(c.properties().get("persistent")) && !(c.properties().get("storage") instanceof java.util.Map))
            errors.add(ctx + ": persistent=true but no storage declared");
        // (8) scale: min <= replicas <= max
        if (c.properties().get("scale") instanceof java.util.Map<?, ?> sc) {
            Integer min = asInt(sc.get("min")), rep = asInt(sc.get("replicas")), max = asInt(sc.get("max"));
            if (min != null && max != null && min > max)
                errors.add(ctx + ": scale.min (" + min + ") > scale.max (" + max + ")");
            if (rep != null && min != null && rep < min)
                errors.add(ctx + ": scale.replicas (" + rep + ") < scale.min (" + min + ")");
            if (rep != null && max != null && rep > max)
                errors.add(ctx + ": scale.replicas (" + rep + ") > scale.max (" + max + ")");
        }

        // M2: verify type is a known concrete subclass of Component
        boolean knownType = metamodel.isA(c.type(), "Component");
        if (!knownType)
            errors.add(ctx + ": unknown type '" + c.type()
                    + "'. Must conform to M2 Component hierarchy.");

        // Well-formedness: Group containment rules
        for (Component child : c.children()) {
            if (c.type().equals("CoLocationGroup") && !metamodel.isA(child.type(), "Workload"))
                errors.add(ctx + ": CoLocationGroup may contain only Workloads, not '"
                        + child.name() + "' (" + child.type() + ")");
            // (9) the deployable unit is the group: a member must not carry its own scale
            if (c.type().equals("CoLocationGroup") && child.properties().get("scale") != null)
                errors.add(ctx + ": member '" + child.name() + "' has its own scale; "
                        + "scale belongs to the CoLocationGroup");
            if (c.type().equals("HostPool") && !metamodel.isA(child.type(), "Host"))
                errors.add(ctx + ": HostPool may contain only Hosts, not '"
                        + child.name() + "' (" + child.type() + ")");
            if (c.type().equals("Zone") && metamodel.isA(child.type(), "Host"))
                errors.add(ctx + ": Zone may not directly contain a Host ('"
                        + child.name() + "')");
        }

        // M2: Component -> children [0..*] containment -- recurse
        for (Component child : c.children())
            errors.addAll(checkComponent(child, ctx + ".children"));

        return errors;
    }

    // -------------------------------------------------------------------------
    // Security rules
    // -------------------------------------------------------------------------

    private List<String> checkRule(SecurityRule rule) {
        List<String> errors = new ArrayList<>();
        switch (rule) {

            // M2: Confidentiality.sctx [1..1], tctx [0..1]
            case Confidentiality r -> {
                if (r.sctx() == null)
                    errors.add("Confidentiality: sctx is required (M2: Confidentiality.sctx [1..1])");
                else errors.addAll(checkRef(r.sctx(), "Confidentiality.sctx"));
                if (r.tctx() != null)
                    errors.addAll(checkRef(r.tctx(), "Confidentiality.tctx"));
            }

            // M2: Integrity.sctx [1..1], tctx [0..1]
            case Integrity r -> {
                if (r.sctx() == null)
                    errors.add("Integrity: sctx is required (M2: Integrity.sctx [1..1])");
                else errors.addAll(checkRef(r.sctx(), "Integrity.sctx"));
                if (r.tctx() != null)
                    errors.addAll(checkRef(r.tctx(), "Integrity.tctx"));
            }

            // M2: Isolation.sctx [1..1], tctx [0..1]
            case Isolation r -> {
                if (r.sctx() == null)
                    errors.add("Isolation: sctx is required (M2: Isolation.sctx [1..1])");
                else errors.addAll(checkRef(r.sctx(), "Isolation.sctx"));
                if (r.tctx() != null)
                    errors.addAll(checkRef(r.tctx(), "Isolation.tctx"));
            }

            // M2: Authentication.sctx [1..1], actx [1..1], tctx [1..1]
            case Authentication r -> {
                if (r.sctx() == null)
                    errors.add("Authentication: sctx is required (M2: Authentication.sctx [1..1])");
                else errors.addAll(checkRef(r.sctx(), "Authentication.sctx"));
                if (r.actx() == null)
                    errors.add("Authentication: actx is required (M2: Authentication.actx [1..1])");
                else errors.addAll(checkRef(r.actx(), "Authentication.actx"));
                if (r.tctx() == null)
                    errors.add("Authentication: tctx is required (M2: Authentication.tctx [1..1])");
                else errors.addAll(checkRef(r.tctx(), "Authentication.tctx"));
            }
        }
        return errors;
    }

    // -------------------------------------------------------------------------
    // Refs
    // -------------------------------------------------------------------------

    private List<String> checkRef(Ref ref, String location) {
        List<String> errors = new ArrayList<>();
        switch (ref) {

            // M2: NamedRef.name [1..1]
            case NamedRef r -> {
                if (blank(r.name()))
                    errors.add(location + ": NamedRef.name is required (M2: NamedRef.name [1..1])");
            }

            // M2: ValuedAttrRef.attribute [1..1], ValuedAttrRef.value [1..1]
            case ValuedAttrRef r -> {
                if (blank(r.attribute()))
                    errors.add(location + ": ValuedAttrRef.attribute is required");
                if (blank(r.value()))
                    errors.add(location + ": ValuedAttrRef.value is required");
            }

            // M2: ComposedRef.conditions [2..*]
            case ComposedRef r -> {
                if (r.conditions().size() < 2)
                    errors.add(location + ": ComposedRef requires at least 2 conditions "
                            + "(M2: ComposedRef.conditions [2..*])");
                for (Ref inner : r.conditions())
                    errors.addAll(checkRef(inner, location + ".conditions"));
            }
        }
        return errors;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static Integer asInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        try { return o == null ? null : Integer.valueOf(o.toString()); }
        catch (NumberFormatException e) { return null; }
    }
}
