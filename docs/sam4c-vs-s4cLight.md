# SAM4C vs s4cLight

This document explains every difference between the original SAM4C Eclipse plugin and the s4cLight CLI tool. The goal is a precise technical comparison so you can understand what was kept, what was dropped, and what was redesigned.

---

## Purpose

Both tools do the same core thing: take an architecture description and a set of security rules, and produce a unified model that combines them with resolved cross-references.

SAM4C was built as a research prototype inside Eclipse. s4cLight is the same pipeline rebuilt as a standalone command-line tool with no IDE dependency.

---

## Architecture description input

### SAM4C

The architecture is drawn graphically using a GMF diagram editor inside Eclipse. The editor writes two files:

- `clinic.arch_diag` -- the GMF notation file (positions, shapes, canvas layout)
- `clinic.arch` -- the XMI model file (the actual data)

The user never writes the `.arch` file by hand. It is always produced by the diagram editor.

### s4cLight

There is no graphical editor. The architecture is described in a plain YAML file. Each component carries an `attributes` map that is used by the merger to evaluate context predicates:

```yaml
- name: PatientDB
  type: Data
  attributes:
    Domain: database
    DataClass: clinical
  ports: [db_in]
```

YAML was chosen because it is human-readable, writable without any tool, trackable in git, and trivial to extend.

---

## Security rules input

### SAM4C

The `.secdsl` file is edited inside Eclipse using a full Xtext editor with syntax highlighting, real-time error markers, and auto-completion including cross-references to architecture element names.

### s4cLight

The `.secdsl` syntax is identical. The same files work in both tools. s4cLight uses a hand-written recursive descent parser instead of the Xtext/ANTLR generated one. The parser gives clear error messages with line numbers.

The `.secdsl` format is fully compatible between SAM4C and s4cLight.

---

## Metamodel definition

### SAM4C

The metamodel is defined in `sam4c.ecore`, an EMF file. EMF generates Java classes from it. Every model class is auto-generated. The ecore is a formal artifact that sits at the M2 level of the MDE hierarchy:

```
M3  Ecore (meta-metamodel, defined by Eclipse)
M2  sam4c.ecore (the SAM4C metamodel -- Architecture, Component, SecurityRules, Rule...)
M1  clinic.arch + clinic.secdsl (model instances conforming to the metamodel)
M0  the actual running system
```

Model instances are automatically checked for conformance against the M2 metamodel (type safety, multiplicity, containment). This is enforced by the EMF runtime.

### s4cLight

There is no formal ecore file. The metamodel is expressed directly as Java 21 records and sealed interfaces:

```java
public sealed interface SecurityRule
    permits Confidentiality, Integrity, Isolation, Authentication {}

public record Confidentiality(Ref sctx, Ref tctx) implements SecurityRule {}
public record Component(String name, String type, List<Port> ports,
                         List<Component> children, boolean external,
                         Map<String, String> attributes, Map<String, Object> properties) {}
```

The MDE levels collapse:

```
M2+M1  Java records define both the metamodel shape and instantiate model objects at runtime
M0     the actual running system
```

There is no separate formal metamodel artifact and no automatic conformance checking by a framework. Type safety is enforced by the Java compiler and the registry pattern (unknown types fail at load time), not by a metamodel validator.

---

## Cross-reference resolution and the object graph

### SAM4C

After `EcoreUtil.resolveAll(resourceSet)`, security rule references are live Java pointers to architecture objects. This works because of the `ContextualElement` bridge in the metamodel: every architecture element extends `ContextualElement` which extends `security.AbstractContext`, making it a valid reference target at the type level.

### s4cLight

`ModelMerger.merge()` evaluates context predicates against component attributes and builds an in-memory object graph where every `ResolvedRule` holds actual `List<Component>` references -- not names, not IDs -- pointing to the same `Component` instances that are in the `Architecture`.

```java
// From a resolved rule, you navigate directly:
for (Component c : rule.sctxComponents()) {
    c.name()        // "PatientDB"
    c.type()        // "Data"
    c.attributes()  // {Domain=database, DataClass=clinical}
    c.ports()       // [Port(db_in)]
    c.children()    // []
}
```

