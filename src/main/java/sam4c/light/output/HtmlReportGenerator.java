package sam4c.light.output;

import sam4c.light.model.*;
import sam4c.light.model.rule.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

/**
 * Generates a self-contained interactive HTML graph from the in-memory UnifiedModel.
 *
 * This class never reads from a file or a JSON string. Every piece of data
 * in the output comes from direct Java field access on the actual model objects:
 *
 *   rule.sctxComponents()   -> List<Component>  (real Java references)
 *   component.name()        -> String
 *   component.type()        -> String
 *   component.ports()       -> List<Port>
 *   component.attributes()  -> Map<String,String>
 *   component.children()    -> List<Component>
 *
 * Node identity is tracked with an IdentityHashMap<Component, String>.
 * IdentityHashMap uses == (reference equality), not .equals().
 * This means: if two security rules both reference the same Component object
 * in memory, they get the same node ID in the graph, producing shared nodes
 * and multiple edges -- exactly the object graph structure in memory.
 */
public class HtmlReportGenerator {

    public static void write(UnifiedModel model, File output) throws IOException {
        // IdentityHashMap: key lookup uses == not .equals()
        // Guarantees one Cytoscape node per Java object, regardless of where
        // in the model the object is referenced from.
        IdentityHashMap<Component, String> nodeIds = new IdentityHashMap<>();
        List<String> cyNodes = new ArrayList<>();
        List<String> cyEdges = new ArrayList<>();

        // Register every component from the architecture tree first
        registerComponents(model.architecture().components(), null, nodeIds, cyNodes);

        // Build a name -> nodeId lookup for resolving port references in links
        Map<String, String> compNameToNodeId = new LinkedHashMap<>();
        nodeIds.forEach((c, id) -> compNameToNodeId.put(c.name(), id));

        // Register connectors as nodes (architecture layer)
        Map<String, String> connectorNodeIds = new LinkedHashMap<>();
        for (Connector c : model.architecture().connectors()) {
            String id = "conn_" + c.name().replaceAll("[^a-zA-Z0-9]", "_");
            connectorNodeIds.put(c.name(), id);
            String label = c.name() + (c.external() ? "\n(external)" : "");
            cyNodes.add(String.format(
                "{ data: { id:'%s', label:'%s', type:'Connector', " +
                "attrs:'', ports:'', bg:'%s', shape:'diamond' } }",
                id, escape(label), c.external() ? "#546e7a" : "#78909c"
            ));
        }

        // Register architecture links as edges (component -> connector topology)
        // portRef format is "ComponentName.portName" -- extract component before the dot
        for (Link link : model.architecture().links()) {
            String compName = link.portRef().contains(".")
                ? link.portRef().substring(0, link.portRef().indexOf('.'))
                : link.portRef();
            String compNodeId  = compNameToNodeId.get(compName);
            String connNodeId  = connectorNodeIds.get(link.connectorName());
            if (compNodeId == null || connNodeId == null) continue;
            cyEdges.add(String.format(
                "{ data: { id:'arch_%s_%s', source:'%s', target:'%s', " +
                "label:'', color:'#455a64', style:'solid', layer:'arch' } }",
                escape(compName), escape(link.connectorName()), compNodeId, connNodeId
            ));
        }

        // Build security edges by traversing resolved rules using real object references.
        // Only leaf components (non-VM) appear as endpoints; VM containers are shown as
        // compound parent nodes, so leaf-to-leaf edges keep the graph readable.
        int edgeSeq = 0;
        Set<String> edgeSeen = new LinkedHashSet<>();

        for (ResolvedRule rr : model.resolvedRules()) {
            String ruleType = rr.rule().getClass().getSimpleName();
            String color    = edgeColor(rr.rule());
            String style    = edgeStyle(rr.rule());

            for (Component from : rr.sctxComponents()) {
                if (isContainer(from)) continue;

                for (Component to : rr.tctxComponents()) {
                    if (isContainer(to)) continue;
                    if (from == to) continue;

                    String fromId = nodeIds.get(from); // IdentityHashMap: uses ==
                    String toId   = nodeIds.get(to);
                    if (fromId == null || toId == null) continue;

                    String edgeKey = fromId + ":" + toId + ":" + ruleType;
                    if (!edgeSeen.add(edgeKey)) continue;

                    cyEdges.add(String.format(
                        "{ data: { id:'e%d', source:'%s', target:'%s', " +
                        "label:'%s', color:'%s', style:'%s', layer:'rule' } }",
                        edgeSeq++, fromId, toId, ruleType, color, style
                    ));
                }
            }
        }

        // Build coverage summary for the sidebar
        StringBuilder coverageHtml = new StringBuilder();
        model.coverage().forEach((ctx, comps) -> {
            coverageHtml.append("<div class='ctx-item'>")
                .append("<span class='ctx-name'>").append(escape(ctx)).append("</span>")
                .append("<span class='ctx-comps'>");
            if (comps.isEmpty()) {
                coverageHtml.append("(no match)");
            } else {
                comps.forEach(c -> coverageHtml.append(escape(c.name())).append(" "));
            }
            coverageHtml.append("</span></div>");
        });

        String html = template()
            .replace("{{TITLE}}",    escape(model.architecture().name()))
            .replace("{{NODES}}",    String.join(",\n", cyNodes))
            .replace("{{EDGES}}",    String.join(",\n", cyEdges))
            .replace("{{COVERAGE}}", coverageHtml.toString());

        Files.writeString(output.toPath(), html);
    }

