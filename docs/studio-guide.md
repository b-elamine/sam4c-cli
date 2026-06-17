# SAM4C Studio Guide

The Studio is the visual, browser based way to use the tool. Start it with:

```bash
java -jar target/sam4c-cli.jar --serve
# then open http://localhost:8080
```

It has three stages, shown as buttons in the top bar: Design, Files, Result. You move
between them freely; your work is kept while you switch.

```
Design  ->  Files  ->  Result
(draw)     (edit text)  (see the security graph)
```

---

## Top bar

| Button | What it does |
|--------|--------------|
| Design | Opens stage 1, the drawing canvas. |
| Files  | Opens stage 2, the text editors for the YAML and the security rules. |
| Result | Opens stage 3, the merged security graph. |

The active stage is highlighted. Switching never loses what you have done.

---

## Stage 1: Design

Three columns: the palette on the left, the canvas in the middle, the properties panel on
the right. A bar of actions sits at the bottom.

### Palette (left column)

The palette lists the building blocks you can place. Each item is a button; clicking it adds
that element to the canvas. The list comes straight from the model, so it always matches what
the tool supports.

Element buttons (a "(holds children)" tag means it can hold other elements inside it):

| Button | Meaning |
|--------|---------|
| App    | A stateless service (something that runs and does not keep its own data). |
| Data   | A stateful store (a database or anything that keeps data). |
| VM     | A virtual machine that other things run on (a host). |
| PM | A physical machine (bare metal host). |
| Worker | A host the platform gives you and manages. |
| Zone   | A boundary that groups elements (an isolation or admin area). |
| Colocation | A set of elements that run together and share a network (think of a pod). |
| HostPool | A group of hosts (a compute pool or cluster). |
| Connector | A communication channel that components attach to through their ports. |

Below the element buttons:

| Button | What it does |
|--------|--------------|
| Draw link | Turns on link mode. Click one end, then the other, to wire a component to a connector. Click the button again to cancel. |
| Select all (Ctrl+A) | Selects every element on the canvas. |
| Duplicate (Ctrl+D) | Copies the selected element(s). |
| Delete selected (Del) | Removes the selected element(s). |

Tip shown in the panel: drag on an empty part of the canvas to box select several elements at
once; hold Shift and click to add an element to the selection.

### Canvas (middle)

This is where the architecture is drawn. You can:

- Drag elements to move them.
- Drop a component inside a host or group (set its parent in the properties panel).
- Click an element to select it and edit it on the right.
- Draw links between components and connectors (see Draw link above).

Containers (VM, Zone, Colocation, HostPool, and the other hosts) are drawn as boxes with
their label at the top, and the elements inside them sit within the box.

### Properties panel (right column)

When you select an element, this panel shows its editable fields. The fields shown depend on
the kind of element. You never have to remember which fields a type supports: the panel is
generated from the model definition itself, so the form always shows exactly the fields that
type can carry, and adding a field to the model makes it appear here automatically.

A field is shown using a widget that matches its type:

- a fixed choice (for example Runtime) is a dropdown of the allowed values,
- a yes/no field (for example Persistent) is a checkbox,
- a free text or number field is a text box,
- a structured field (a map such as Scale or Resources) shows one row per entry, with a "+ ... entry" button that asks for the key,
- a list field (such as Secrets) shows one row per item, with a "+ ..." button that asks for the value.

Common to all (except connectors):

| Field | Meaning |
|-------|---------|
| Name | The element's name. |
| Inside (host or group) | A dropdown to put this element inside a host or group (or leave it at top level). |
| Attributes | Free form tags such as `Domain`, `Role`, `DataClass`. Required ones are marked. Use "+ Add" to add a new tag, and "[remove]" to drop one. These tags are what the security rules match against. |

For a workload (App or Data):

| Field | Meaning |
|-------|---------|
| Runtime | How it is packaged: container, process, or function. |
| Exposure | How reachable it is: none, internal, or external. |
| Lifecycle | How it runs: continuous, batch, or scheduled. |
| Schedule | The cron expression, when Lifecycle is scheduled. |
| Image | The artifact or image it runs, for example `registry/api:1.4`. |
| Scale | A map. Common keys: `replicas`, `min`, `max`, `metric`. |
| Resources | A map of requested resources, for example `cpu`, `memory`. |
| Config | A map of configuration values (key to value). |
| Secrets | A list of secret names the workload needs. |
| Health | A map describing the health check, for example `path`, `port`. |
| Trigger | A map describing what invokes the workload, for example `kind`, `source`. |
| Placement | A map of placement hints, for example `zone`, `affinity`, `scope`. |
| Spread | none, host, or zone. How the copies fan out: none (no constraint), host (different machines), zone (different data centers). Used by the Availability check. |
| Deployed on (host) | A dropdown to place this workload on a specific host. |
| Ports | One row per port, each with a name, a port number, and a protocol. Use "+ port" to add a row and the "x" to remove one. |

For a Data element, two extra fields:

