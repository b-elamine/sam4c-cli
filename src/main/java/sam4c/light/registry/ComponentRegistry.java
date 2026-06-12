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
                        bool(yaml, "external"), loadAttributes(yaml), Map.of());
            }
        });

        r.register(new ComponentTypeHandler() {
            public String typeName() { return "Data"; }
            public Component load(String name, Map<String, Object> yaml, ComponentRegistry reg) {
                return new Component(name, "Data", loadPorts(yaml), List.of(),
                        bool(yaml, "external"), loadAttributes(yaml), Map.of());
            }
        });

        r.register(new ComponentTypeHandler() {
            public String typeName() { return "VM"; }
            public Component load(String name, Map<String, Object> yaml, ComponentRegistry reg) {
                List<Component> children = new ArrayList<>();
                Object raw = yaml.get("children");
                if (raw instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> childYaml) {
                            children.add(reg.load((Map<String, Object>) childYaml));
                        }
                    }
                }
                return new Component(name, "VM", List.of(), children,
                        bool(yaml, "external"), loadAttributes(yaml), Map.of());
            }
        });

        return r;
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
        for (Object p : (List<?>) raw) ports.add(new Port(p.toString()));
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
}
