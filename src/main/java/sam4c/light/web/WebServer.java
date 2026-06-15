package sam4c.light.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import sam4c.light.model.Architecture;
import sam4c.light.output.ArchYamlWriter;
import sam4c.light.output.GraphBuilder;
import sam4c.light.output.GraphData;
import sam4c.light.output.SecDslScaffolder;
import sam4c.light.pipeline.Pipeline;
import sam4c.light.pipeline.PipelineResult;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Embedded web server for SAM4C Studio (--serve), using the JDK's HttpServer.
 *
 * A thin transport layer over the shared engine. Routes:
 *   GET  /                      the Studio SPA (design / files / result)
 *   GET  /api/palette           component palette derived from the metamodel
 *   POST /api/diagram-to-yaml   { diagram } -> { yaml }  (blocks on missing required attrs)
 *   POST /api/diagram-to-secdsl { diagram } -> { secdsl }
 *   POST /api/merge             { arch, rules } -> { nodes, edges, coverage, warnings, conformanceErrors }
 *
 * Intended for localhost use; no auth or rate limiting.
 */
public final class WebServer {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final Pipeline pipeline = new Pipeline();

    public void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this::handleRoot);
        server.createContext("/api/palette", this::handlePalette);
        server.createContext("/api/diagram-to-yaml", ex -> handleDiagram(ex, true));
        server.createContext("/api/diagram-to-secdsl", ex -> handleDiagram(ex, false));
        server.createContext("/api/merge", this::handleMerge);
        server.setExecutor(null);
        server.start();
        System.out.println("SAM4C Studio running at http://localhost:" + port);
        System.out.println("Press Ctrl+C to stop.");
    }

    private void handleRoot(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { send(ex, 405, "text/plain", "Method Not Allowed"); return; }
        if (!ex.getRequestURI().getPath().equals("/")) { send(ex, 404, "text/plain", "Not Found"); return; }
        send(ex, 200, "text/html; charset=utf-8", StudioPage.html());
    }

    private void handlePalette(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { send(ex, 405, "application/json", "{\"error\":\"GET only\"}"); return; }
        send(ex, 200, "application/json", Palette.json());
    }

    @SuppressWarnings("unchecked")
    private void handleDiagram(HttpExchange ex, boolean yaml) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { send(ex, 405, "application/json", "{\"error\":\"POST only\"}"); return; }
        try {
            Map<String, Object> diagram = JSON.readValue(ex.getRequestBody().readAllBytes(), Map.class);
            Architecture arch = DiagramReader.build(diagram);

            // Required-attribute policy BLOCKS generation
            List<String> problems = AttributePolicy.validate(arch);
            if (!problems.isEmpty()) {
                String nodeNames = problems.stream()
                    .map(p -> p.substring(0, p.indexOf(" (")))
                    .map(WebServer::jsonString).collect(Collectors.joining(", "));
                send(ex, 200, "application/json",
                    "{ \"error\": \"missing required attributes\", \"problems\": " + jsonArray(problems)
                    + ", \"problemNodes\": [" + nodeNames + "] }");
                return;
            }

            String content = yaml ? ArchYamlWriter.write(arch) : SecDslScaffolder.generate(arch);
            String key = yaml ? "yaml" : "secdsl";
            send(ex, 200, "application/json", "{ \"" + key + "\": " + jsonString(content) + " }");
        } catch (Exception e) {
            send(ex, 200, "application/json", "{ \"error\": " + jsonString(errMsg(e)) + " }");
        }
    }

    @SuppressWarnings("unchecked")
    private void handleMerge(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { send(ex, 405, "application/json", "{\"error\":\"POST only\"}"); return; }
        try {
            Map<String, String> req = JSON.readValue(ex.getRequestBody().readAllBytes(), Map.class);
            PipelineResult result = pipeline.run(req.getOrDefault("arch", ""), req.getOrDefault("rules", ""), "model");

            if (!result.ok()) {
                send(ex, 200, "application/json",
                    "{ \"conformanceErrors\": " + jsonArray(result.conformanceErrors()) + " }");
                return;
            }
            GraphData g = GraphBuilder.build(result.model());
            send(ex, 200, "application/json", "{ "
                + "\"nodes\": [" + g.nodesJson() + "], "
                + "\"edges\": [" + g.edgesJson() + "], "
                + "\"coverage\": " + g.coverageJson() + ", "
                + "\"warnings\": " + jsonArray(result.warnings()) + ", "
                + "\"conformanceErrors\": [] }");
        } catch (Exception e) {
            send(ex, 200, "application/json", "{ \"error\": " + jsonString(errMsg(e)) + " }");
        }
    }

    // -- helpers ---------------------------------------------------------------

    private static String errMsg(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    private static void send(HttpExchange ex, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private static String jsonArray(List<String> items) {
        return "[" + items.stream().map(WebServer::jsonString).collect(Collectors.joining(", ")) + "]";
    }

    private static String jsonString(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "").replace("\t", "  ") + "\"";
    }
}
