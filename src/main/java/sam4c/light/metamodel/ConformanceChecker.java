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
        for (Connector c : arch.connectors()) {
            if (blank(c.name()))
                errors.add("Connector: name is required (M2: Connector.name [1..1])");
        }

        // M2: Architecture -> links [0..*] containment
        for (Link l : arch.links()) {
            if (blank(l.portRef()))
                errors.add("Link: portRef is required (M2: Link.portRef [1..1])");
            if (blank(l.connectorName()))
                errors.add("Link: connectorName is required (M2: Link.connectorName [1..1])");
        }

        return errors;
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

        // M2: Component -> ports [0..*] -- each port needs a name
        for (Port p : c.ports()) {
            if (blank(p.name()))
                errors.add(ctx + ": Port.name is required (M2: Port.name [1..1])");
        }

        // M2: verify type is a known concrete subclass of Component
        boolean knownType = metamodel.isA(c.type(), "Component")
                || c.type().equals("VM") || c.type().equals("App") || c.type().equals("Data");
        if (!knownType)
            errors.add(ctx + ": unknown type '" + c.type()
                    + "'. Must conform to M2 Component hierarchy.");

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
}
