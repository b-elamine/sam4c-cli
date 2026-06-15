package sam4c.light.web;

import sam4c.light.output.GraphView;

/**
 * The single-page Studio served at GET /.
 *
 * Three pipeline stages with top navigation and shared in-memory state:
 *   1. Design  -- palette (from metamodel) + canvas editor + properties form
 *   2. Files   -- YAML + SecDSL text editors, each with upload/download
 *   3. Result  -- the merged security graph (reuses GraphView)
 *
 * The result stage reuses GraphView (css/sidebar/script -> initGraph). The design
 * stage has its own lightweight Cytoscape editor instance (editorCy) so the two
 * never collide.
 */
public final class StudioPage {

    private StudioPage() {}

    public static String html() {
        String page = TEMPLATE
            .replace("{{CSS}}", GraphView.css())
            .replace("{{STUDIO_CSS}}", STUDIO_CSS)
            .replace("{{RESULT_SIDEBAR}}", GraphView.sidebar("Result"))
            .replace("{{GRAPHVIEW_JS}}", GraphView.script())
            .replace("{{EDITOR_JS}}", EDITOR_JS);
        return CDN_LINE.replace("{{CDN}}", GraphView.CDN).concat(page);
    }

    private static final String CDN_LINE = "{{CDN}}";

    private static final String STUDIO_CSS = """
#topnav { position: fixed; top: 0; left: 0; right: 0; height: 46px; background: #181825; border-bottom: 1px solid #313244; display: flex; align-items: center; gap: 4px; padding: 0 14px; z-index: 50; }
#topnav .brand { color: #cba6f7; font-weight: bold; font-size: 14px; margin-right: 16px; }
#topnav .nav-btn { background: #1e1e2e; color: #cdd6f4; border: 1px solid #313244; padding: 6px 14px; border-radius: 6px; cursor: pointer; font-size: 12px; }
#topnav .nav-btn.active { background: #3949ab; color: #fff; border-color: #1565c0; }
.stage { position: fixed; top: 46px; left: 0; right: 0; bottom: 0; display: none; }
.stage.active { display: flex; }

/* Stage 1: design */
#palette { width: 170px; background: #11111b; border-right: 1px solid #313244; padding: 12px; }
#palette h3 { font-size: 11px; text-transform: uppercase; color: #6c7086; margin-bottom: 8px; }
.pal-item { display: block; width: 100%; text-align: left; margin: 4px 0; padding: 8px 10px; border-radius: 6px; border: 1px solid #45475a; background: #1e1e2e; color: #cdd6f4; cursor: pointer; font-size: 12px; }
.pal-item:hover { background: #313244; }
#cy-edit { flex: 1; background: #fafafa; }
#props { width: 280px; background: #1e1e2e; color: #cdd6f4; border-left: 1px solid #313244; padding: 14px; overflow-y: auto; }
#props h3 { font-size: 11px; text-transform: uppercase; color: #6c7086; margin-bottom: 10px; }
#props label { display: block; font-size: 11px; color: #89dceb; margin: 8px 0 3px; }
#props input { width: 100%; background: #11111b; color: #cdd6f4; border: 1px solid #313244; border-radius: 4px; padding: 6px; font-size: 12px; }
#props .req { color: #f9e2af; }
#design-actions { position: fixed; bottom: 0; left: 170px; right: 280px; padding: 10px; background: #181825; border-top: 1px solid #313244; text-align: center; }
.act-btn { background: #3949ab; color: #fff; border: none; padding: 8px 16px; border-radius: 6px; cursor: pointer; font-size: 13px; margin: 0 4px; }
.act-btn.secondary { background: #313244; }
#design-msg { font-size: 11px; margin-left: 10px; }
#design-msg.error { color: #f38ba8; }
#design-msg.ok { color: #a6e3a1; }

/* Stage 2: files */
#files { flex: 1; display: flex; flex-direction: column; }
#files-cols { flex: 1; display: flex; }
.file-col { flex: 1; display: flex; flex-direction: column; border-right: 1px solid #313244; background: #11111b; }
.file-col h3 { font-size: 11px; text-transform: uppercase; color: #6c7086; padding: 10px 14px 4px; }
.file-col textarea { flex: 1; margin: 0 14px; background: #1e1e2e; color: #cdd6f4; border: 1px solid #313244; border-radius: 6px; padding: 10px; font-family: Consolas, monospace; font-size: 12px; resize: none; }
.file-bar { padding: 10px 14px; display: flex; gap: 8px; }
.file-bar .act-btn { font-size: 12px; padding: 6px 12px; }
#files-footer { padding: 12px; background: #181825; border-top: 1px solid #313244; text-align: center; }

/* Stage 3: result reuses GraphView sidebar + #cy */
#result { flex: 1; display: flex; }
#result-actions { position: fixed; bottom: 12px; right: 12px; z-index: 10; display: flex; gap: 6px; }
""";

