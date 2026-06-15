package sam4c.light.output;

/**
 * Shared presentation for the Cytoscape graph: CSS, the Cytoscape style array,
 * and all interaction logic.
 *
 * Both the static file (HtmlReportGenerator) and the live web page (web/EditorPage)
 * include these fragments, so the graph looks and behaves identically in both. The
 * only difference between the two is how the graph data arrives: embedded inline in
 * the file, fetched over HTTP in the web app. Both ultimately call initGraph().
 */
public final class GraphView {

    private GraphView() {}

    /** CDN script tag for Cytoscape.js. */
    public static final String CDN =
        "<script src=\"https://cdnjs.cloudflare.com/ajax/libs/cytoscape/3.29.2/cytoscape.min.js\"></script>";

    /** Shared CSS (graph canvas, sidebar, panels, legend). */
    public static String css() {
        return """
* { box-sizing: border-box; margin: 0; padding: 0; }
body { font-family: 'Segoe UI', Arial, sans-serif; background: #f5f5f5; display: flex; height: 100vh; }

#sidebar { width: 320px; min-width: 280px; background: #1e1e2e; color: #cdd6f4; display: flex; flex-direction: column; overflow: hidden; }
#sidebar h1 { font-size: 14px; padding: 16px; background: #181825; color: #cba6f7; border-bottom: 1px solid #313244; }
#sidebar h2 { font-size: 11px; text-transform: uppercase; letter-spacing: 1px; color: #6c7086; padding: 12px 16px 6px; }

#info-panel { padding: 12px 16px; border-bottom: 1px solid #313244; min-height: 90px; }
#info-panel p { font-size: 12px; color: #6c7086; }
#info-name  { font-size: 15px; font-weight: bold; color: #cba6f7; margin-bottom: 6px; }
#info-type  { font-size: 12px; color: #89dceb; margin-bottom: 6px; }
#info-attrs { font-size: 11px; color: #a6e3a1; line-height: 1.6; }
#info-ports { font-size: 11px; color: #fab387; margin-top: 4px; }

#view-panel, #filter-panel { padding: 10px 16px; border-bottom: 1px solid #313244; }
.view-btn { display: inline-block; font-size: 11px; padding: 5px 12px; margin: 2px; border-radius: 6px; border: 1px solid #45475a; background: #313244; color: #cdd6f4; cursor: pointer; }
.view-btn.active { background: #3949ab; color: #fff; border-color: #1565c0; }
.filter-btn { display: inline-block; font-size: 10px; padding: 3px 8px; margin: 2px; border-radius: 10px; border: none; cursor: pointer; color: white; opacity: 0.9; }
.filter-btn.off { opacity: 0.3; }

#coverage-panel { flex: 1; overflow-y: auto; padding: 0 16px 16px; }
.ctx-item { font-size: 11px; margin: 4px 0; line-height: 1.5; }
.ctx-name  { color: #cba6f7; font-weight: bold; margin-right: 6px; }
.ctx-comps { color: #a6e3a1; }

#warnings-panel { padding: 0 16px; }
.warn-item { font-size: 11px; color: #f9e2af; margin: 4px 0; line-height: 1.4; }

#legend { padding: 12px 16px; border-top: 1px solid #313244; font-size: 11px; color: #cdd6f4; }
.legend-item { display: flex; align-items: center; margin: 3px 0; color: #cdd6f4; }
.legend-line { width: 24px; height: 3px; margin-right: 8px; border-radius: 2px; flex-shrink: 0; }
.legend-dash { border-top: 2px dashed; height: 0; width: 24px; margin-right: 8px; flex-shrink: 0; }

#cy-container { flex: 1; position: relative; background: #fafafa; }
#cy { width: 100%; height: 100%; }
#controls { position: absolute; top: 12px; right: 12px; display: flex; gap: 6px; z-index: 5; }
.ctrl-btn { background: #1e1e2e; color: #cdd6f4; border: 1px solid #313244; padding: 6px 12px; border-radius: 6px; cursor: pointer; font-size: 12px; }
.ctrl-btn:hover { background: #313244; }
""";
    }

