package sam4c.light.web;

import sam4c.light.model.Architecture;
import sam4c.light.model.Component;
import sam4c.light.model.Connector;
import sam4c.light.model.Direction;
import sam4c.light.model.Link;
import sam4c.light.model.Port;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds an Architecture from a diagram posted by the web canvas.
 *
 * The inverse of GraphBuilder (which goes Architecture -> graph). The diagram is a
 * plain domain structure the canvas serializes to:
 *
 *   {
 *     "name": "model",
 *     "components": [ { "id","name","type","parent","attributes":{}, "ports":[] } ],
 *     "connectors": [ { "name","external" } ],
 *     "links":      [ { "port":"Comp.port", "connector":"ConnName" } ]
 *   }
 *
 * Components are reassembled into the nesting tree by their parent id.
 */
public final class DiagramReader {

    private DiagramReader() {}

    @SuppressWarnings("unchecked")
    public static Architecture build(Map<String, Object> diagram) {
        String name = (String) diagram.getOrDefault("name", "model");

        List<Map<String, Object>> rawComps =
            (List<Map<String, Object>>) diagram.getOrDefault("components", List.of());

        // Group child raw-DTOs by parent id (null parent = top level)
        Map<String, List<Map<String, Object>>> byParent = new LinkedHashMap<>();
        for (Map<String, Object> rc : rawComps) {
            Object p = rc.get("parent");
            String parent = (p == null) ? "" : p.toString();
            byParent.computeIfAbsent(parent, k -> new ArrayList<>()).add(rc);
        }

        List<Component> roots = buildChildren("", byParent);

        // Connectors
        List<Connector> connectors = new ArrayList<>();
        for (Map<String, Object> rc : (List<Map<String, Object>>) diagram.getOrDefault("connectors", List.of())) {
            connectors.add(new Connector((String) rc.get("name"), bool(rc.get("external"))));
        }

        // Links
        List<Link> links = new ArrayList<>();
        for (Map<String, Object> rl : (List<Map<String, Object>>) diagram.getOrDefault("links", List.of())) {
            Direction dir = Direction.parse((String) rl.get("direction"));
            links.add(new Link((String) rl.get("port"), (String) rl.get("connector"), dir));
        }

        return new Architecture(name, roots, connectors, links);
    }

    @SuppressWarnings("unchecked")
    private static List<Component> buildChildren(String parentId,
                                                 Map<String, List<Map<String, Object>>> byParent) {
        List<Component> result = new ArrayList<>();
        for (Map<String, Object> rc : byParent.getOrDefault(parentId, List.of())) {
            String id = String.valueOf(rc.get("id"));
            String cname = (String) rc.getOrDefault("name", id);
            String type = (String) rc.getOrDefault("type", "App");

            Map<String, String> attrs = new LinkedHashMap<>();
            Object rawAttrs = rc.get("attributes");
            if (rawAttrs instanceof Map<?, ?> m)
                m.forEach((k, v) -> { if (v != null) attrs.put(k.toString(), v.toString()); });

            List<Port> ports = new ArrayList<>();
            Object rawPorts = rc.get("ports");
            if (rawPorts instanceof List<?> l)
                for (Object pn : l) ports.add(new Port(pn.toString()));

            List<Component> children = buildChildren(id, byParent);

            result.add(new Component(cname, type, ports, children,
                    bool(rc.get("external")), attrs, Map.of()));
        }
        return result;
    }

    private static boolean bool(Object o) {
        return o instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(o));
    }
}