The object graph is equivalent in navigability to the EMF model. The mechanism is different (predicate evaluation vs. Xtext cross-reference resolution) but the result -- a rule that directly holds its target component objects -- is the same.

---

## Context-to-component assignment

### SAM4C

In SAM4C a context can reference architecture elements directly by name:
```
#context frontendCtx = FrontendVM;
```
After resolution, `frontendCtx` holds an EMF pointer to `FrontendVM`. Alternatively, a context can use attribute predicates `(Domain=frontend)` but architecture elements have no attribute map by default -- the predicate system was designed for user-defined attributes, not structural matching.

### s4cLight

Every component carries an `attributes` map in the YAML. The merger evaluates predicates against these attributes to find matching components. A context `(Domain=frontend)` matches every component with `attributes.Domain = frontend`. Direct name references also work: `NamedRef("FrontendVM")` resolves to the `FrontendVM` component directly.

This is more explicit and more powerful than the original: every component is always tagged, and the coverage map is always fully computable.

---

## Serialisation format

### SAM4C

The unified model is written as a `.sam4c` XMI file. Cross-references in the XMI are object ID references -- not name strings.

### s4cLight

The unified model is written as a `.sam4c.json` file. Component references inside `resolvedRules` are written as name strings (cross-reference handles), exactly as XMI uses object IDs. The full component data lives once in the `architecture` section. The in-memory model has real Java references; the JSON is only a serialisation of that graph.

---

## Extensibility

### SAM4C

New component type: add EClass to ecore, regenerate Java code, update GMF diagram.
New security property: add EClass to ecore, regenerate, add grammar rule to SecDsl.xtext, run MWE2 workflow. Three tools, two generation steps.

### s4cLight

New security property:
1. Add a Java record implementing `SecurityRule`
2. Add it to the `permits` clause of the sealed interface
3. Register a `RuleFactory` in `Main.java`
4. Add a case to `ModelMerger.resolveRule()`

New component type:
1. Register a `ComponentTypeHandler` in `Main.java`
2. Use the new `type:` value in YAML

One tool, zero generation steps.

---

## Build and distribution

### SAM4C

Eclipse OSGi bundles. Requires launching a second Eclipse runtime instance.

### s4cLight

```bash
mvn package
java -jar target/sam4c-cli.jar arch.yaml rules.secdsl
java -jar target/sam4c-cli.jar arch.yaml rules.secdsl --inspect
java -jar target/sam4c-cli.jar arch.yaml rules.secdsl --validate
```

Single fat JAR. No IDE, no plugin installation, runs in any terminal or CI pipeline.

---

## Dependencies

### SAM4C

Eclipse Platform, EMF, GMF, Xtext, ANTLR, Apache Batik, Guava, Log4j.

### s4cLight

Jackson (JSON/YAML), picocli. Nothing else.

---

## What is missing in s4cLight compared to SAM4C

| Feature | SAM4C | s4cLight |
|---|---|---|
| Formal ecore metamodel at M2 level | Yes | No |
| Automatic model conformance checking | Yes (EMF runtime) | No (compile-time only) |
| Graphical diagram editor | Yes (GMF) | No (YAML instead) |
| IDE auto-complete in .secdsl | Yes (Xtext) | No |
| XMI standard serialisation | Yes | No (JSON instead) |
| Interoperability with ATL, Acceleo, QVT | Yes | No |
| infra.ecore infrastructure metamodel | Defined | Not ported |
| OCL constraint language | Possible | Not implemented |

---

## What s4cLight adds that SAM4C does not have

| Feature | SAM4C | s4cLight |
|---|---|---|
| Runs without Eclipse | No | Yes |
| CLI interface | No | Yes |
| Runs in CI/CD pipelines | No | Yes |
| YAML architecture input with attribute tagging | No | Yes |
| Explicit coverage map (context -> component objects) | No | Yes |
| Full object graph traversal via --inspect | No | Yes |
| Single fat JAR distribution | No | Yes |
| Extensibility without code generation | No | Yes |

---

## What neither tool has yet

The generator. Both SAM4C and s4cLight stop at producing the unified model. The step that traverses the model and emits IaC (Terraform, Kubernetes, Docker Compose) is not built in either. That is the next phase of the research.
