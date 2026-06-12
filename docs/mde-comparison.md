# MDE Comparison: SAM4C vs s4cLight

This document compares the two tools from three angles: academic MDE rigor, industrial value, and extensibility. It is written to be honest about what each tool is and is not.

---

## 1. Academic MDE rigor

### 1.1 Standards compliance

**SAM4C**

SAM4C is built on EMF (Eclipse Modelling Framework), which is the Java reference implementation of the OMG MOF standard (Meta Object Facility). Everything in SAM4C maps to a published international standard:

| SAM4C artifact | Standard it implements |
|---|---|
| `sam4c.ecore` | MOF M2 metamodel (OMG MOF 2.0) |
| `.arch` XMI file | OMG XMI 2.x (model interchange format) |
| `SecDsl.xtext` | Formal grammar specification (ANTLR4 / Xtext) |
| EMF object graph | MOF reflective API (EObject, EClass, EStructuralFeature) |
| `EcoreUtil.resolveAll` | MOF cross-reference resolution protocol |

The MDE levels are formally aligned to the MOF stack:

```
M3   MOF (defined by OMG)
M2   sam4c.ecore (instance of MOF)
M1   clinic.arch (instance of sam4c.ecore)
M0   running system
```

This means SAM4C models can be consumed by any MOF-compliant tool in the world. An ATL transformation, an Acceleo template, a QVT specification, Sirius graphical editor -- all work directly on SAM4C models without any adapter.

**s4cLight**

s4cLight has a structurally equivalent M2/M1 separation, but it does not implement MOF and does not claim to. The M3 framework (MPackage, MClass, MAttribute, MReference) is a custom design inspired by MOF but not conforming to it.

| s4cLight artifact | What it is |
|---|---|
| `CoreMetamodel`, `ArchMetamodel`, `SecurityMetamodel` | Custom M2 definitions, not MOF instances |
| `.sam4c.json` | Custom JSON serialization, not XMI |
| `DslParser` | Hand-written recursive descent parser, not a formal grammar artifact |
| `ModelMerger` | Custom cross-reference resolution, semantically equivalent to EcoreUtil but not protocol-compatible |

The MDE levels exist and are structurally equivalent:

```
M3   MPackage / MClass / MAttribute / MReference (custom, not MOF)
M2   CoreMetamodel + ArchMetamodel + SecurityMetamodel
M1   Architecture + SecurityModel instances
M0   running system
```

But the M3 is not MOF. An ATL tool cannot read `Sam4cMetamodel.java`. The models cannot interoperate with standard MDE tools without writing adapters.

---

### 1.2 Metamodel quality

Both tools define the same concepts. The structural design is equivalent. The key difference is formalism.

**Namespace URIs**

Both tools assign namespace URIs to their metamodel packages:

| Package | SAM4C URI | s4cLight URI |
|---|---|---|
| Core | `http://avalon.inria.fr/sam4c/core/` | same |
| Architecture | `http://avalon.inria.fr/sam4c/architecture/` | same |
| Security | `http://avalon.inria.fr/sam4c/security/` | same |

The URIs are identical by design. This means the conceptual identity of the models is preserved even though the serialization formats differ.

**The ContextualElement bridge**

Both tools preserve the most important architectural decision in the metamodel: `ContextualElement` (core) is a supertype of `Component` (architecture), which means every architecture element is a valid context target for security rules. This is not an implementation detail -- it is the semantic claim that architecture and security are co-defined and inseparable in this modelling language.

In SAM4C this is enforced by the EMF type system at the M2 level. In s4cLight it is declared in `ArchMetamodel` and enforced by `ConformanceChecker` and `ModelMerger`.

**Multiplicity constraints**

Both tools specify multiplicities on every reference. Examples:

| Constraint | SAM4C | s4cLight |
|---|---|---|
| Authentication.actx | `[1..1]` (required) | `[1..1]` declared in SecurityMetamodel, checked by ConformanceChecker |
| ComposedRef.conditions | `[2..*]` | `[2..*]` declared and checked |
| Component.ports | `[0..*]` | `[0..*]` |

In SAM4C, EMF enforces these at the API level (you cannot set a `[1..1]` reference to null without a validation error). In s4cLight, enforcement happens at load time in `ConformanceChecker`. The constraint is the same. The enforcement mechanism is different.

---

### 1.3 Interoperability

This is the clearest academic gap.

**SAM4C**

A SAM4C `.sam4c` XMI file can be consumed directly by:
- ATL (model-to-model transformation to any target metamodel)
- Acceleo (model-to-text template engine, generates any textual format)
- QVT (bidirectional model transformation)
- Sirius (define new graphical editors over existing metamodels)
- Any OCL engine (for formal constraint verification)
- Eclipse CDO (distributed model repository)

