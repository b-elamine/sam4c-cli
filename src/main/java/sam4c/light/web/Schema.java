package sam4c.light.web;

import sam4c.light.metamodel.MAttribute;
import sam4c.light.metamodel.MClass;
import sam4c.light.metamodel.Sam4cMetamodel;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Per-type editable-field schema, derived entirely from the M2 metamodel.
 *
 * For each concrete component type (and Connector), it lists the fields a user may edit,
 * with the kind of each (string | enum | bool | int | map | list) and any enum values.
 * The Studio renders its properties form generically from this, so adding a field to the
 * metamodel automatically makes it editable in the Studio -- no Studio code change.
 *
 * `type` is excluded (fixed by the palette choice). `ports`/`children`/`deployedOn` and the
 * free-form `attributes` are handled by the Studio's own structural sections, not here.
 */
public final class Schema {

    private Schema() {}

    public static String json() {
        var mm = Sam4cMetamodel.INSTANCE;
        List<String> entries = new ArrayList<>();

        for (MClass cls : Sam4cMetamodel.ARCH.classes()) {
            if (cls.abstractClass()) continue;
            String type = cls.name();
            boolean isComponent = mm.isA(type, "Component");
            if (!isComponent && !type.equals("Connector")) continue;

            List<String> fields = new ArrayList<>();
            for (MAttribute a : mm.allAttributes(type)) {
                // `type` is fixed by the palette choice; `name` is rendered by the Studio's
                // own structural section. Both are handled outside the generic field loop.
                if (a.name().equals("type") || a.name().equals("name")) continue;
                fields.add(fieldJson(a));
            }
            entries.add("\"" + type + "\": [" + String.join(", ", fields) + "]");
        }
        return "{ " + String.join(", ", entries) + " }";
    }

    private static String fieldJson(MAttribute a) {
        String kind = switch (a.type()) {
            case BOOLEAN -> "bool";
            case INT     -> "int";
            case MAP     -> "map";
            case LIST    -> "list";
            case STRING  -> a.isEnum() ? "enum" : "string";
        };
        String allowed = a.allowed().stream().map(v -> "\"" + v + "\"").collect(Collectors.joining(", "));
        return "{ \"name\":\"" + a.name() + "\", \"kind\":\"" + kind + "\""
                + (a.isEnum() ? ", \"allowed\":[" + allowed + "]" : "")
                + (a.required() ? ", \"required\":true" : "") + " }";
    }
}
