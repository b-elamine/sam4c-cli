package sam4c.light.output;

import sam4c.light.merger.SemanticValidator;
import sam4c.light.model.UnifiedModel;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.stream.Collectors;

/**
 * Writes a self-contained interactive HTML graph file (--html).
 *
 * The graph data and presentation are produced by GraphBuilder and GraphView,
 * the same components the web server uses -- so the file and the live web view
 * are identical. This class only assembles them into a static page with the
 * data embedded inline (no server needed to open it).
 */
public final class HtmlReportGenerator {

    private HtmlReportGenerator() {}

    public static void write(UnifiedModel model, File output) throws IOException {
        GraphData data = GraphBuilder.build(model);

        // Embed the semantic warnings as a JS array literal
        String warningsJs = SemanticValidator.validate(model).stream()
            .map(w -> "'" + w.replace("\\", "\\\\").replace("'", "\\'") + "'")
            .collect(Collectors.joining(", "));

        String html = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>%s -- SAM4C Model Graph</title>
%s
<style>
%s
</style>
</head>
<body>
%s
<div id="cy-container">
  <div id="cy"></div>
  <div id="controls">
    <button class="ctrl-btn" onclick="fitGraph()">Fit</button>
    <button class="ctrl-btn" onclick="relayout()">Re-layout</button>
  </div>
</div>
<script>
%s

initGraph(%s, %s, [%s]);
</script>
</body>
</html>
""".formatted(
            escapeHtml(model.architecture().name()),
            GraphView.CDN,
            GraphView.css(),
            GraphView.sidebar(escapeHtml(model.architecture().name())),
            GraphView.script(),
            data.elementsJson(),
            data.coverageJson(),
            warningsJs
        );

        Files.writeString(output.toPath(), html);
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