This means a researcher can build on SAM4C by writing an ATL transformation to a Terraform metamodel, an Acceleo template for Kubernetes YAML, or an OCL constraint for security rule consistency -- all without modifying SAM4C itself. The modelling ecosystem absorbs it.

**s4cLight**

The `.sam4c.json` output can be consumed by any tool that reads JSON. This is a much larger universe in practical terms (every language, every platform) but it is not the MDE ecosystem. There is no ATL adapter for JSON, no Acceleo template that reads plain Java objects.

To use s4cLight output in the standard MDE ecosystem, you would need to either:
1. Write an XMI serializer that outputs XMI conformant to `sam4c.ecore` (a genuine bridge)
2. Or use s4cLight as a standalone tool and build generators directly in Java on the in-memory model

---

### 1.4 Conformance verification

**SAM4C**

EMF validates model instances automatically at every operation. When you call `rule.setSctx(null)` on a `[1..1]` reference, EMF raises an error. When you serialize a model, EMF checks every constraint in the metamodel before writing. The validation is continuous, transparent, and zero-maintenance.

**s4cLight**

`ConformanceChecker` runs once at load time, before the merge. It checks every constraint that is declared in the sub-metamodels and cites the M2 rule it is enforcing. If a `.arch.yaml` or `.secdsl` file produces a non-conforming model, the tool rejects it with a clear error message. The validation is explicit, traceable, and one-shot.

The difference: EMF validation is always on. s4cLight validation is at load time only. If you build a model programmatically in Java (not from files), the conformance check does not run unless you call it explicitly.

---

## 2. Industrial value

### 2.1 Deployment and distribution

| Criterion | SAM4C | s4cLight |
|---|---|---|
| Distribution | Eclipse plugin JARs, requires Eclipse install | Single fat JAR |
| Runtime requirement | Eclipse IDE (JVM + OSGi container + UI) | JVM only |
| Launch time | 10-30 seconds (Eclipse startup) | Under 1 second |
| Headless execution | Possible but complex (Eclipse headless mode) | Native CLI |
| Docker image | Large (Eclipse ~300MB) | Small (JRE ~200MB + 10MB JAR) |

### 2.2 CI/CD integration

**SAM4C**

Not designed for CI/CD. Requires a display server (or Xvfb) even in headless mode. No standard exit codes. No command-line interface.

**s4cLight**

```bash
# In any CI pipeline:
java -jar sam4c-cli.jar arch.yaml rules.secdsl --validate
# exit 0 = model valid and fully resolved
# exit 1 = conformance violations or unresolved references
```

The `--validate` flag was designed specifically for this: parse, conform, resolve, report, exit with a code. No output file written. Standard Unix exit codes.

### 2.3 Version control

**SAM4C**

`.arch` files are XMI (XML). XMI diffs are readable but noisy -- every position change in the diagram produces a diff even if the model content did not change, because the `.arch_diag` file stores coordinates.

**s4cLight**

`.arch.yaml` and `.secdsl` are plain text. Git diffs are clean and meaningful. Reviewing a change to a security rule is a two-line diff. Reviewing the addition of a VM is an eight-line diff.

### 2.4 Learnability

**SAM4C**

To model a new system a user must:
1. Install the correct Eclipse package
2. Install all required plugins
3. Learn the GMF diagram editor
4. Understand the EMF tree editor for the output
5. Learn the SecDSL syntax

**s4cLight**

To model a new system a user must:
1. Write a YAML file following the documented schema
2. Write a `.secdsl` file following the documented syntax
3. Run one command

---

## 3. Extensibility

### 3.1 Adding a new security property

**SAM4C**

1. Open `sam4c.ecore` in the Eclipse Ecore editor
2. Add a new `EClass` in the security subpackage
3. Run "Generate Model Code" from `sam4c.genmodel` -- Eclipse regenerates Java source
4. Add a grammar rule to `SecDsl.xtext`
5. Run the MWE2 workflow (`GenerateSecDsl.mwe2`) -- regenerates the Xtext parser, lexer, content assist
6. Update the GMF diagram if the new property needs visual representation
7. Update any ATL/Acceleo templates that process security rules

Tools required: Eclipse with EMF, Xtext, and GMF plugins. Steps: 5 to 7.

**s4cLight**

1. Add one `MClass` to `SecurityMetamodel.java` -- the metamodel is updated
2. Add one Java record to `model/rule/`
3. Add it to the `permits` clause of the `SecurityRule` sealed interface
4. Register a `RuleFactory` in `PropertyRegistry` (or in `Main.java`)
5. Add a case to `ModelMerger.resolveRule()` and `ConformanceChecker.checkRule()`