| Field | Meaning |
|-------|---------|
| Persistent | Tick if the data must survive restarts. |
| Storage | A map describing the disk, for example `size`, `class`. |

For a Colocation:

| Field | Meaning |
|-------|---------|
| Share network | Tick if the members share one network. |
| Share storage | Tick if the members share storage. |
| Scale | A map. The group scales as one unit. |
| Spread | none, host, or zone. How the unit's copies fan out across failure domains. |

For a Zone: a Boundary field (the boundary name or level).

For a Connector:

| Field | Meaning |
|-------|---------|
| External | Tick if the channel comes from outside the system (for example the internet). |
| Protocol | The channel protocol: tcp, udp, http, or grpc. |

Buttons at the bottom of the panel:

| Button | What it does |
|--------|--------------|
| Apply  | Saves the values you typed into the selected element. |
| Delete | Removes the selected element. |
| + Add  | Adds a new attribute (tag) row. |
| + port | Adds a new port row (workloads). |
| + ... entry / + ... | Adds a row to a map or list field (asks for the key or value). |

Note: clicking any of the "+" buttons or "[remove]" keeps everything else you already typed;
you do not lose unsaved edits.

### Bottom action bar

| Button | What it does |
|--------|--------------|
| Download YAML | Turns your drawing into an architecture file and downloads it. It also fills the YAML editor in the Files stage. |
| Download SecDSL | Generates a starter security file from your drawing's attributes (the rules are left for you to write). It also fills the SecDSL editor. |
| Files | Jumps to the Files stage. |

If a required field is missing (for example a `Domain` attribute), the download is blocked,
the message turns red, and the elements with the problem are outlined in red.

---

## Stage 2: Files

Two text editors side by side: the architecture file on the left, the security rules on the
right. Use this stage to write or paste text directly, or to refine what the Design stage
generated.

Each editor has:

| Button | What it does |
|--------|--------------|
| Upload   | Loads a file from your computer into the editor. |
| Download | Saves the editor's content to a file. |

At the bottom:

| Button | What it does |
|--------|--------------|
| Merge  | Runs the full pipeline (checks, merge, validation) on the two texts and opens the Result stage with the graph. |

A short status message next to Merge tells you if it worked, or shows the errors found.

---

## Stage 3: Result

The merged model shown as an interactive graph, with a sidebar of controls on the left and the
graph on the right.

### Sidebar

| Section | What it shows |
|---------|---------------|
| View | Three buttons: Both, Architecture, Security. They switch which edges are shown (the topology, the security rules, or both). |
| Selected | Details of whatever node or edge you last clicked. |
| Filter rules | One toggle per security property (Confidentiality, Integrity, Isolation, Authentication). Turn a property off to hide its edges. |
| Warnings | Advisory notes, for example a rule that covers no components. |
| Coverage | For each named context, the components it matched. |
| Legend | The colour and line style for each rule type and for architecture links. |

### The graph

- Components are nodes; hosts and groups (VMs, zones, co-location groups) are boxes around their children.
- Grey lines are the architecture topology (which component connects to which connector), with
  arrows showing the flow direction and the port name as the label.
- Coloured lines are the security rules (blue Confidentiality, green Integrity, red dashed
  Isolation, purple Authentication).
- Click a node to see its type, attributes, and ports.
- Click a security edge to see the full rule (all of its source, target, and mechanism components).

### Controls (top right of the graph)

| Button | What it does |
|--------|--------------|
| Fit | Zooms so the whole graph fits the screen. |
| Re-layout | Re-arranges the nodes. |

---

## Keyboard shortcuts (Design stage)

| Keys | Action |
|------|--------|
| Ctrl+A | Select all elements. |
| Ctrl+D | Duplicate the selected element(s). |
| Delete or Backspace | Delete the selected element(s). |

These only act on the canvas, and never while you are typing in a field.

---

## Concepts in one place

| Term | Plain meaning |
|------|---------------|
| Component | Any element you place: a workload, a host, or a group. |
| Workload  | Something that runs: App (stateless) or Data (stateful). |
| Host      | Something workloads run on: VM, PM, Worker. |
| Group     | Something that contains elements: Zone, Colocation, HostPool. |
| Connector | A communication channel between components. |
| Port      | A named connection point on a component, with a number and protocol. |
| Link      | A wire from a component's port to a connector, with a direction. |
| Attribute | A free form tag (Domain, Role, DataClass) used to classify a component. Security rules select components by these tags. |
| Context   | A named group of components defined in the security file by a tag rule, for example `(Domain=backend)`. |
| Coverage  | Which real components a context matched. |
| Rule      | A security requirement (Confidentiality, Integrity, Isolation, Authentication) between contexts. |

## Typical workflow

1. Design stage: place components, set their properties, draw the connectors and links.
2. Download YAML and Download SecDSL (or go to Files to write them).
3. Files stage: write or refine the security rules, then press Merge.
4. Result stage: read the graph, use the View toggle and filters, check the warnings and coverage.