    private static final String EDITOR_JS = """
// ---------------- Stage navigation ----------------
function showStage(n) {
  document.querySelectorAll('.stage').forEach(s => s.classList.remove('active'));
  document.querySelectorAll('#topnav .nav-btn').forEach(b => b.classList.remove('active'));
  document.getElementById('stage-' + n).classList.add('active');
  document.getElementById('nav-' + n).classList.add('active');
  if (n === 'design' && editorCy) { editorCy.resize(); editorCy.fit(null, 40); }
  if (n === 'result' && typeof cy !== 'undefined' && cy) { cy.resize(); cy.fit(null, 40); }
}

// ---------------- Stage 1: canvas editor ----------------
let editorCy = null;
let palette = [];
let seq = 0;
let selectedId = null;
let linkMode = false;
let linkSource = null;

function editorStyle() {
  return [
    { selector: 'node', style: { 'label':'data(name)', 'background-color':'data(bg)', 'shape':'data(shape)', 'color':'#fff', 'font-size':'11px', 'text-valign':'center', 'text-halign':'center', 'text-wrap':'wrap', 'border-width':1, 'border-color':'#555', 'padding':'10px' } },
    { selector: 'node[kind = "VM"]', style: { 'background-color':'#e8eaf6', 'color':'#1a237e', 'font-weight':'bold', 'text-valign':'top', 'border-color':'#3949ab', 'border-width':2, 'padding':'22px' } },
    { selector: 'node[kind = "Connector"]', style: { 'shape':'diamond', 'background-color':'#78909c', 'width':'34px', 'height':'34px', 'font-size':'9px' } },
    { selector: ':parent', style: { 'background-opacity':0.15 } },
    { selector: 'edge', style: { 'line-color':'#455a64', 'target-arrow-color':'#455a64', 'source-arrow-color':'#455a64', 'target-arrow-shape':'triangle', 'curve-style':'bezier', 'width':2, 'label':'data(port)', 'font-size':'9px', 'color':'#333', 'text-background-color':'#fff', 'text-background-opacity':0.9 } },
    { selector: 'edge[dir = "inout"]', style: { 'source-arrow-shape':'triangle' } },
    { selector: 'node:selected', style: { 'border-color':'#f9a825', 'border-width':4 } },
    { selector: 'edge:selected', style: { 'line-color':'#f9a825', 'target-arrow-color':'#f9a825', 'width':3 } },
    { selector: '.sel', style: { 'border-color':'#f9a825', 'border-width':3 } },
    { selector: '.bad', style: { 'border-color':'#f38ba8', 'border-width':4 } }
  ];
}

function colorFor(type) { return type==='VM'?'#e8eaf6':type==='App'?'#1565c0':type==='Data'?'#e65100':'#78909c'; }
function shapeFor(type) { return type==='VM'?'roundrectangle':type==='App'?'ellipse':type==='Data'?'barrel':'diamond'; }

async function initEditor() {
  const res = await fetch('/api/palette');
  palette = await res.json();
  const pal = document.getElementById('palette-items');
  pal.innerHTML = '';
  palette.forEach(p => {
    const b = document.createElement('button');
    b.className = 'pal-item';
    b.textContent = '+ ' + p.type + (p.container ? '  (container)' : '');
    b.onclick = () => addNode(p);
    pal.appendChild(b);
  });

  editorCy = cytoscape({ container: document.getElementById('cy-edit'), elements: [], style: editorStyle(),
    layout: { name: 'preset' }, boxSelectionEnabled: true, selectionType: 'single' });

  editorCy.on('tap', 'node', function(e) {
    if (linkMode) { handleLinkClick(e.target); return; }
    selectNode(e.target.id());
  });
  editorCy.on('tap', function(e) { if (e.target === editorCy && !linkMode) { selectedId = null; renderProps(); editorCy.nodes().removeClass('sel'); } });
}

// ---- selection / delete / duplicate ----
function selectAllNodes() { if (editorCy) editorCy.elements().select(); }

function deleteSelected() {
  if (!editorCy) return;
  let sel = editorCy.$(':selected');
  if (sel.length === 0 && selectedId) sel = editorCy.getElementById(selectedId);
  editorCy.remove(sel);
  selectedId = null;
  renderProps();
  setMsg('', '');
}

function duplicateSelected() {
  if (!editorCy) return;
  const sel = editorCy.$('node:selected');
  const toAdd = [];
  sel.forEach(n => {
    const src = n.data();
    const d = Object.assign({}, src);
    d.id = 'x' + (seq++);
    d.name = src.name + '_copy';
    d.attrs = Object.assign({}, src.attrs || {});
    d.ports = (src.ports || []).slice();
    const pos = n.position();
    toAdd.push({ group: 'nodes', data: d, position: { x: pos.x + 30, y: pos.y + 30 } });
  });
  editorCy.add(toAdd);
}

function addNode(p) {
  const id = 'x' + (seq++);
  const isConn = p.kind === 'connector';
  const data = { id: id, name: p.type + seq, type: p.type, kind: isConn ? 'Connector' : (p.container ? 'VM' : 'leaf'),
                 bg: colorFor(p.type), shape: shapeFor(p.type), attrs: {}, ports: [], external: false };
  // pre-seed required attribute keys so the user just fills values
  (p.requiredAttrs || []).forEach(k => data.attrs[k] = '');
  editorCy.add({ group: 'nodes', data: data, position: { x: 120 + Math.random()*300, y: 120 + Math.random()*250 } });
  selectNode(id);
}

function selectNode(id) {
  selectedId = id;
  editorCy.nodes().removeClass('sel');
  editorCy.getElementById(id).addClass('sel');
  renderProps();
}

function renderProps() {
  const box = document.getElementById('props-body');
  if (!selectedId) { box.innerHTML = '<p style="color:#6c7086;font-size:12px">Select a node, or add one from the palette.</p>'; return; }
  const n = editorCy.getElementById(selectedId);
  const d = n.data();
  let html = '<label>Name</label><input id="p-name" value="' + (d.name||'') + '">';

  if (d.kind === 'Connector') {
    html += '<label>External</label><input id="p-ext" value="' + (d.external?'true':'false') + '">';
  } else {
    // parent (nesting) -- choose a VM container
    html += '<label>Inside (VM)</label><select id="p-parent" style="width:100%;background:#11111b;color:#cdd6f4;border:1px solid #313244;border-radius:4px;padding:6px"><option value="">(top level)</option>';
    editorCy.nodes().forEach(o => { if (o.id() !== selectedId && o.data('kind') === 'VM') {
      const sel = (n.data('parent') === o.id()) ? ' selected' : '';
      html += '<option value="' + o.id() + '"' + sel + '>' + o.data('name') + '</option>';
    }});
    html += '</select>';
    html += '<label>Ports (comma separated)</label><input id="p-ports" value="' + (d.ports||[]).join(', ') + '">';
    html += '<h3 style="margin-top:14px">Attributes</h3>';
    const req = (palette.find(p => p.type === d.type) || {}).requiredAttrs || [];
    const keys = new Set([...req, ...Object.keys(d.attrs||{})]);
    keys.forEach(k => {
      const isReq = req.includes(k);
      html += '<label>' + k + (isReq ? ' <span class="req">*required</span>'
              : ' <span style="color:#6c7086;cursor:pointer" onclick="removeAttr(\\'' + k + '\\')">[remove]</span>') + '</label>';
      html += '<input data-attr="' + k + '" value="' + (d.attrs[k]||'') + '">';
    });
    // Free-form: add any attribute (Role, Tier, Env, ...) -- the model allows any key
    html += '<div style="margin-top:10px;display:flex;gap:4px">';
    html += '<input id="new-attr-key" placeholder="new attribute (e.g. Role)" style="flex:1">';
    html += '<button class="act-btn secondary" onclick="addAttr()">+ Add</button></div>';
  }
  html += '<div style="margin-top:14px"><button class="act-btn" onclick="applyProps()">Apply</button> <button class="act-btn secondary" onclick="deleteNode()">Delete</button></div>';
  box.innerHTML = html;
}

// Capture whatever is currently typed in the properties form into the node's data,
// so re-rendering (after add/remove attribute) never loses unsaved edits.
function captureProps() {
  const n = editorCy.getElementById(selectedId);
  if (!n) return null;
  const nameEl = document.getElementById('p-name');
  if (nameEl) n.data('name', nameEl.value);
  if (n.data('kind') !== 'Connector') {
    const attrs = {};
    document.querySelectorAll('#props-body input[data-attr]').forEach(inp => {
      if (inp.value.trim()) attrs[inp.getAttribute('data-attr')] = inp.value.trim();
    });
    n.data('attrs', attrs);
  }
  return n;
}

function addAttr() {
  const keyEl = document.getElementById('new-attr-key');
  const key = keyEl.value.trim();
  if (!key) return;
  const n = captureProps();
  const attrs = Object.assign({}, n.data('attrs') || {});
  if (!(key in attrs)) attrs[key] = '';
  n.data('attrs', attrs);
  renderProps();
}

function removeAttr(key) {
  const n = captureProps();
  const attrs = Object.assign({}, n.data('attrs') || {});
  delete attrs[key];
  n.data('attrs', attrs);
  renderProps();
}

function applyProps() {
  const n = editorCy.getElementById(selectedId);
  n.data('name', document.getElementById('p-name').value);
  if (n.data('kind') === 'Connector') {
    n.data('external', document.getElementById('p-ext').value.trim() === 'true');
  } else {
    const parentSel = document.getElementById('p-parent').value;
    n.move({ parent: parentSel || null });
    const ports = document.getElementById('p-ports').value.split(',').map(s => s.trim()).filter(s => s);
    n.data('ports', ports);
    const attrs = {};
    document.querySelectorAll('#props-body input[data-attr]').forEach(inp => {
      if (inp.value.trim()) attrs[inp.getAttribute('data-attr')] = inp.value.trim();
    });
    n.data('attrs', attrs);
  }
  editorCy.nodes().removeClass('bad');
  setMsg('', '');
}

function deleteNode() { if (selectedId) { editorCy.remove(editorCy.getElementById(selectedId)); selectedId = null; renderProps(); } }

function toggleLinkMode() {
  linkMode = !linkMode;
  linkSource = null;
  document.getElementById('link-btn').textContent = linkMode ? 'Linking: pick component, then connector (click to cancel)' : 'Draw link';
  document.getElementById('link-btn').style.background = linkMode ? '#1565c0' : '';
}

function handleLinkClick(node) {
  // First click: remember whatever was clicked (component OR connector).
  if (!linkSource) {
    if (node.data('kind') === 'VM') { setMsg('Links attach to App/Data components or connectors, not VMs.', 'error'); return; }
    linkSource = node; node.addClass('sel');
    setMsg('now click the other end (the ' + (node.data('kind') === 'Connector' ? 'component' : 'connector') + ')', '');
    return;
  }
  if (node.id() === linkSource.id()) return; // clicking the same node = no-op

  // Second click: figure out which end is the component and which is the connector.
  const a = linkSource, b = node;
  let comp, conn;
  if (a.data('kind') === 'Connector' && b.data('kind') !== 'Connector') { conn = a; comp = b; }
  else if (b.data('kind') === 'Connector' && a.data('kind') !== 'Connector') { conn = b; comp = a; }
  else { setMsg('A link must connect a component and a connector (one of each).', 'error'); return; }
  if (comp.data('kind') === 'VM') { setMsg('Pick an App/Data component, not a VM.', 'error'); return; }

  const ports = comp.data('ports') || [];
  let port = ports.length ? ports[0] : '';
  if (ports.length > 1) { const p = prompt('Which port of ' + comp.data('name') + '? (' + ports.join(', ') + ')', ports[0]); if (p) port = p; }
  const portRef = comp.data('name') + (port ? '.' + port : '');

  // Flow direction (component's perspective): out, in, or inout
  let dir = (prompt('Direction? out = component sends, in = component receives, inout = both', 'inout') || 'inout').trim().toLowerCase();
  if (dir !== 'in' && dir !== 'out') dir = 'inout';

  // Draw IN reversed (arrow points at the component); out/inout drawn component->connector.
  const src = (dir === 'in') ? conn.id() : comp.id();
  const tgt = (dir === 'in') ? comp.id() : conn.id();
  editorCy.add({ group: 'edges', data: { id: 'l'+(seq++), source: src, target: tgt, port: portRef, connector: conn.data('name'), dir: dir } });
  editorCy.nodes().removeClass('sel');
  linkSource = null;
  toggleLinkMode();
}

// editorCy -> diagram domain structure
function serializeDiagram() {
  const components = [], connectors = [], links = [];
  editorCy.nodes().forEach(n => {
    const d = n.data();
    if (d.kind === 'Connector') {
      connectors.push({ name: d.name, external: !!d.external });
    } else {
      components.push({ id: d.id, name: d.name, type: d.type, parent: n.data('parent') || null, attributes: d.attrs || {}, ports: d.ports || [] });
    }
  });
  editorCy.edges().forEach(e => {
    links.push({ port: e.data('port'), connector: e.data('connector'), direction: e.data('dir') || 'inout' });
  });
  return { name: 'model', components, connectors, links };
}

function setMsg(text, cls) { const m = document.getElementById('design-msg'); m.textContent = text; m.className = cls; }

async function generate(endpoint, key) {
  setMsg('generating...', '');
  const res = await fetch(endpoint, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(serializeDiagram()) });
  const data = await res.json();
  if (data.error) {
    setMsg(data.error + (data.problems ? ': ' + data.problems.join('; ') : ''), 'error');
    // highlight offending nodes by name
    editorCy.nodes().removeClass('bad');
    (data.problemNodes || []).forEach(nm => editorCy.nodes('[name = "' + nm + '"]').addClass('bad'));
    return null;
  }
  setMsg('ok', 'ok');
  return data[key];
}

async function downloadYaml() {
  const yaml = await generate('/api/diagram-to-yaml', 'yaml');
  if (yaml == null) return;
  document.getElementById('yaml').value = yaml;
  download('architecture.arch.yaml', yaml);
}
async function downloadSecdsl() {
  const secdsl = await generate('/api/diagram-to-secdsl', 'secdsl');
  if (secdsl == null) return;
  document.getElementById('secdsl').value = secdsl;
  download('security.secdsl', secdsl);
}

function download(filename, text) {
  const a = document.createElement('a');
  a.href = 'data:text/plain;charset=utf-8,' + encodeURIComponent(text);
  a.download = filename;
  a.click();
}

function uploadInto(id, ev) {
  const file = ev.target.files[0]; if (!file) return;
  const reader = new FileReader();
  reader.onload = () => { document.getElementById(id).value = reader.result; };
  reader.readAsText(file);
}

// ---------------- Stage 2 -> 3: merge ----------------
async function runMerge() {
  const status = document.getElementById('merge-status');
  status.className = ''; status.textContent = 'merging...';
  try {
    const res = await fetch('/api/merge', { method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ arch: document.getElementById('yaml').value, rules: document.getElementById('secdsl').value }) });
    const data = await res.json();
    if (data.error) { status.className = 'error'; status.textContent = data.error; return; }
    if (data.conformanceErrors && data.conformanceErrors.length) {
      status.className = 'error'; status.textContent = data.conformanceErrors.length + ' conformance error(s)';
      showStage('result'); renderWarnings(data.conformanceErrors); return;
    }
    showStage('result');
    initGraph({ nodes: data.nodes, edges: data.edges }, data.coverage, data.warnings);
    status.className = 'ok'; status.textContent = 'ok';
  } catch (e) { status.className = 'error'; status.textContent = 'failed: ' + e.message; }
}

// Keyboard shortcuts -- only on the Design stage, and never while typing in a field
document.addEventListener('keydown', function(e) {
  if (!document.getElementById('stage-design').classList.contains('active')) return;
  const t = document.activeElement;
  if (t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.tagName === 'SELECT')) return;
  if (e.key === 'Delete' || e.key === 'Backspace') { e.preventDefault(); deleteSelected(); }
  else if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'a') { e.preventDefault(); selectAllNodes(); }
  else if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'd') { e.preventDefault(); duplicateSelected(); }
});

window.addEventListener('load', initEditor);
""";