    // -------------------------------------------------------------------------
    // Component registration
    // -------------------------------------------------------------------------

    private static void registerComponents(List<Component> components, String parentId,
                                            IdentityHashMap<Component, String> nodeIds,
                                            List<String> cyNodes) {
        for (Component c : components) {
            String id = "n" + nodeIds.size();
            nodeIds.put(c, id); // uses == for key storage

            String parent  = parentId != null ? String.format(", parent: '%s'", parentId) : "";
            String bgColor = nodeColor(c.type());
            String shape   = nodeShape(c.type());

            String attrsStr = escape(c.attributes().toString());
            String portsStr = escape(c.ports().stream().map(Port::name)
                .collect(java.util.stream.Collectors.joining(", ")));

            cyNodes.add(String.format(
                "{ data: { id:'%s', label:'%s', type:'%s', " +
                "attrs:'%s', ports:'%s', bg:'%s', shape:'%s'%s } }",
                id, escape(c.name()), escape(c.type()),
                attrsStr, portsStr, bgColor, shape, parent
            ));

            if (!c.children().isEmpty()) {
                registerComponents(c.children(), id, nodeIds, cyNodes);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Style helpers
    // -------------------------------------------------------------------------

    private static boolean isContainer(Component c) {
        return !c.children().isEmpty() || c.type().equals("VM");
    }

    private static String nodeColor(String type) {
        return switch (type) {
            case "VM"   -> "#e8eaf6";
            case "App"  -> "#1565c0";
            case "Data" -> "#e65100";
            default     -> "#37474f";
        };
    }

    private static String nodeShape(String type) {
        return switch (type) {
            case "VM"   -> "roundrectangle";
            case "App"  -> "ellipse";
            case "Data" -> "barrel";
            default     -> "diamond";
        };
    }

    private static String edgeColor(SecurityRule rule) {
        return switch (rule) {
            case Confidentiality ignored -> "#1565c0";
            case Integrity       ignored -> "#2e7d32";
            case Isolation       ignored -> "#c62828";
            case Authentication  ignored -> "#6a1b9a";
        };
    }

    private static String edgeStyle(SecurityRule rule) {
        return rule instanceof Isolation ? "dashed" : "solid";
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ");
    }

    // -------------------------------------------------------------------------
    // HTML template with embedded Cytoscape.js (CDN)
    // -------------------------------------------------------------------------

    private static String template() {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>{{TITLE}} -- SAM4C Model Graph</title>
<script src="https://cdnjs.cloudflare.com/ajax/libs/cytoscape/3.29.2/cytoscape.min.js"></script>
<style>
* { box-sizing: border-box; margin: 0; padding: 0; }
body { font-family: 'Segoe UI', Arial, sans-serif; background: #f5f5f5; display: flex; height: 100vh; }

#sidebar {
  width: 300px; min-width: 260px; background: #1e1e2e; color: #cdd6f4;
  display: flex; flex-direction: column; overflow: hidden;
}
#sidebar h1 { font-size: 14px; padding: 16px; background: #181825; color: #cba6f7; border-bottom: 1px solid #313244; }
#sidebar h2 { font-size: 11px; text-transform: uppercase; letter-spacing: 1px; color: #6c7086; padding: 12px 16px 6px; }

#info-panel { padding: 12px 16px; border-bottom: 1px solid #313244; min-height: 100px; }
#info-panel p { font-size: 12px; color: #6c7086; }
#info-name  { font-size: 15px; font-weight: bold; color: #cba6f7; margin-bottom: 6px; }
#info-type  { font-size: 12px; color: #89dceb; margin-bottom: 6px; }
#info-attrs { font-size: 11px; color: #a6e3a1; line-height: 1.6; }
#info-ports { font-size: 11px; color: #fab387; margin-top: 4px; }

#view-panel { padding: 10px 16px; border-bottom: 1px solid #313244; }
.view-btn {
  display: inline-block; font-size: 11px; padding: 5px 12px; margin: 2px;
  border-radius: 6px; border: 1px solid #45475a; background: #313244; color: #cdd6f4;
  cursor: pointer; transition: all 0.15s;
}
.view-btn.active { background: #3949ab; color: #fff; border-color: #1565c0; }

#filter-panel { padding: 10px 16px; border-bottom: 1px solid #313244; }
.filter-btn {
  display: inline-block; font-size: 10px; padding: 3px 8px; margin: 2px;
  border-radius: 10px; border: none; cursor: pointer; color: white; opacity: 0.9;
}
.filter-btn.off { opacity: 0.3; }

#coverage-panel { flex: 1; overflow-y: auto; padding: 0 16px 16px; }
.ctx-item { font-size: 11px; margin: 4px 0; line-height: 1.5; }
.ctx-name  { color: #cba6f7; font-weight: bold; margin-right: 6px; }
.ctx-comps { color: #a6e3a1; }

#legend { padding: 12px 16px; border-top: 1px solid #313244; font-size: 11px; color: #cdd6f4; }
.legend-item { display: flex; align-items: center; margin: 3px 0; color: #cdd6f4; }
.legend-line { width: 24px; height: 3px; margin-right: 8px; border-radius: 2px; flex-shrink: 0; }
.legend-dash { border-top: 2px dashed; height: 0; width: 24px; margin-right: 8px; flex-shrink: 0; }

#cy-container { flex: 1; position: relative; background: #fafafa; }
#cy { width: 100%; height: 100%; }

#controls { position: absolute; top: 12px; right: 12px; display: flex; gap: 6px; }
.ctrl-btn {
  background: #1e1e2e; color: #cdd6f4; border: 1px solid #313244;
  padding: 6px 12px; border-radius: 6px; cursor: pointer; font-size: 12px;
}
.ctrl-btn:hover { background: #313244; }
</style>
</head>
<body>

<div id="sidebar">
  <h1>&#9650; {{TITLE}}</h1>

  <h2>View</h2>
  <div id="view-panel">
    <button class="view-btn active" data-view="both">Both</button>
    <button class="view-btn" data-view="arch">Architecture</button>
    <button class="view-btn" data-view="security">Security</button>
  </div>

  <h2>Selected</h2>
  <div id="info-panel">
    <p>Click a node or edge to inspect it.</p>
  </div>

  <h2>Filter rules</h2>
  <div id="filter-panel">
    <button class="filter-btn" style="background:#1565c0" data-rule="Confidentiality">Confidentiality</button>
    <button class="filter-btn" style="background:#2e7d32" data-rule="Integrity">Integrity</button>
    <button class="filter-btn" style="background:#c62828" data-rule="Isolation">Isolation</button>
    <button class="filter-btn" style="background:#6a1b9a" data-rule="Authentication">Authentication</button>
  </div>

  <h2>Coverage</h2>
  <div id="coverage-panel">{{COVERAGE}}</div>

  <div id="legend">
    <div class="legend-item"><div class="legend-line" style="background:#1565c0"></div>Confidentiality</div>
    <div class="legend-item"><div class="legend-line" style="background:#2e7d32"></div>Integrity</div>
    <div class="legend-item"><div class="legend-dash" style="border-color:#c62828"></div>Isolation</div>
    <div class="legend-item"><div class="legend-line" style="background:#6a1b9a"></div>Authentication</div>
    <div class="legend-item"><div class="legend-line" style="background:#455a64"></div>Architecture link</div>
  </div>
</div>

<div id="cy-container">
  <div id="cy"></div>
  <div id="controls">
    <button class="ctrl-btn" onclick="cy.fit(null, 40)">Fit</button>
    <button class="ctrl-btn" onclick="relayout()">Re-layout</button>
  </div>
</div>

<script>
const elements = {
  nodes: [ {{NODES}} ],
  edges: [ {{EDGES}} ]
};

const cy = cytoscape({
  container: document.getElementById('cy'),
  elements: elements,
  style: [
    {
      selector: 'node',
      style: {
        'label': 'data(label)',
        'background-color': 'data(bg)',
        'shape': 'data(shape)',
        'color': '#fff',
        'font-size': '11px',
        'text-valign': 'center',
        'text-halign': 'center',
        'text-wrap': 'wrap',
        'border-width': 1,
        'border-color': '#555',
        'padding': '10px'
      }
    },
    {
      selector: 'node[type = "VM"]',
      style: {
        'background-color': '#e8eaf6',
        'color': '#1a237e',
        'font-size': '12px',
        'font-weight': 'bold',
        'text-valign': 'top',
        'text-halign': 'center',
        'border-color': '#3949ab',
        'border-width': 2,
        'padding': '20px'
      }
    },
    {
      selector: 'node[type = "Connector"]',
      style: {
        'background-color': 'data(bg)',
        'color': '#fff',
        'font-size': '9px',
        'shape': 'diamond',
        'width': '34px',
        'height': '34px',
        'border-width': 1,
        'border-color': '#37474f'
      }
    },
    {
      selector: ':parent',
      style: { 'background-opacity': 0.15 }
    },
    {
      selector: 'edge',
      style: {
        'line-color': 'data(color)',
        'target-arrow-color': 'data(color)',
        'target-arrow-shape': 'triangle',
        'arrow-scale': 1.2,
        'line-style': 'data(style)',
        'label': 'data(label)',
        'font-size': '9px',
        'color': '#333',
        'text-background-color': '#fff',
        'text-background-opacity': 0.9,
        'text-background-padding': '2px',
        'curve-style': 'bezier',
        'width': 2
      }
    },
    {
      selector: 'edge[layer = "arch"]',
      style: {
        'line-color': '#455a64',
        'target-arrow-shape': 'none',
        'width': 1.5,
        'opacity': 0.45
      }
    },
    {
      selector: '.highlighted',
      style: { 'border-color': '#f9a825', 'border-width': 3, 'opacity': 1 }
    },
    {
      selector: '.faded',
      style: { 'opacity': 0.15 }
    }
  ],
  layout: { name: 'cose', animate: true, animationDuration: 600, fit: true, padding: 40, nodeRepulsion: 9000, idealEdgeLength: 130 }
});

// Click node -- show details and highlight neighbourhood
cy.on('tap', 'node', function(e) {
  const d = e.target.data();
  cy.elements().removeClass('highlighted faded');
  e.target.addClass('highlighted');
  e.target.neighborhood().addClass('highlighted');
  cy.elements().not(e.target.neighborhood()).not(e.target).addClass('faded');

  document.getElementById('info-panel').innerHTML =
    '<div id="info-name">' + d.label + '</div>' +
    '<div id="info-type">Type: ' + d.type + '</div>' +
    (d.attrs ? '<div id="info-attrs">Attributes: ' + d.attrs + '</div>' : '') +
    (d.ports ? '<div id="info-ports">Ports: ' + d.ports + '</div>' : '');
});

// Click edge -- show rule details
cy.on('tap', 'edge', function(e) {
  const d = e.target.data();
  cy.elements().removeClass('highlighted faded');
  e.target.addClass('highlighted');
  e.target.source().addClass('highlighted');
  e.target.target().addClass('highlighted');
  cy.elements().not(e.target).not(e.target.source()).not(e.target.target()).addClass('faded');

  const label = d.label || 'Architecture link';
  document.getElementById('info-panel').innerHTML =
    '<div id="info-name">' + label + '</div>' +
    '<div id="info-type">' + e.target.source().data('label') +
    ' \\u2192 ' + e.target.target().data('label') + '</div>';
});

// Click background -- reset
cy.on('tap', function(e) {
  if (e.target === cy) {
    cy.elements().removeClass('highlighted faded');
    document.getElementById('info-panel').innerHTML =
      '<p>Click a node or edge to inspect it.</p>';
  }
});

// View toggle: both / architecture / security
let currentView = 'both';
document.querySelectorAll('.view-btn').forEach(btn => {
  btn.addEventListener('click', function() {
    currentView = this.dataset.view;
    document.querySelectorAll('.view-btn').forEach(b => b.classList.remove('active'));
    this.classList.add('active');
    applyView();
  });
});

function applyView() {
  if (currentView === 'both') {
    cy.elements().style('display', 'element');
    cy.edges('[layer = "rule"]').forEach(applyRuleFilter);
  } else if (currentView === 'arch') {
    cy.edges('[layer = "rule"]').style('display', 'none');
    cy.edges('[layer = "arch"]').style('display', 'element');
    cy.nodes().style('display', 'element');
  } else if (currentView === 'security') {
    cy.edges('[layer = "arch"]').style('display', 'none');
    cy.edges('[layer = "rule"]').forEach(applyRuleFilter);
    cy.nodes('[type = "Connector"]').style('display', 'none');
    cy.nodes('[type != "Connector"]').style('display', 'element');
  }
}

// Rule type filter
const activeFilters = new Set(['Confidentiality','Integrity','Isolation','Authentication']);
function applyRuleFilter(edge) {
  const visible = activeFilters.has(edge.data('label'));
  edge.style('display', visible ? 'element' : 'none');
}
document.querySelectorAll('.filter-btn').forEach(btn => {
  btn.addEventListener('click', function() {
    const rule = this.dataset.rule;
    if (activeFilters.has(rule)) {
      activeFilters.delete(rule);
      this.classList.add('off');
    } else {
      activeFilters.add(rule);
      this.classList.remove('off');
    }
    applyView();
  });
});

function relayout() {
  cy.layout({ name: 'cose', animate: true, animationDuration: 600, fit: true, padding: 40, nodeRepulsion: 9000, idealEdgeLength: 130 }).run();
}
</script>
</body>
</html>
""";
    }
}