Tools required: a text editor. Steps: 5. All in one language (Java).

### 3.2 Adding a new architecture component type

**SAM4C**

1. Add an `EClass` to the architecture subpackage in `sam4c.ecore`
2. Regenerate model code from `.genmodel`
3. Add a new GMF palette entry and a creation command
4. Update the diagram updater and visual ID registry

**s4cLight**

1. Register a `ComponentTypeHandler` in `ComponentRegistry`
2. Use the new `type:` value in YAML

### 3.3 Adding a new infrastructure concept (e.g. Region, Subnet, SecurityGroup)

**SAM4C**

`infra.ecore` is a separate metamodel. Adding concepts requires editing the ecore, regenerating code, and writing an M2M transformation (ATL or Java) from the architecture metamodel to the infrastructure metamodel.

**s4cLight**

Add a new sub-metamodel:

```java
public final class InfraMetamodel {
    public static final MPackage INSTANCE = new MPackage(
        "infra", "http://avalon.inria.fr/sam4c/infra/", List.of(
            MClass.builder("Region").build(),
            MClass.builder("Subnet").superType("Region").build(),
            // ...
        )
    );
}
```

Then compose it into `Sam4cMetamodel`. No tools required beyond a text editor.

---

## 4. Research contribution assessment

### 4.1 What SAM4C contributes that s4cLight does not

- A MOF-compliant metamodel that is formally citeable as a standards-based MDE contribution
- Full interoperability with the Eclipse MDE ecosystem (ATL, Acceleo, Sirius, OCL)
- A formal abstract syntax defined at M2 independent of any implementation language
- The graphical concrete syntax (GMF) which demonstrates full MDE methodology

For a paper whose research claim is specifically about MDE methodology, tool composition, or metamodel design, these properties matter significantly.

### 4.2 What s4cLight contributes that SAM4C does not

- A usable tool -- SAM4C is broken and unusable in its current state without significant repair work
- Explicit coverage evaluation: the predicate-to-component evaluation step (context -> component mapping) is more powerful and more explicit than the original Xtext cross-reference resolution
- Three separately defined sub-metamodels with their own namespace URIs, improving modularity over the original flat approach
- A working conformance checker with traceable M2 citations
- A CLI-ready pipeline suitable for automated deployment workflows, which is the research goal
- An in-memory object graph with real component references navigable without any framework dependency

### 4.3 The honest academic position

s4cLight implements the same modelling concepts as SAM4C. The metamodel structure, the security property semantics, the object graph, the cross-reference resolution -- all are equivalent or better. What s4cLight does not have is MOF compliance and standard tool interoperability.

For the PhD research, which is about automated secure cloud deployment (not about MDE tooling per se), the absence of MOF compliance is not a research gap -- it is a deliberate engineering tradeoff that makes the tool actually usable.

The contribution is still MDE-based because:
- There is a formal metamodel (three sub-packages, namespace URIs, multiplicity constraints)
- There is M2/M1 level separation with conformance checking
- The model is a proper object graph navigable by generators
- The pipeline from model to deployment artefact is traceable to metamodel decisions

The academic framing: s4cLight demonstrates that MDE methodology can be applied to cloud security without depending on heavyweight MDE infrastructure, which is itself a research finding.

---

## 5. Summary table

| Criterion | SAM4C | s4cLight |
|---|---|---|
| MOF compliance | Yes (EMF) | No (custom M3) |
| M2 metamodel exists | Yes (ecore) | Yes (Java classes) |
| M2/M1 separation | Yes | Yes |
| Three sub-metamodels | Yes (core, arch, security) | Yes (same structure) |
| Namespace URIs | Yes | Yes (same URIs) |
| Conformance checking | Automatic (EMF) | Explicit at load time |
| XMI serialization | Yes | No (JSON) |
| ATL/Acceleo/QVT interop | Yes | No |
| Object graph with real references | Yes (EMF pointers) | Yes (Java references) |
| DSL formal grammar | Yes (Xtext/ANTLR) | Hand-written parser |
| IDE auto-complete | Yes | No |
| Graphical editor | Yes (GMF) | No (YAML) |
| CLI usable | No | Yes |
| CI/CD ready | No | Yes |
| Single file distribution | No | Yes (fat JAR) |
| Adding a new property | 5-7 steps, 3 tools | 5 steps, 1 language |
| Git-friendly input files | Partial (.secdsl yes, .arch noisy) | Yes (both plain text) |
| Currently working | No (5 known bugs) | Yes |
| Coverage evaluation | Implicit (Xtext resolution) | Explicit (predicate evaluation) |
| Generator built | No | No |
