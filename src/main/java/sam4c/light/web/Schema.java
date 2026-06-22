package sam4c.light.web;

import sam4c.light.metamodel.MAttribute;
import sam4c.light.metamodel.MClass;
import sam4c.light.metamodel.Sam4cMetamodel;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

// Per-type editable fields for the Studio form, read straight from the metamodel:
// each field's kind (string|enum|bool|int|map|list) plus any enum values. The form is
// generated from this, so a new metamodel field shows up with no Studio change.
// type/name are skipped; ports/children/deployedOn/attributes the Studio handles itself.
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
