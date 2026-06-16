package sam4c.light.output;

import sam4c.light.model.Architecture;
import sam4c.light.model.Component;
import sam4c.light.model.Connector;
import sam4c.light.model.Link;
import sam4c.light.model.Port;

import java.util.Map;

/**
 * Serializes an Architecture back to .arch.yaml text.
 *
 * The inverse of ArchLoader: ArchLoader reads YAML -> Architecture, this writes
 * Architecture -> YAML. Used by the web editor's "Download YAML" so a drawn
 * diagram round-trips into the same format the CLI consumes.
 */
public final class ArchYamlWriter {

    private ArchYamlWriter() {}

    public static String write(Architecture arch) {
        StringBuilder sb = new StringBuilder();
        sb.append("name: ").append(arch.name()).append("\n\n");

        sb.append("components:\n");
        for (Component c : arch.components()) writeComponent(sb, c, 1);
        sb.append("\n");

        sb.append("connectors:\n");
        if (arch.connectors().isEmpty()) sb.append("  []\n");
        for (Connector c : arch.connectors()) {
            sb.append("  - name: ").append(c.name()).append("\n");
            if (c.external())         sb.append("    external: true\n");
            if (c.protocol() != null) sb.append("    protocol: ").append(c.protocol()).append("\n");
        }
        sb.append("\n");

        sb.append("links:\n");
        if (arch.links().isEmpty()) sb.append("  []\n");
        for (Link l : arch.links()) {
            sb.append("  - port: ").append(l.portRef()).append("\n");
            sb.append("    connector: ").append(l.connectorName()).append("\n");
            if (l.direction() != sam4c.light.model.Direction.INOUT)
                sb.append("    direction: ").append(l.direction().token()).append("\n");
        }

        return sb.toString();
    }

    private static void writeComponent(StringBuilder sb, Component c, int depth) {
        String pad = "  ".repeat(depth);
        sb.append(pad).append("- name: ").append(c.name()).append("\n");
        sb.append(pad).append("  type: ").append(c.type()).append("\n");
        if (c.external()) sb.append(pad).append("  external: true\n");

        if (!c.attributes().isEmpty()) {
            sb.append(pad).append("  attributes:\n");
            for (Map.Entry<String, String> e : c.attributes().entrySet())
                sb.append(pad).append("    ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
        }

        if (!c.ports().isEmpty()) {
            boolean anyRich = c.ports().stream().anyMatch(p -> p.number() != null || p.protocol() != null);
            if (!anyRich) {
                // simple form: ports: [a, b]
                String ports = String.join(", ", c.ports().stream().map(Port::name).toList());
                sb.append(pad).append("  ports: [").append(ports).append("]\n");
            } else {
                // rich form: ports:\n  - { name: a, number: 80, protocol: http }
                sb.append(pad).append("  ports:\n");
                for (Port p : c.ports()) {
                    sb.append(pad).append("    - { name: ").append(p.name());
                    if (p.number() != null)   sb.append(", number: ").append(p.number());
                    if (p.protocol() != null) sb.append(", protocol: ").append(p.protocol());
                    sb.append(" }\n");
                }
            }
        }

        // Deployment properties from the properties map
        writeScalar(sb, pad, "image", c.properties().get("image"));
        writeScalar(sb, pad, "runtime", c.properties().get("runtime"));
        writeScalar(sb, pad, "exposure", c.properties().get("exposure"));
        writeScalar(sb, pad, "lifecycle", c.properties().get("lifecycle"));
        writeScalar(sb, pad, "schedule", c.properties().get("schedule"));
        writeScalar(sb, pad, "persistent", c.properties().get("persistent"));
        writeScalar(sb, pad, "deployedOn", c.properties().get("deployedOn"));
        writeNestedMap(sb, pad, "scale", c.properties().get("scale"));
        writeNestedMap(sb, pad, "resources", c.properties().get("resources"));
        writeNestedMap(sb, pad, "storage", c.properties().get("storage"));
        writeNestedMap(sb, pad, "config", c.properties().get("config"));
        writeNestedMap(sb, pad, "health", c.properties().get("health"));
        writeNestedMap(sb, pad, "trigger", c.properties().get("trigger"));
        writeNestedMap(sb, pad, "placement", c.properties().get("placement"));
        writeNestedMap(sb, pad, "capacity", c.properties().get("capacity"));
        writeList(sb, pad, "secrets", c.properties().get("secrets"));

        if (!c.children().isEmpty()) {
            sb.append(pad).append("  children:\n");
            for (Component child : c.children()) writeComponent(sb, child, depth + 2);
        }
    }

    /** Writes `key: value` if value is non-null. */
    private static void writeScalar(StringBuilder sb, String pad, String key, Object value) {
        if (value != null) sb.append(pad).append("  ").append(key).append(": ").append(value).append("\n");
    }

    /** Writes `key: [a, b]` if value is a non-empty list. */
    private static void writeList(StringBuilder sb, String pad, String key, Object value) {
        if (!(value instanceof java.util.List<?> l) || l.isEmpty()) return;
        sb.append(pad).append("  ").append(key).append(": [")
          .append(String.join(", ", l.stream().map(String::valueOf).toList())).append("]\n");
    }

    /** Writes a one-level nested map (e.g. scale, resources) under `key`, if present. */
    private static void writeNestedMap(StringBuilder sb, String pad, String key, Object value) {
        if (!(value instanceof Map<?, ?> m) || m.isEmpty()) return;
        sb.append(pad).append("  ").append(key).append(":\n");
        for (Map.Entry<?, ?> e : m.entrySet())
            sb.append(pad).append("    ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
    }
}
