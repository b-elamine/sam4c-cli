package sam4c.light.registry;

import sam4c.light.model.Component;

import java.util.Map;

/**
 * Implement this interface to add a new architecture component type.
 *
 * Example -- adding Container:
 *
 *   public class ContainerHandler implements ComponentTypeHandler {
 *       public String typeName() { return "Container"; }
 *       public Component load(String name, Map<String, Object> yaml, ComponentRegistry reg) {
 *           String image = (String) yaml.getOrDefault("image", "");
 *           return new Component(name, "Container", List.of(), List.of(), false,
 *                                Map.of("image", image));
 *       }
 *   }
 *
 * Then register it:
 *   ComponentRegistry registry = ComponentRegistry.withDefaults();
 *   registry.register(new ContainerHandler());
 */
public interface ComponentTypeHandler {
    String typeName();
    Component load(String name, Map<String, Object> yaml, ComponentRegistry registry);
}
