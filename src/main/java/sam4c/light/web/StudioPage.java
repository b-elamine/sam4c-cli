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
/* Clean Light theme -- inherits --bg/--surface/--border/--text/--muted/--accent from GraphView */
#topnav { position: fixed; top: 0; left: 0; right: 0; height: 50px; background: var(--surface); border-bottom: 1px solid var(--border); display: flex; align-items: center; gap: 6px; padding: 0 16px; z-index: 50; box-shadow: 0 1px 3px rgba(0,0,0,.03); }
#topnav .brand { color: var(--text); font-weight: 700; font-size: 14px; margin-right: 20px; letter-spacing: -0.2px; }
#topnav .nav-btn { background: var(--surface); color: var(--muted); border: 1px solid transparent; padding: 6px 14px; border-radius: 8px; cursor: pointer; font-size: 12px; font-weight: 500; transition: all .12s; }
#topnav .nav-btn:hover { color: var(--text); background: var(--bg); }
#topnav .nav-btn.active { background: var(--accent-soft); color: var(--accent); border-color: var(--border); }
.stage { position: fixed; top: 50px; left: 0; right: 0; bottom: 0; display: none; }
.stage.active { display: flex; }

/* Stage 1: design */
#palette { width: 184px; background: var(--surface); border-right: 1px solid var(--border); padding: 14px; overflow-y: auto; }
#palette h3 { font-size: 11px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.6px; color: var(--muted); margin-bottom: 8px; }
.pal-item { display: block; width: 100%; text-align: left; margin: 5px 0; padding: 9px 11px; border-radius: 8px; border: 1px solid var(--border-strong); background: var(--surface); color: var(--text); cursor: pointer; font-size: 12px; font-weight: 500; transition: all .12s; }
.pal-item:hover { border-color: var(--accent); color: var(--accent); }
#cy-edit { flex: 1; background: #fafbfc; }
#props { width: 296px; background: var(--surface); color: var(--text); border-left: 1px solid var(--border); padding: 16px; overflow-y: auto; }
#props h3 { font-size: 11px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.6px; color: var(--muted); margin-bottom: 10px; }
#props label { display: block; font-size: 11px; font-weight: 500; color: var(--muted); margin: 10px 0 3px; }
#props input, #props select { width: 100%; background: var(--surface); color: var(--text); border: 1px solid var(--border-strong); border-radius: 6px; padding: 7px 8px; font-size: 12px; font-family: inherit; }
#props input:focus, #props select:focus { outline: none; border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-soft); }
#props .req { color: var(--warn); font-weight: 600; }
#design-actions { position: fixed; bottom: 0; left: 184px; right: 296px; padding: 12px; background: var(--surface); border-top: 1px solid var(--border); text-align: center; }
.act-btn { background: var(--accent); color: #fff; border: none; padding: 8px 16px; border-radius: 8px; cursor: pointer; font-size: 13px; font-weight: 500; margin: 0 4px; transition: all .12s; }
.act-btn:hover { filter: brightness(1.08); }
.act-btn.secondary { background: var(--surface); color: var(--text); border: 1px solid var(--border-strong); }
.act-btn.secondary:hover { border-color: var(--accent); color: var(--accent); filter: none; }
#design-msg { font-size: 11.5px; margin-left: 10px; color: var(--muted); }
#design-msg.error { color: var(--danger); }
#design-msg.ok { color: var(--ok); }

/* Stage 2: files */
#files { flex: 1; display: flex; flex-direction: column; background: var(--bg); }
#files-cols { flex: 1; display: flex; }
.file-col { flex: 1; display: flex; flex-direction: column; border-right: 1px solid var(--border); background: var(--surface); }
.file-col:last-child { border-right: none; }
.file-col h3 { font-size: 11px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.6px; color: var(--muted); padding: 12px 14px 6px; }
.file-col textarea { flex: 1; margin: 0 14px; background: #fbfbfd; color: var(--text); border: 1px solid var(--border-strong); border-radius: 8px; padding: 12px; font-family: 'SF Mono', 'JetBrains Mono', Consolas, monospace; font-size: 12.5px; line-height: 1.6; resize: none; }
.file-col textarea:focus { outline: none; border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-soft); }
.file-bar { padding: 10px 14px; display: flex; gap: 8px; }
.file-bar .act-btn { font-size: 12px; padding: 6px 12px; }
#files-footer { padding: 14px; background: var(--surface); border-top: 1px solid var(--border); text-align: center; }

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
let schema = {};   // per-type editable field descriptors, fetched from /api/schema (metamodel-derived)
let seq = 0;
let selectedId = null;
let linkMode = false;
let linkSource = null;

function editorStyle() {
  return [
    { selector: 'node', style: { 'label':'data(name)', 'background-color':'data(bg)', 'shape':'data(shape)', 'color':'#ffffff', 'font-size':'10px', 'font-weight':500, 'text-valign':'center', 'text-halign':'center', 'text-wrap':'wrap', 'text-max-width':'90px', 'text-background-color':'#0f172a', 'text-background-opacity':0.6, 'text-background-padding':'3px', 'text-background-shape':'roundrectangle', 'border-width':1, 'border-color':'rgba(15,23,42,0.15)', 'padding':'10px', 'z-index':30 } },
    { selector: ':parent', style: { 'background-opacity':0.18, 'color':'#334155', 'font-weight':600, 'text-valign':'top', 'text-margin-y':1, 'text-background-opacity':0, 'border-color':'#cbd5e1', 'border-width':1, 'padding':'8px' } },
    { selector: 'node[kind = "Connector"]', style: { 'shape':'diamond', 'background-color':'#64748b', 'width':'34px', 'height':'34px', 'font-size':'9px' } },
    { selector: ':parent', style: { 'background-opacity':0.15 } },
    { selector: 'edge', style: { 'line-color':'#455a64', 'target-arrow-color':'#455a64', 'source-arrow-color':'#455a64', 'target-arrow-shape':'triangle', 'curve-style':'bezier', 'width':2, 'label':'data(port)', 'font-size':'9px', 'color':'#333', 'text-background-color':'#fff', 'text-background-opacity':0.9, 'z-index':1 } },
    { selector: 'edge[dir = "inout"]', style: { 'source-arrow-shape':'triangle' } },
    { selector: 'node:selected', style: { 'border-color':'#f9a825', 'border-width':4 } },
    { selector: 'edge:selected', style: { 'line-color':'#f9a825', 'target-arrow-color':'#f9a825', 'width':3 } },
    { selector: '.sel', style: { 'border-color':'#f9a825', 'border-width':3 } },
    { selector: '.bad', style: { 'border-color':'#f38ba8', 'border-width':4 } }
  ];
}

function colorFor(type) {
  const hosts = ['VM','PM','Worker'];
  if (hosts.includes(type)) return '#e8eaf6';
  if (type==='Zone') return '#f1f5f9';
  if (type==='Colocation') return '#fef9c3';
  if (type==='HostPool') return '#eef2ff';
  if (type==='App') return '#1565c0';
  if (type==='Data') return '#e65100';
  return '#78909c';
}
function shapeFor(type) { return (type==='App')?'ellipse':(type==='Data')?'barrel':(type==='Connector')?'diamond':'roundrectangle'; }

// type classification (mirrors the metamodel roles)
const HOST_TYPES = ['VM','PM','Worker'];
const GROUP_TYPES = ['Zone','Colocation','HostPool'];
function isHostType(t){ return HOST_TYPES.includes(t); }
function isGroupType(t){ return GROUP_TYPES.includes(t); }
function isContainerType(t){ return isHostType(t) || isGroupType(t); }
function isWorkloadType(t){ return t==='App' || t==='Data'; }
function isConnectorType(t){ return t==='Connector'; }

// build <option> list, marking `current` selected (includes a blank choice)
function optionList(values, current){
  return ['',...values].map(v => '<option value="'+v+'"'+(v===(current||'')?' selected':'')+'>'+(v||'(unset)')+'</option>').join('');
}
// set a node-data key if val is non-empty, else remove it (so unset fields don't serialize)
function setOrDel(n, key, val){ if(val!==undefined && val!==null && val!=='') n.data(key, val); else n.removeData(key); }

async function initEditor() {
  const res = await fetch('/api/palette');
  palette = await res.json();
  schema = await (await fetch('/api/schema')).json();   // metamodel-derived editable fields per type
  const pal = document.getElementById('palette-items');
  pal.innerHTML = '';
  palette.forEach(p => {
    const b = document.createElement('button');
    b.className = 'pal-item';
    b.textContent = '+ ' + p.type + (p.container ? '  (holds children)' : '');
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
                 bg: colorFor(p.type), shape: shapeFor(p.type), attrs: {}, ports: [] };
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

// Render a single metamodel field by its kind. The schema (from /api/schema) is the single
// source of truth: add a field to the M2 metamodel and it appears here automatically.
function fieldWidget(f, d) {
  const v = d[f.name];
  const req = f.required ? ' <span class="req">*required</span>' : '';
  const lbl = '<label>' + f.name + req + '</label>';
  if (f.kind === 'bool')
    return lbl + '<input type="checkbox" data-f="' + f.name + '" data-k="bool"' + (v ? ' checked' : '') + '>';
  if (f.kind === 'enum')
    return lbl + '<select data-f="' + f.name + '" data-k="enum">' + optionList(f.allowed || [], v) + '</select>';
  if (f.kind === 'int')
    return lbl + '<input data-f="' + f.name + '" data-k="int" value="' + (v ?? '') + '">';
  if (f.kind === 'string')
    return lbl + '<input data-f="' + f.name + '" data-k="string" value="' + (v || '') + '">';
  if (f.kind === 'map') {
    const m = v || {};
    let h = lbl;
    Object.keys(m).forEach(k => {
      h += '<div style="display:flex;gap:3px;margin:2px 0;align-items:center">'
         + '<span style="flex:1;font-size:11px;color:var(--muted)">' + k + '</span>'
         + '<input data-mapval="' + f.name + '" data-mk="' + k + '" value="' + (m[k] ?? '') + '" style="flex:1">'
         + '<span style="cursor:pointer;color:var(--danger);padding:0 4px" onclick="removeMapEntry(\\'' + f.name + '\\',\\'' + k + '\\')">&times;</span></div>';
    });
    h += '<button class="act-btn secondary" onclick="addMapEntry(\\'' + f.name + '\\')">+ ' + f.name + ' entry</button>';
    return h;
  }
  if (f.kind === 'list') {
    const arr = v || [];
    let h = lbl;
    arr.forEach((item, i) => {
      h += '<div style="display:flex;gap:3px;margin:2px 0;align-items:center">'
         + '<span style="flex:1;font-size:12px">' + item + '</span>'
         + '<span style="cursor:pointer;color:var(--danger);padding:0 4px" onclick="removeListItem(\\'' + f.name + '\\',' + i + ')">&times;</span></div>';
    });
    h += '<button class="act-btn secondary" onclick="addListItem(\\'' + f.name + '\\')">+ ' + f.name + '</button>';
    return h;
  }
  return '';
}

function renderProps() {
  const box = document.getElementById('props-body');
  if (!selectedId) { box.innerHTML = '<p style="color:var(--muted);font-size:12px">Select a node, or add one from the palette.</p>'; return; }
  const n = editorCy.getElementById(selectedId);
  const d = n.data();
  const t = d.type;
  let html = '<label>Name</label><input id="p-name" value="' + (d.name||'') + '">';

  // parent (nesting) -- any container (host or group); connectors are not contained
  if (!isConnectorType(t)) {
    html += '<label>Inside (host or group)</label><select id="p-parent"><option value="">(top level)</option>';
    editorCy.nodes().forEach(o => { if (o.id() !== selectedId && isContainerType(o.data('type'))) {
      const sel = (n.data('parent') === o.id()) ? ' selected' : '';
      html += '<option value="' + o.id() + '"' + sel + '>' + o.data('name') + '</option>';
    }});
    html += '</select>';
  }

  // metamodel-driven fields for this type (single source of truth)
  (schema[t] || []).forEach(f => { html += fieldWidget(f, d); });

  // deployedOn is a REFERENCE (host placement), resolved against live host nodes -> kept structural
  if (isWorkloadType(t)) {
    html += '<label>Deployed on (host)</label><select id="p-deployedOn"><option value="">(none)</option>';
    editorCy.nodes().forEach(o => { if (isHostType(o.data('type'))) {
      html += '<option' + (d.deployedOn===o.data('name')?' selected':'') + '>' + o.data('name') + '</option>'; }});
    html += '</select>';

    html += '<h3 style="margin-top:14px">Ports</h3>';
    (d.ports||[]).forEach((p,i) => {
      html += '<div style="display:flex;gap:3px;margin:2px 0">'
            + '<input data-pn="'+i+'" placeholder="name" value="'+(p.name||'')+'" style="flex:2">'
            + '<input data-pnum="'+i+'" placeholder="port" value="'+(p.number??'')+'" style="flex:1">'
            + '<select data-pp="'+i+'" style="flex:1">'+optionList(['tcp','udp','http','grpc'],p.protocol)+'</select>'
            + '<span style="cursor:pointer;color:var(--danger);padding:4px" onclick="removePort('+i+')">&times;</span></div>';
    });
    html += '<button class="act-btn secondary" onclick="addPort()">+ port</button>';
  }

  // free-form classification attributes (the security bridge) -- not in the architecture schema
  if (!isConnectorType(t)) {
    html += '<h3 style="margin-top:14px">Attributes</h3>';
    const req = (palette.find(p => p.type === t) || {}).requiredAttrs || [];
    const keys = new Set([...req, ...Object.keys(d.attrs||{})]);
    keys.forEach(k => {
      const isReq = req.includes(k);
      html += '<label>' + k + (isReq ? ' <span class="req">*required</span>'
              : ' <span style="color:var(--muted);cursor:pointer" onclick="removeAttr(\\'' + k + '\\')">[remove]</span>') + '</label>';
      html += '<input data-attr="' + k + '" value="' + ((d.attrs||{})[k]||'') + '">';
    });
    html += '<div style="margin-top:8px;display:flex;gap:4px">'
          + '<input id="new-attr-key" placeholder="new attribute (e.g. Role)" style="flex:1">'
          + '<button class="act-btn secondary" onclick="addAttr()">+ Add</button></div>';
  }
  html += '<div style="margin-top:14px"><button class="act-btn" onclick="applyProps()">Apply</button> <button class="act-btn secondary" onclick="deleteNode()">Delete</button></div>';
  box.innerHTML = html;
}

// Capture whatever is currently typed in the properties form into the node's data,
// so re-rendering (after add/remove attribute) never loses unsaved edits.
// Read everything currently in the form into the node's data (so re-rendering after
// add/remove of an attribute or port never loses unsaved edits).
function captureForm() {
  const n = editorCy.getElementById(selectedId);
  if (!n) return null;
  const t = n.data('type');
  const val = id => { const e = document.getElementById(id); return e ? e.value.trim() : undefined; };

  const nm = val('p-name'); if (nm !== undefined) n.data('name', nm);

  // metamodel-driven fields (same loop as renderProps, in reverse)
  (schema[t] || []).forEach(f => {
    if (f.kind === 'bool') {
      const e = document.querySelector('#props-body input[data-f="' + f.name + '"]');
      if (e && e.checked) n.data(f.name, true); else n.removeData(f.name);
    } else if (f.kind === 'map') {
      const m = {};
      document.querySelectorAll('#props-body input[data-mapval="' + f.name + '"]').forEach(inp => {
        const v = inp.value.trim(); if (v !== '') m[inp.getAttribute('data-mk')] = numIfPossible(v);
      });
      if (Object.keys(m).length) n.data(f.name, m); else n.removeData(f.name);
    } else if (f.kind === 'list') {
      // list items are not edited in place (added/removed only) -> keep current node data as-is
    } else {  // enum | string | int
      const e = document.querySelector('#props-body [data-f="' + f.name + '"]');
      let v = e ? e.value.trim() : '';
      if (f.kind === 'int' && v !== '') { const x = parseInt(v); v = isNaN(x) ? '' : x; }
      setOrDel(n, f.name, v);
    }
  });

  if (isConnectorType(t)) return n;

  const attrs = {};
  document.querySelectorAll('#props-body input[data-attr]').forEach(inp => {
    if (inp.value.trim()) attrs[inp.getAttribute('data-attr')] = inp.value.trim();
  });
  n.data('attrs', attrs);

  if (isWorkloadType(t)) {
    setOrDel(n, 'deployedOn', val('p-deployedOn'));
    capturePorts(n);
  }
  return n;
}

// map values that look like integers are stored as numbers (e.g. scale.replicas: 3)
function numIfPossible(v) { return (/^-?\\d+$/.test(v)) ? parseInt(v) : v; }

function capturePorts(n) {
  const rows = {};
  document.querySelectorAll('#props-body input[data-pn]').forEach(i => { const k=i.getAttribute('data-pn'); (rows[k]=rows[k]||{}).name=i.value.trim(); });
  document.querySelectorAll('#props-body input[data-pnum]').forEach(i => { const k=i.getAttribute('data-pnum'); const x=parseInt(i.value.trim()); if(!isNaN(x)) (rows[k]=rows[k]||{}).number=x; });
  document.querySelectorAll('#props-body select[data-pp]').forEach(s => { const k=s.getAttribute('data-pp'); if(s.value) (rows[k]=rows[k]||{}).protocol=s.value; });
  const ports = Object.keys(rows).sort((a,b)=>a-b).map(k=>rows[k]).filter(p=>p.name);
  n.data('ports', ports);
}

function addAttr() {
  const key = (document.getElementById('new-attr-key').value || '').trim();
  if (!key) return;
  const n = captureForm();
  const attrs = Object.assign({}, n.data('attrs') || {});
  if (!(key in attrs)) attrs[key] = '';
  n.data('attrs', attrs);
  renderProps();
}
function removeAttr(key) {
  const n = captureForm();
  const attrs = Object.assign({}, n.data('attrs') || {});
  delete attrs[key];
  n.data('attrs', attrs);
  renderProps();
}
function addPort() {
  const n = captureForm();
  const ports = (n.data('ports') || []).slice();
  ports.push({ name: 'p' + (ports.length + 1) });
  n.data('ports', ports);
  renderProps();
}
function removePort(i) {
  const n = captureForm();
  const ports = (n.data('ports') || []).slice();
  ports.splice(i, 1);
  n.data('ports', ports);
  renderProps();
}

// generic map-field editing (scale, resources, storage, config, health, trigger, placement, capacity)
function addMapEntry(field) {
  const n = captureForm();
  const key = (prompt('Key for "' + field + '" (e.g. replicas, cpu, size):', '') || '').trim();
  if (!key) return;
  const m = Object.assign({}, n.data(field) || {});
  if (!(key in m)) m[key] = '';
  n.data(field, m);
  renderProps();
}
function removeMapEntry(field, key) {
  const n = captureForm();
  const m = Object.assign({}, n.data(field) || {});
  delete m[key];
  if (Object.keys(m).length) n.data(field, m); else n.removeData(field);
  renderProps();
}

// generic list-field editing (secrets)
function addListItem(field) {
  const n = captureForm();
  const v = (prompt('Add to "' + field + '":', '') || '').trim();
  if (!v) return;
  const arr = (n.data(field) || []).slice();
  arr.push(v);
  n.data(field, arr);
  renderProps();
}
function removeListItem(field, i) {
  const n = captureForm();
  const arr = (n.data(field) || []).slice();
  arr.splice(i, 1);
  if (arr.length) n.data(field, arr); else n.removeData(field);
  renderProps();
}

function applyProps() {
  const n = captureForm();
  if (!n) return;
  if (!isConnectorType(n.data('type'))) {
    const p = document.getElementById('p-parent');
    if (p) n.move({ parent: p.value || null });
  }
  editorCy.nodes().removeClass('bad');
  setMsg('applied', 'ok');
}

function deleteNode() { if (selectedId) { editorCy.remove(editorCy.getElementById(selectedId)); selectedId = null; renderProps(); } }

function toggleLinkMode() {
  linkMode = !linkMode;
  linkSource = null;
  const b = document.getElementById('link-btn');
  b.textContent = linkMode ? 'Linking: pick one end, then the other (click to cancel)' : 'Draw link';
  b.style.background = linkMode ? 'var(--accent)' : '';
  b.style.color = linkMode ? '#fff' : '';
  b.style.borderColor = linkMode ? 'var(--accent)' : '';
}

function handleLinkClick(node) {
  // First click: remember whatever was clicked (workload OR connector).
  if (!linkSource) {
    if (isContainerType(node.data('type'))) { setMsg('Links attach to App/Data components or connectors, not hosts or groups.', 'error'); return; }
    linkSource = node; node.addClass('sel');
    setMsg('now click the other end (the ' + (isConnectorType(node.data('type')) ? 'component' : 'connector') + ')', '');
    return;
  }
  if (node.id() === linkSource.id()) return; // clicking the same node = no-op

  // Second click: figure out which end is the component and which is the connector.
  const a = linkSource, b = node;
  let comp, conn;
  if (isConnectorType(a.data('type')) && !isConnectorType(b.data('type'))) { conn = a; comp = b; }
  else if (isConnectorType(b.data('type')) && !isConnectorType(a.data('type'))) { conn = b; comp = a; }
  else { setMsg('A link must connect a component and a connector (one of each).', 'error'); return; }
  if (!isWorkloadType(comp.data('type'))) { setMsg('Pick an App/Data component.', 'error'); return; }

  // A link attaches to a PORT (portRef = "Component.port"). Ports are objects {name,number,protocol}.
  let ports = comp.data('ports') || [];
  let port;
  if (ports.length === 0) {
    port = (prompt('Name a port on "' + comp.data('name') + '" for this link:', 'p1') || '').trim();
    if (!port) { setMsg('Link cancelled -- a port name is required.', 'error'); linkSource.removeClass('sel'); linkSource = null; return; }
    comp.data('ports', ports.concat([{ name: port }]));   // add the new port
  } else if (ports.length === 1) {
    port = ports[0].name;
  } else {
    const names = ports.map(p => p.name);
    port = (prompt('Which port of "' + comp.data('name') + '"? (' + names.join(', ') + ')', names[0]) || names[0]).trim();
  }
  const portRef = comp.data('name') + '.' + port;

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
    const fields = schema[d.type] || [];   // metamodel-declared fields for this type
    if (isConnectorType(d.type)) {
      const c = { name: d.name };
      fields.forEach(f => { if (d[f.name] !== undefined) c[f.name] = d[f.name]; });
      connectors.push(c);
    } else {
      const comp = { id: d.id, name: d.name, type: d.type, parent: n.data('parent') || null,
                     attributes: d.attrs || {}, ports: d.ports || [] };
      fields.forEach(f => { if (d[f.name] !== undefined) comp[f.name] = d[f.name]; });
      if (d.deployedOn) comp.deployedOn = d.deployedOn;   // reference, not a schema attribute
      components.push(comp);
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
    <button class="pal-item" onclick="deleteSelected()" style="border-color:var(--danger);color:var(--danger)">Delete selected (Del)</button>
    <p style="font-size:10px;color:var(--muted);margin-top:8px;line-height:1.4">Drag on empty canvas to box-select. Shift-click to add to selection.</p>
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
