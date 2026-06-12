# s4cLight User Guide

s4cLight takes two input files -- an architecture description and a set of security rules -- and merges them into a single unified model. That unified model is the starting point for any downstream work such as validation, IaC generation, or compliance checking.

---

## Table of contents

1. [Build and install](#1-build-and-install)
2. [CLI reference](#2-cli-reference)
3. [Writing the architecture file](#3-writing-the-architecture-file)
4. [Writing the security rules file](#4-writing-the-security-rules-file)
5. [Understanding the output](#5-understanding-the-output)
6. [Extending the tool](#6-extending-the-tool)
7. [Error messages](#7-error-messages)

---

## 1. Build and install

Requirements: Java 21, Maven 3.8+.

```bash
cd s4cLight
mvn package -q
```

This produces `target/s4clight.jar`. That single file contains all dependencies. You can copy it anywhere.

To make the command available globally:

```bash
cp target/s4clight.jar ~/bin/s4clight.jar
alias s4c="java -jar ~/bin/s4clight.jar"
```

---

## 2. CLI reference

```
s4clight [--metamodel] <arch-file> <rules-file> [options]
```

```
s4clight <arch-file> <rules-file> [options]
```

### Arguments

| Argument | Description |
|---|---|
| `<arch-file>` | Path to the architecture YAML file (`.arch.yaml`) |
| `<rules-file>` | Path to the security rules file (`.secdsl`) |

### Options

| Option | Description |
|---|---|
| `-o <file>` / `--output <file>` | Where to write the output JSON. Defaults to `<arch-name>.sam4c.json` in the same directory as the arch file |
| `--metamodel` | Print the full M2 metamodel definition (all three sub-packages: core, architecture, security) and exit. No input files required |
| `--inspect` | Print a full object-graph traversal: every rule with its fully resolved component objects, their types, attributes, ports, and children. Use this to verify the merge is correct before running a generator |
| `--validate` | Run the merge and print the resolution report, but do not write any output file. Exit code is 0 if all references resolved, 1 if any are unresolved |
| `--help` | Print usage |
| `--version` | Print version |

### Examples

Merge and write output:
```bash
java -jar s4clight.jar clinic.arch.yaml clinic.secdsl
```

Merge with explicit output path:
```bash
java -jar s4clight.jar clinic.arch.yaml clinic.secdsl -o output/clinic.sam4c.json
```

Validate without writing output (useful in CI):
```bash
java -jar s4clight.jar clinic.arch.yaml clinic.secdsl --validate
echo $?   # 0 = all resolved, 1 = unresolved references
```

Inspect the full object graph traversal:
```bash
java -jar s4clight.jar clinic.arch.yaml clinic.secdsl --inspect
```

Print the M2 metamodel (no input files needed):
```bash
java -jar s4clight.jar --metamodel
```

### Terminal output

Every normal run first checks conformance against the M2 metamodel, then prints a summary:

```
Loading architecture: clinic.arch.yaml
Parsing security rules: clinic.secdsl
Checking conformance against M2 metamodel...
  Conformance OK
Merging and resolving cross-references...

-- Resolution report --
  dbCtx                          -> context
  backendCtx                     -> context
  adminRoleCtx                   -> context
  adminCtx                       -> context
  frontendCtx                    -> context

-- Architecture --
  Name       : clinic-portal
  Components : 4
  Connectors : 4
  Links      : 7

-- Security --
  Attributes : 3
  Contexts   : 7
  Rules      : 7

Wrote unified model to: clinic.sam4c.json
```

If a name in a security rule cannot be resolved, it appears under "Unresolved" with an `x` marker and the tool exits with code 1.

---

## 3. Writing the architecture file

The architecture file is a YAML file. The convention is to name it `<name>.arch.yaml`. It describes the components of your system, the connectors between them, and the links that wire ports to connectors.

### Minimal example

```yaml
name: my-app

components:
  - name: ServerVM
    type: VM
    children:
      - name: WebServer
        type: App
        ports: [http_in, http_out]

connectors:
  - name: Internet
    external: true

links:
  - port: WebServer.http_in
    connector: Internet
```

### Top-level fields

| Field | Required | Description |
|---|---|---|
| `name` | Yes | Name of the architecture. Used as the base name for the output file |
| `components` | Yes | List of top-level components |
| `connectors` | Yes | List of connectors (network endpoints between components) |
| `links` | Yes | List of associations between ports and connectors |

---

### Components

Each component has the following fields:

| Field | Required | Description |
|---|---|---|
| `name` | Yes | Unique name for this component |
| `type` | Yes | Component type: `VM`, `App`, or `Data` (or any custom type you register) |
| `ports` | No | List of port names on this component |
| `children` | No | Nested components (only meaningful for `VM`) |
| `external` | No | Boolean. Marks a component as hosted outside your infrastructure (default: false) |
| `attributes` | No | Key-value map used by the merger to evaluate security context predicates. A component with `Domain: frontend` will be matched by any context that contains `(Domain=frontend)` |

#### Type: VM

A virtual machine or grouping element. Contains other components as children. VMs do not have ports directly -- their child components do.

```yaml
- name: BackendVM
  type: VM
  children:
    - name: SpringAPI
      type: App
      ports: [api_in, db_out, admin_in]
    - name: Cache
      type: App
      ports: [cache_in]
```

#### Type: App

A running application, service, or microservice. Has ports.

```yaml
- name: Nginx
  type: App
  attributes:
    Domain: frontend
  ports: [http_in, api_out]
```

#### Type: Data

A data store (database, object storage, message queue). Has ports.

```yaml
- name: PostgreSQL
  type: Data
  attributes:
    Domain: database
    DataClass: clinical
  ports: [db_in]
```

#### Nesting

Components can be nested to any depth. A VM contains Apps and Data stores. You can also nest VMs inside VMs to model clusters or groups.

```yaml
- name: Cluster
  type: VM
  children:
    - name: Node1
      type: VM
      children:
        - name: ServiceA
          type: App
          ports: [in, out]
    - name: Node2
      type: VM
      children:
        - name: ServiceB
          type: App
          ports: [in, out]
```

---

### Connectors

A connector is a named communication channel. Components are never directly connected to each other -- they are always connected through a connector via their ports.

```yaml
connectors:
  - name: FE_to_BE
  - name: BE_to_DB
  - name: Internet
    external: true
```

| Field | Required | Description |
|---|---|---|
| `name` | Yes | Unique name for the connector |
| `external` | No | Boolean. Marks this connector as originating from outside the system (default: false) |

---

### Links

A link connects a port (written as `ComponentName.portName`) to a connector. Both ends of a communication channel need a link: one for the sending port and one for the receiving port.

```yaml
links:
  - port: Nginx.api_out
    connector: FE_to_BE
  - port: SpringAPI.api_in
    connector: FE_to_BE
```

| Field | Required | Description |
|---|---|---|
| `port` | Yes | Reference to a port, written as `ComponentName.portName` |
| `connector` | Yes | Name of the connector this port is wired to |

#### Port reference format

The component name in a port reference is the direct parent component name, not the full qualified path:

```
Nginx.api_out        # correct: Nginx is the direct owner of api_out
FrontendVM.api_out   # wrong: api_out belongs to Nginx, not FrontendVM
```

---

### Full example

```yaml
name: clinic-portal

components:
  - name: FrontendVM
    type: VM
    children:
      - name: Nginx
        type: App
        ports: [http_in, api_out]

  - name: BackendVM
    type: VM
    children:
      - name: SpringAPI
        type: App
        ports: [api_in, db_out, admin_in]

  - name: DatabaseVM
    type: VM
    children:
      - name: PatientDB
        type: Data
        ports: [db_in]

  - name: AdminVM
    type: VM
    children:
      - name: AdminDashboard
        type: App
        ports: [mgmt_out]

connectors:
  - name: FE_to_BE
  - name: BE_to_DB
  - name: Admin_to_BE
  - name: Internet
    external: true

links:
  - port: Nginx.http_in
    connector: Internet
  - port: Nginx.api_out
    connector: FE_to_BE
  - port: SpringAPI.api_in
    connector: FE_to_BE
  - port: SpringAPI.db_out
    connector: BE_to_DB
  - port: PatientDB.db_in
    connector: BE_to_DB
  - port: AdminDashboard.mgmt_out
    connector: Admin_to_BE
  - port: SpringAPI.admin_in
    connector: Admin_to_BE
```

---

## 4. Writing the security rules file

The security rules file uses the `.secdsl` extension. It is the same format as the original SAM4C SecDSL. A file contains three kinds of declarations, in any order: attribute declarations, context declarations, and property declarations.

### 4.1 Attribute declarations

An attribute is a named set of possible values. Attributes are used to classify components.

```
#attribute Name = (value1, value2, value3);
```

Examples:

```
#attribute Domain = (frontend, backend, database, admin);
#attribute Role = (patient, clinician, administrator);
#attribute DataClass = (pii, clinical, audit);
#attribute Tier = (web, app, data);
```

Attributes can also be declared without values if you only need the name as a type:

```
#attribute Sensitivity;
```

---

### 4.2 Context declarations

A context is a named predicate that selects a set of elements. Contexts are the subjects and targets of security rules.

```
#context name = condition1 : condition2 : ... ;
```

A condition is an attribute-value pair written as `(AttributeName=value)`.

A context with one condition:

```
#context frontendCtx = (Domain=frontend);
```

A context with multiple conditions (all must match -- it is a conjunction):

```
#context adminCtx = (Domain=backend) : (Role=administrator);
```

Contexts can also reference other named contexts directly:

```
#context sensitiveBackend = backendCtx : (DataClass=pii);
```

---

### 4.3 Property declarations

A security property expresses a security requirement between contexts. The general syntax is:

```
#property PropertyName(arg1, arg2, ...) -> optional_target ;
```

There are four built-in properties.

---

#### Confidentiality

The communication between `sctx` and `tctx` must be encrypted. No third party can read the data in transit.

```
#property Confidentiality(sctx, tctx);
```

`tctx` is optional. When omitted it means the property applies to all communications involving `sctx`.

Examples:

```
#property Confidentiality(dbCtx, backendCtx);
#property Confidentiality(clinicalDataCtx, backendCtx);
#property Confidentiality(piiCtx);
```

Real-world mapping: TLS on the link between the two components, encrypted at-rest storage for data contexts.

---

#### Integrity

Data flowing from `sctx` to `tctx` must not be modifiable in transit.

```
#property Integrity(sctx, tctx);
```

`tctx` is optional.

Examples:

```
#property Integrity(backendCtx, dbCtx);
#property Integrity(clinicalDataCtx);
```

Real-world mapping: TLS with integrity checking, message signing, write-protected channels.

---

#### Isolation

`sctx` and `tctx` must have no shared execution or network path. They cannot communicate with each other under any circumstances.

```
#property Isolation(sctx, tctx);
```

`tctx` is optional.

Examples:

```
#property Isolation(dbCtx, frontendCtx);
#property Isolation(adminCtx, frontendCtx);
```

Real-world mapping: separate VPCs or subnets, security group rules with explicit deny, Kubernetes NetworkPolicy with no ingress from the other namespace.

---

#### Authentication

An entity described by `sctx` must authenticate via mechanism `actx` before it is allowed to reach `tctx`.

```
#property Authentication(sctx, actx) -> tctx;
```

All three arguments are required. The arrow `->` separates the authenticating party and mechanism from the target.

Read it as: "who=sctx, authenticated by=actx, can reach=tctx".

Examples:

```
#property Authentication(adminRoleCtx, adminCtx) -> backendCtx;
#property Authentication((Role=administrator), (Domain=admin)) -> dbCtx;
```

Real-world mapping: IAM role check, mTLS client certificate validation, API key gate, OAuth token verification on the target endpoint.

---

### 4.4 Inline references

Instead of declaring a named context and then referencing it, you can write the condition inline inside a rule.

Single inline condition:

```
#property Confidentiality((Domain=database), (Domain=backend));
```

Multiple inline conditions joined with `:` (conjunction):

```
#property Authentication((Role=administrator) : (Domain=admin), (Domain=admin)) -> (Domain=backend);
```

A group of condition sets in braces (disjunction -- any of the groups):

```
#property Isolation(dbCtx, {(Domain=frontend) : (Role=patient), (Domain=frontend) : (Role=guest)});
```

---

### 4.5 Complete example

```
#attribute Domain = (frontend, backend, database, admin);
#attribute Role = (patient, clinician, administrator);
#attribute DataClass = (pii, clinical, audit);

#context frontendCtx = (Domain=frontend);
#context backendCtx = (Domain=backend);
#context dbCtx = (Domain=database);
#context adminCtx = (Domain=admin);
#context adminRoleCtx = (Role=administrator);
#context clinicalDataCtx = (DataClass=clinical);
#context piiCtx = (DataClass=pii);

#property Confidentiality(dbCtx, backendCtx);
#property Integrity(backendCtx, dbCtx);
#property Authentication(adminRoleCtx, adminCtx) -> backendCtx;
#property Isolation(dbCtx, frontendCtx);
#property Isolation(adminCtx, frontendCtx);
#property Confidentiality(clinicalDataCtx, backendCtx);
#property Confidentiality(piiCtx, backendCtx);
```

---

### 4.6 Comments

Lines starting with `//` are ignored:

```
// This is a comment
#attribute Domain = (frontend, backend);  // inline comment
```

---

## 5. Understanding the output

The output is a JSON file named `<arch-name>.sam4c.json`. It contains four sections.

### architecture

The full architecture as loaded from the YAML file. Nested structure is preserved. Each port appears as an object `{ "name": "http_in" }`.

### security

The security model as parsed from the `.secdsl` file.

Each rule is serialised with a `@type` discriminator matching the property name:

```json
{
  "@type": "Confidentiality",
  "sctx": { "@type": "NamedRef", "name": "dbCtx" },
  "tctx": { "@type": "NamedRef", "name": "backendCtx" }
}
```

Reference types:

| JSON `@type` | Meaning |
|---|---|
| `NamedRef` | A name that was resolved against contexts or architecture elements |
| `ValuedAttrRef` | An inline condition `(attribute=value)` |
| `ComposedRef` | A conjunction of conditions |

### resolution

A map of every name that appeared in the security rules and what it resolved to:

```json
"resolution": {
  "dbCtx":      "context",
  "backendCtx": "context",
  "Nginx":      "component:App",
  "FE_to_BE":   "connector"
}
```

Possible resolution values:

| Value | Meaning |
|---|---|
| `context` | Resolved to a named context declared in the `.secdsl` file |
| `attribute` | Resolved to a declared attribute type |
| `component:VM` | Resolved to a VM component in the architecture |
| `component:App` | Resolved to an App component in the architecture |
| `component:Data` | Resolved to a Data component in the architecture |
| `connector` | Resolved to a connector in the architecture |
| `port` | Resolved to a port |

### unresolved

A list of names that appeared in the security rules but could not be resolved to anything in either the security model or the architecture:

```json
"unresolved": ["typoCtx", "UnknownVM"]
```

An empty list means the model is fully resolved. A non-empty list is a warning -- the tool still writes the output, but the exit code is 1.

---

## 6. Extending the tool

### Adding a new security property

**Step 1.** Add the model record to `sam4c/light/model/rule/`:

```java
// NonRepudiation.java
package sam4c.light.model.rule;
import sam4c.light.model.ref.Ref;

public record NonRepudiation(Ref sctx) implements SecurityRule {}
```

**Step 2.** Add it to the `permits` clause of `SecurityRule.java`:

```java
public sealed interface SecurityRule
        permits Confidentiality, Integrity, Isolation, Authentication, NonRepudiation {}
```

**Step 3.** Register the factory in `Main.java` inside the `call()` method:

```java
propReg.register(new RuleFactory() {
    public String keyword() { return "NonRepudiation"; }
    public SecurityRule create(List<Ref> args, Ref ret) {
        return new NonRepudiation(args.get(0));
    }
});
```

**Step 4.** Add a case to the `collectRefs` switch in `ModelMerger.java`:

```java
case NonRepudiation r -> addRef(refs, r.sctx());
```

**Step 5.** Use it in any `.secdsl` file:

```
#property NonRepudiation(auditLogCtx);
```

---

### Adding a new component type

**Step 1.** Register a handler in `Main.java` inside the `call()` method:

```java
compReg.register(new ComponentTypeHandler() {
    public String typeName() { return "Container"; }
    public Component load(String name, Map<String, Object> yaml, ComponentRegistry reg) {
        String image = (String) yaml.getOrDefault("image", "");
        int replicas = (int) yaml.getOrDefault("replicas", 1);
        return new Component(
                name, "Container",
                ComponentRegistry.loadPorts(yaml),
                List.of(),
                ComponentRegistry.bool(yaml, "external"),
                Map.of("image", image, "replicas", replicas)
        );
    }
});
```

**Step 2.** Use it in any `.arch.yaml` file:

```yaml
- name: ApiPod
  type: Container
  image: my-registry/api:latest
  replicas: 3
  ports: [http_in, grpc_out]
```

The `image` and `replicas` fields end up in `Component.properties` and are available in the JSON output for a future generator to consume.

---

## 7. Error messages

### ParseException: Unknown keyword

```
Line 4: Unknown keyword: #propertyy
```

A typo in a `#attribute`, `#context`, or `#property` keyword. Check the spelling.

---

### ParseException: Expected identifier

```
Line 12: Expected identifier but got '('
```

The parser expected a name but found something else. Common cause: a missing context name, or a property keyword not followed by the correct syntax.

---

### Unknown property

```
Error: Unknown property 'Availabilty'. Known: [Confidentiality, Integrity, Isolation, Authentication]
```

A property keyword in the `.secdsl` file is not registered. Either it is a typo, or you need to register it as a new property (see section 6).

---

### Unknown component type

```
Error: Unknown component type 'Pod'. Known: [App, Data, VM]
```

A `type:` value in the `.arch.yaml` file is not registered. Either it is a typo, or you need to register it as a new component type (see section 6).

---

### Unresolved references (warning)

```
WARNING: 2 unresolved reference(s). Check names in your .secdsl file.

-- Resolution report --
  x typoCtx
  x UnknownVM
```

A name used in a security rule or context could not be matched to any named context or architecture element. Common causes:

- Typo in the context name
- Context declared in the `.secdsl` file but referenced with a different capitalisation
- Architecture element name that does not match what is in the YAML file
- A context referenced before it is declared (declarations can be in any order, so this should not happen -- if it does, it is a bug)

---

### Architecture file not found / Rules file not found

```
Architecture file not found: clinic.arch.yaml
```

The path passed as the first argument does not exist. Use an absolute path or make sure the working directory is correct.