    /** The sidebar markup (view toggle, info, filters, coverage, warnings, legend). */
    public static String sidebar(String title) {
        return """
<div id="sidebar">
  <h1>&#9650; %s</h1>

  <h2>View</h2>
  <div id="view-panel">
    <button class="view-btn active" data-view="both">Both</button>
    <button class="view-btn" data-view="arch">Architecture</button>
    <button class="view-btn" data-view="security">Security</button>
  </div>

  <h2>Selected</h2>
  <div id="info-panel"><p>Click a node or edge to inspect it.</p></div>

  <h2>Filter rules</h2>
  <div id="filter-panel">
    <button class="filter-btn" style="background:#1565c0" data-rule="Confidentiality">Confidentiality</button>
    <button class="filter-btn" style="background:#2e7d32" data-rule="Integrity">Integrity</button>
    <button class="filter-btn" style="background:#c62828" data-rule="Isolation">Isolation</button>
    <button class="filter-btn" style="background:#6a1b9a" data-rule="Authentication">Authentication</button>
  </div>

  <h2>Warnings</h2>
  <div id="warnings-panel"></div>

  <h2>Coverage</h2>
  <div id="coverage-panel"></div>

  <div id="legend">
    <div class="legend-item"><div class="legend-line" style="background:#1565c0"></div>Confidentiality</div>
    <div class="legend-item"><div class="legend-line" style="background:#2e7d32"></div>Integrity</div>
    <div class="legend-item"><div class="legend-dash" style="border-color:#c62828"></div>Isolation</div>
    <div class="legend-item"><div class="legend-line" style="background:#6a1b9a"></div>Authentication</div>
    <div class="legend-item"><div class="legend-line" style="background:#455a64"></div>Architecture link</div>
  </div>
</div>
""".formatted(title);
    }

