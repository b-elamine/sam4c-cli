package sam4c.light.loader;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import sam4c.light.model.Architecture;
import sam4c.light.model.Component;
import sam4c.light.model.Connector;
import sam4c.light.model.Link;
import sam4c.light.registry.ComponentRegistry;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ArchLoader {

    private final ComponentRegistry registry;

    public ArchLoader(ComponentRegistry registry) {
        this.registry = registry;
    }

    @SuppressWarnings("unchecked")
    public Architecture load(File file) throws IOException {
        YAMLMapper mapper = new YAMLMapper();
        Map<String, Object> root = mapper.readValue(file, Map.class);

        String name = (String) root.getOrDefault("name", file.getName());

        List<Component> components = new ArrayList<>();
        Object rawComponents = root.get("components");
        if (rawComponents instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> yaml) {
                    components.add(registry.load((Map<String, Object>) yaml));
                }
            }
        }

        List<Connector> connectors = new ArrayList<>();
        Object rawConnectors = root.get("connectors");
        if (rawConnectors instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> yaml) {
                    Map<String, Object> m = (Map<String, Object>) yaml;
                    String cname = (String) m.get("name");
                    boolean external = ComponentRegistry.bool(m, "external");
                    connectors.add(new Connector(cname, external));
                }
            }
        }

        List<Link> links = new ArrayList<>();
        Object rawLinks = root.get("links");
        if (rawLinks instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> yaml) {
                    Map<String, Object> m = (Map<String, Object>) yaml;
                    String port = (String) m.get("port");
                    String connector = (String) m.get("connector");
                    if (port == null || connector == null)
                        throw new IOException("Each link requires 'port' and 'connector' fields");
                    links.add(new Link(port, connector));
                }
            }
        }

        return new Architecture(name, components, connectors, links);
    }
}
