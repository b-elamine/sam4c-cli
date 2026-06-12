package sam4c.light.output;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import sam4c.light.model.*;
import sam4c.light.model.rule.SecurityRule;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class ModelSerializer {

    private static final ObjectMapper JSON = buildMapper();

    private static ObjectMapper buildMapper() {
        ObjectMapper m = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);
        SimpleModule module = new SimpleModule();
        module.addSerializer(UnifiedModel.class, new UnifiedModelSerializer());
        m.registerModule(module);
        return m;
    }

    public static void write(UnifiedModel model, File output) throws IOException {
        JSON.writeValue(output, model);
    }

    public static String toJson(UnifiedModel model) throws IOException {
        return JSON.writeValueAsString(model);
    }

    // -------------------------------------------------------------------------
    // Custom serializer: writes the object graph in a clean, readable form.
    // Component references inside resolvedRules are written as just their names,
    // exactly as XMI uses object IDs for cross-references. The full Component
    // objects live in the architecture section.
    // -------------------------------------------------------------------------

    private static class UnifiedModelSerializer extends StdSerializer<UnifiedModel> {

        UnifiedModelSerializer() { super(UnifiedModel.class); }

        @Override
        public void serialize(UnifiedModel model, JsonGenerator g, SerializerProvider p)
                throws IOException {

            g.writeStartObject();

            // Full architecture -- the complete object graph
            g.writeFieldName("architecture");
            p.defaultSerializeValue(model.architecture(), g);

            // Full security model -- attributes, contexts, rules as parsed
            g.writeFieldName("security");
            p.defaultSerializeValue(model.security(), g);

            // Coverage: context name -> list of component names (cross-reference handles)
            g.writeFieldName("coverage");
            g.writeStartObject();
            for (Map.Entry<String, List<Component>> entry : model.coverage().entrySet()) {
                g.writeFieldName(entry.getKey());
                g.writeStartArray();
                for (Component c : entry.getValue()) g.writeString(c.name());
                g.writeEndArray();
            }
            g.writeEndObject();

            // Resolved rules: rule type + component name lists (not duplicated objects)
            g.writeFieldName("resolvedRules");
            g.writeStartArray();
            for (ResolvedRule rr : model.resolvedRules()) {
                g.writeStartObject();
                g.writeStringField("type", rr.rule().getClass().getSimpleName());
                writeComponentRefs(g, "sctx", rr.sctxComponents());
                if (!rr.tctxComponents().isEmpty())
                    writeComponentRefs(g, "tctx", rr.tctxComponents());
                if (!rr.actxComponents().isEmpty())
                    writeComponentRefs(g, "actx", rr.actxComponents());
                g.writeEndObject();
            }
            g.writeEndArray();

            if (!model.unresolved().isEmpty()) {
                g.writeFieldName("unresolved");
                p.defaultSerializeValue(model.unresolved(), g);
            }

            g.writeEndObject();
        }

        private void writeComponentRefs(JsonGenerator g, String field, List<Component> comps)
                throws IOException {
            g.writeFieldName(field);
            g.writeStartArray();
            for (Component c : comps) g.writeString(c.name());
            g.writeEndArray();
        }
    }
}