    private static final String TEMPLATE = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>SAM4C Studio</title>
<style>
{{CSS}}
{{STUDIO_CSS}}
</style>
</head>
<body>

<div id="topnav">
  <span class="brand">&#9650; SAM4C Studio</span>
  <button id="nav-design" class="nav-btn active" onclick="showStage('design')">1 &middot; Design</button>
  <button id="nav-files"  class="nav-btn" onclick="showStage('files')">2 &middot; Files</button>
  <button id="nav-result" class="nav-btn" onclick="showStage('result')">3 &middot; Result</button>
</div>

<!-- STAGE 1: DESIGN -->
<div id="stage-design" class="stage active">
  <div id="palette">
    <h3>Palette</h3>
    <div id="palette-items"></div>
    <h3 style="margin-top:16px">Links</h3>
    <button id="link-btn" class="pal-item" onclick="toggleLinkMode()">Draw link</button>

    <h3 style="margin-top:16px">Edit</h3>
    <button class="pal-item" onclick="selectAllNodes()">Select all (Ctrl+A)</button>
    <button class="pal-item" onclick="duplicateSelected()">Duplicate (Ctrl+D)</button>
    <button class="pal-item" onclick="deleteSelected()" style="border-color:#f38ba8;color:#f38ba8">Delete selected (Del)</button>
    <p style="font-size:10px;color:#6c7086;margin-top:8px;line-height:1.4">Drag on empty canvas to box-select. Shift-click to add to selection.</p>
  </div>
  <div id="cy-edit"></div>
  <div id="props">
    <h3>Properties</h3>
    <div id="props-body"><p style="color:#6c7086;font-size:12px">Select a node, or add one from the palette.</p></div>
  </div>
  <div id="design-actions">
    <button class="act-btn" onclick="downloadYaml()">&#11015; Download YAML</button>
    <button class="act-btn" onclick="downloadSecdsl()">&#11015; Download SecDSL</button>
    <button class="act-btn secondary" onclick="showStage('files')">Files &rarr;</button>
    <span id="design-msg"></span>
  </div>
</div>

<!-- STAGE 2: FILES -->
<div id="stage-files" class="stage">
  <div id="files">
    <div id="files-cols">
      <div class="file-col">
        <h3>architecture.arch.yaml</h3>
        <textarea id="yaml" spellcheck="false"></textarea>
        <div class="file-bar">
          <label class="act-btn secondary">&#11014; Upload<input type="file" style="display:none" onchange="uploadInto('yaml', event)"></label>
          <button class="act-btn secondary" onclick="download('architecture.arch.yaml', document.getElementById('yaml').value)">&#11015; Download</button>
        </div>
      </div>
      <div class="file-col">
        <h3>security.secdsl</h3>
        <textarea id="secdsl" spellcheck="false"></textarea>
        <div class="file-bar">
          <label class="act-btn secondary">&#11014; Upload<input type="file" style="display:none" onchange="uploadInto('secdsl', event)"></label>
          <button class="act-btn secondary" onclick="download('security.secdsl', document.getElementById('secdsl').value)">&#11015; Download</button>
        </div>
      </div>
    </div>
    <div id="files-footer">
      <button class="act-btn" onclick="runMerge()">Merge &rarr;</button>
      <span id="merge-status" style="font-size:11px;margin-left:10px;color:#6c7086"></span>
    </div>
  </div>
</div>

<!-- STAGE 3: RESULT -->
<div id="stage-result" class="stage">
  <div id="result">
    {{RESULT_SIDEBAR}}
    <div id="cy-container">
      <div id="cy"></div>
      <div id="controls">
        <button class="ctrl-btn" onclick="fitGraph()">Fit</button>
        <button class="ctrl-btn" onclick="relayout()">Re-layout</button>
      </div>
    </div>
  </div>
</div>

<script>
{{GRAPHVIEW_JS}}
{{EDITOR_JS}}
</script>
</body>
</html>
""";
}
