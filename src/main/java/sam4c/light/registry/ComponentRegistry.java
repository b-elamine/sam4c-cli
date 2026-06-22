package sam4c.light.registry;

import sam4c.light.model.Component;
import sam4c.light.model.Port;

import java.util.*;

public class ComponentRegistry {

    private final Map<String, ComponentTypeHandler> handlers = new LinkedHashMap<>();

    @SuppressWarnings("unchecked")
    public static ComponentRegistry withDefaults() {
        ComponentRegistry r = new ComponentRegistry();

        r.register(new ComponentTypeHandler() {
            public String typeName() { return "App"; }
            public Component load(String name, Map<String, Object> yaml, ComponentRegistry reg) {
                return new Component(name, "App", loadPorts(yaml), List.of(),
                        bool(yaml, "external"), loadAttributes(yaml), loadProperties(yaml));
            }
        });

        r.register(new ComponentTypeHandler() {
            public String typeName() { return "Data"; }
            public Component load(String name, Map<String, Object> yaml, ComponentRegistry reg) {
                return new Component(name, "Data", loadPorts(yaml), List.of(),
                        bool(yaml, "external"), loadAttributes(yaml), loadProperties(yaml));
            }
        });

        // Container types -- hosts (VM/PM/Worker) and groups
        // (Zone/Colocation/HostPool) all load the same way: they hold child components
        // via the children tree. Their distinguishing attrs ride in the properties map.
        for (String containerType : new String[]{
                "VM", "PM", "Worker", "Zone", "Colocation", "HostPool"}) {
            final String t = containerType;
            r.register(new ComponentTypeHandler() {
                public String typeName() { return t; }
                public Component load(String name, Map<String, Object> yaml, ComponentRegistry reg) {
                    return new Component(name, t, List.of(), loadChildren(yaml, reg),
                            bool(yaml, "external"), loadAttributes(yaml), loadProperties(yaml));
                }
            });
        }

        return r;
    }

    @SuppressWarnings("unchecked")
    private static List<Component> loadChildren(Map<String, Object> yaml, ComponentRegistry reg) {
        List<Component> children = new ArrayList<>();
        Object raw = yaml.get("children");
        if (raw instanceof List<?> list) {
            for (Object item : list)
                if (item instanceof Map<?, ?> childYaml)
                    children.add(reg.load((Map<String, Object>) childYaml));
        }
        return children;
    }

    public void register(ComponentTypeHandler handler) {
        handlers.put(handler.typeName(), handler);
    }

    @SuppressWarnings("unchecked")
    public Component load(Map<String, Object> yaml) {
        String name = (String) yaml.get("name");
        String type = (String) yaml.getOrDefault("type", "App");
        ComponentTypeHandler handler = handlers.get(type);
        if (handler == null)
            throw new IllegalArgumentException(
                    "Unknown component type '" + type + "'. Known: " + handlers.keySet());
        return handler.load(name, yaml, this);
    }

    public Set<String> types() {
        return Collections.unmodifiableSet(handlers.keySet());
    }

    @SuppressWarnings("unchecked")
    public static List<Port> loadPorts(Map<String, Object> yaml) {
        Object raw = yaml.get("ports");
        if (raw == null) return List.of();
        List<Port> ports = new ArrayList<>();
        for (Object p : (List<?>) raw) {
            if (p instanceof Map<?, ?> m) {
                // rich form: { name: http_in, number: 8080, protocol: http }
                Map<String, Object> pm = (Map<String, Object>) m;
                String pname = String.valueOf(pm.get("name"));
                Integer number = pm.get("number") instanceof Number n ? n.intValue() : null;
                String protocol = pm.get("protocol") != null ? pm.get("protocol").toString() : null;
                ports.add(new Port(pname, number, protocol));
            } else {
                // simple form: just the name
                ports.add(new Port(p.toString()));
            }
        }
        return ports;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> loadAttributes(Map<String, Object> yaml) {
        Object raw = yaml.get("attributes");
        if (raw == null) return Map.of();
        Map<String, Object> rawMap = (Map<String, Object>) raw;
        Map<String, String> result = new LinkedHashMap<>();
        rawMap.forEach((k, v) -> result.put(k, v.toString()));
        return result;
    }

    public static boolean bool(Map<String, Object> yaml, String key) {
        Object v = yaml.get(key);
        return v instanceof Boolean b && b;
    }

    // reads the deployment fields into the untyped properties map. add a key here when a
    // new field is declared on the metamodel.
    public static Map<String, Object> loadProperties(Map<String, Object> yaml) {
        Map<String, Object> props = new LinkedHashMap<>();
        // workload deployment properties
        if (yaml.get("image") != null)      props.put("image", yaml.get("image").toString());
        if (yaml.get("runtime") != null)    props.put("runtime", yaml.get("runtime").toString());
        if (yaml.get("exposure") != null)   props.put("exposure", yaml.get("exposure").toString());
        if (yaml.get("deployedOn") != null) props.put("deployedOn", yaml.get("deployedOn").toString());
        if (yaml.get("scale") instanceof Map<?, ?> s)     props.put("scale", s);
        if (yaml.get("resources") instanceof Map<?, ?> r) props.put("resources", r);
        if (yaml.get("lifecycle") != null)  props.put("lifecycle", yaml.get("lifecycle").toString());
        if (yaml.get("schedule") != null)   props.put("schedule", yaml.get("schedule").toString());
        if (yaml.get("persistent") != null) props.put("persistent", yaml.get("persistent"));
        if (yaml.get("storage") instanceof Map<?, ?> st)   props.put("storage", st);
        if (yaml.get("config") instanceof Map<?, ?> cfg)   props.put("config", cfg);
        if (yaml.get("secrets") instanceof List<?> sec)    props.put("secrets", sec);
        if (yaml.get("health") instanceof Map<?, ?> h)     props.put("health", h);
        if (yaml.get("trigger") instanceof Map<?, ?> tg)   props.put("trigger", tg);
        if (yaml.get("placement") instanceof Map<?, ?> pl) props.put("placement", pl);
        if (yaml.get("spread") != null)     props.put("spread", yaml.get("spread").toString());
        // host property
        if (yaml.get("capacity") instanceof Map<?, ?> cap) props.put("capacity", cap);
        // group properties
        if (yaml.get("boundary") != null)     props.put("boundary", yaml.get("boundary").toString());
        if (yaml.get("shareNetwork") != null) props.put("shareNetwork", yaml.get("shareNetwork"));
        if (yaml.get("shareStorage") != null) props.put("shareStorage", yaml.get("shareStorage"));
        return props;
    }
}