    /**
     * Shared JavaScript: defines initGraph(elements, coverage, warnings) plus all
     * interaction handlers. Wires the sidebar buttons once on load. Operates on a
     * module-global `cy` so the control buttons and re-runs work consistently.
     */
    public static String script() {
        return """
let cy = null;
let currentView = 'both';
const activeFilters = new Set(['Confidentiality','Integrity','Isolation','Authentication']);

function coseLayout() {
  return { name: 'cose', animate: true, animationDuration: 600, fit: true, padding: 40, nodeRepulsion: 9000, idealEdgeLength: 130 };
}
function fitGraph() { if (cy) cy.fit(null, 40); }
function relayout() { if (cy) cy.layout(coseLayout()).run(); }

function cyStyle() {
  return [
    { selector: 'node', style: { 'label':'data(label)', 'background-color':'data(bg)', 'shape':'data(shape)', 'color':'#fff', 'font-size':'11px', 'text-valign':'center', 'text-halign':'center', 'text-wrap':'wrap', 'border-width':1, 'border-color':'#555', 'padding':'10px' } },
    { selector: 'node[type = "VM"]', style: { 'background-color':'#e8eaf6', 'color':'#1a237e', 'font-size':'12px', 'font-weight':'bold', 'text-valign':'top', 'text-halign':'center', 'border-color':'#3949ab', 'border-width':2, 'padding':'20px' } },
    { selector: 'node[type = "Connector"]', style: { 'background-color':'data(bg)', 'color':'#fff', 'font-size':'9px', 'shape':'diamond', 'width':'34px', 'height':'34px', 'border-width':1, 'border-color':'#37474f' } },
    { selector: ':parent', style: { 'background-opacity':0.15 } },
    { selector: 'edge', style: { 'line-color':'data(color)', 'target-arrow-color':'data(color)', 'target-arrow-shape':'triangle', 'arrow-scale':1.2, 'line-style':'data(style)', 'label':'data(label)', 'font-size':'9px', 'color':'#333', 'text-background-color':'#fff', 'text-background-opacity':0.9, 'text-background-padding':'2px', 'text-rotation':'autorotate', 'curve-style':'bezier', 'control-point-step-size':60, 'width':2 } },
    { selector: 'edge[layer = "arch"]', style: { 'line-color':'#455a64', 'target-arrow-color':'#455a64', 'source-arrow-color':'#455a64', 'target-arrow-shape':'triangle', 'source-arrow-shape':'none', 'width':1.5, 'opacity':0.55 } },
    { selector: 'edge[layer = "arch"][dir = "inout"]', style: { 'source-arrow-shape':'triangle' } },
    { selector: '.highlighted', style: { 'border-color':'#f9a825', 'border-width':3, 'opacity':1 } },
    { selector: '.faded', style: { 'opacity':0.15 } }
  ];
}

function initGraph(elements, coverageData, warnings) {
  cy = cytoscape({ container: document.getElementById('cy'), elements: elements, style: cyStyle(), layout: coseLayout() });

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

  cy.on('tap', 'edge', function(e) {
    const d = e.target.data();
    cy.elements().removeClass('highlighted faded');
    e.target.addClass('highlighted');
    e.target.source().addClass('highlighted');
    e.target.target().addClass('highlighted');
    cy.elements().not(e.target).not(e.target.source()).not(e.target.target()).addClass('faded');
    if (d.layer === 'rule') {
      let html = '<div id="info-name">' + d.label + '</div>';
      html += '<div id="info-attrs">sctx = [' + (d.sctxAll || '') + ']</div>';
      if (d.tctxAll) html += '<div id="info-attrs">tctx = [' + d.tctxAll + ']</div>';
      if (d.actxAll) html += '<div id="info-attrs">actx = [' + d.actxAll + ']</div>';
      html += '<div id="info-ports" style="margin-top:6px">this edge: ' + e.target.source().data('label') + ' → ' + e.target.target().data('label') + '</div>';
      document.getElementById('info-panel').innerHTML = html;
    } else {
      document.getElementById('info-panel').innerHTML =
        '<div id="info-name">Architecture link</div>' +
        '<div id="info-type">' + e.target.source().data('label') + ' → ' + e.target.target().data('label') + '</div>';
    }
  });

  cy.on('tap', function(e) {
    if (e.target === cy) {
      cy.elements().removeClass('highlighted faded');
      document.getElementById('info-panel').innerHTML = '<p>Click a node or edge to inspect it.</p>';
    }
  });

  renderCoverage(coverageData);
  renderWarnings(warnings);
  applyView();
}

function renderCoverage(data) {
  const panel = document.getElementById('coverage-panel');
  if (!panel) return;
  if (!data) { panel.innerHTML = ''; return; }
  let html = '';
  for (const ctx in data) {
    const comps = data[ctx];
    const txt = comps.length ? comps.join(' ') : '(no match)';
    html += "<div class='ctx-item'><span class='ctx-name'>" + ctx + "</span><span class='ctx-comps'>" + txt + "</span></div>";
  }
  panel.innerHTML = html;
}

function renderWarnings(warnings) {
  const panel = document.getElementById('warnings-panel');
  if (!panel) return;
  if (!warnings || !warnings.length) { panel.innerHTML = "<div class='warn-item' style='color:#6c7086'>none</div>"; return; }
  panel.innerHTML = warnings.map(w => "<div class='warn-item'>! " + w + "</div>").join('');
}

function applyView() {
  if (!cy) return;
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

function applyRuleFilter(edge) {
  edge.style('display', activeFilters.has(edge.data('label')) ? 'element' : 'none');
}

document.querySelectorAll('.view-btn').forEach(btn => btn.addEventListener('click', function() {
  currentView = this.dataset.view;
  document.querySelectorAll('.view-btn').forEach(b => b.classList.remove('active'));
  this.classList.add('active');
  applyView();
}));

document.querySelectorAll('.filter-btn').forEach(btn => btn.addEventListener('click', function() {
  const rule = this.dataset.rule;
  if (activeFilters.has(rule)) { activeFilters.delete(rule); this.classList.add('off'); }
  else { activeFilters.add(rule); this.classList.remove('off'); }
  applyView();
}));
""";
    }
}
