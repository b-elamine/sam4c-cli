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
            if (c.external()) sb.append("    external: true\n");
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
            String ports = String.join(", ", c.ports().stream().map(Port::name).toList());
            sb.append(pad).append("  ports: [").append(ports).append("]\n");
        }

        if (!c.children().isEmpty()) {
            sb.append(pad).append("  children:\n");
            for (Component child : c.children()) writeComponent(sb, child, depth + 2);
        }
    }
}
