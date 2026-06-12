# s4cLight

## Quick start

Build:
```bash
mvn package -q
```

Run on the included example:
```bash
java -jar target/s4clight.jar examples/clinic.arch.yaml examples/clinic.secdsl
```

See the full object graph (what each rule actually points to):
```bash
java -jar target/s4clight.jar examples/clinic.arch.yaml examples/clinic.secdsl --inspect
```

See the metamodel definition:
```bash
java -jar target/s4clight.jar --metamodel
```

Validate without writing output (for CI):
```bash
java -jar target/s4clight.jar examples/clinic.arch.yaml examples/clinic.secdsl --validate
echo $?  # 0 = clean, 1 = problems
```

---

## Two input files

### The architecture file (.arch.yaml)

```yaml
name: my-app

components:
  - name: BackendVM
    type: VM
    attributes:
      Domain: backend
    children:
      - name: API
        type: App
        attributes:
          Domain: backend
        ports: [in, out]

  - name: DatabaseVM
    type: VM
    attributes:
      Domain: database
    children:
      - name: DB
        type: Data
        attributes:
          Domain: database
        ports: [db_in]

connectors:
  - name: API_to_DB

links:
  - port: API.out
    connector: API_to_DB
  - port: DB.db_in
    connector: API_to_DB
```

Component types: `VM`, `App`, `Data`. You can add more by registering a handler (see docs).

The `attributes` map on each component is what the security rules evaluate against. A context `(Domain=backend)` will match any component with `Domain: backend` in its attributes.

### The security rules file (.secdsl)

```
#attribute Domain = (backend, database, frontend);

#context backendCtx = (Domain=backend);
#context dbCtx      = (Domain=database);

#property Confidentiality(dbCtx, backendCtx);
#property Integrity(backendCtx, dbCtx);
#property Isolation(dbCtx, (Domain=frontend));
```

Four built-in property types:

| Property | Syntax | Meaning |
|---|---|---|
| Confidentiality | `Confidentiality(sctx, tctx?)` | Communication must be encrypted |
| Integrity | `Integrity(sctx, tctx?)` | Data must not be alterable in transit |
| Isolation | `Isolation(sctx, tctx?)` | No network path allowed between the two sides |
| Authentication | `Authentication(sctx, actx) -> tctx` | sctx must authenticate via actx before reaching tctx |

---

## Output

A `.sam4c.json` file with four sections:

- `architecture` -- the full component tree as loaded
- `security` -- attributes, contexts, rules as parsed
- `coverage` -- for each named context, which component names it matched
- `resolvedRules` -- each rule with the component names it governs

The in-memory model (before JSON serialization) has real Java object references -- `rule.sctxComponents()` returns `List<Component>`, not `List<String>`. A generator reading the model navigates it like a proper object graph.

---

## MDE design

The tool is built as a proper MDE tool with three levels:

```
M3   MPackage / MClass / MAttribute / MReference   (the metamodel framework)
M2   CoreMetamodel + ArchMetamodel + SecurityMetamodel   (the SAM4C type system)
M1   Architecture + SecurityModel instances   (your actual models)
M0   the running system
```

The M2 metamodel is split into three sub-packages with their own namespace URIs, mirroring the structure of the original SAM4C Eclipse plugin:

- `http://avalon.inria.fr/sam4c/core/` -- shared base types
- `http://avalon.inria.fr/sam4c/architecture/` -- architecture concepts
- `http://avalon.inria.fr/sam4c/security/` -- security concepts

Every model loaded from files is validated against the M2 metamodel before the merge runs. Conformance errors cite the M2 constraint they are enforcing.

---

## Extending

**New security property** (e.g. NonRepudiation):
1. Add MClass to `SecurityMetamodel.java`
2. Add Java record to `model/rule/`
3. Add to `permits` clause in `SecurityRule.java`
4. Register a `RuleFactory` in `PropertyRegistry`
5. Add a case in `ModelMerger.resolveRule()` and `ConformanceChecker.checkRule()`

**New component type** (e.g. Container):
1. Add MClass to `ArchMetamodel.java`
2. Register a `ComponentTypeHandler` in `ComponentRegistry`
3. Use `type: Container` in your YAML

---

## Background

This is a rewrite of SAM4C, an Eclipse MDE research plugin from INRIA Avalon. The original tool modelled cloud security using EMF/Ecore and a GMF graphical editor. It was functional but required Eclipse to run, making it unusable in any automated or CLI context.

s4cLight keeps the same modelling concepts and metamodel structure, drops the Eclipse dependency entirely, and adds explicit coverage evaluation (mapping contexts to the actual components they cover via attribute predicates). The `.secdsl` syntax is identical to the original -- existing rule files work without modification.

The research goal is automated secure cloud deployment: model once, generate everywhere.

---

## Docs

- `docs/user-guide.md` -- full CLI and file format reference
- `docs/codebase.md` -- detailed explanation of every file (gitignored)
- `docs/sam4c-vs-s4cLight.md` -- technical comparison with the original Eclipse tool
- `docs/mde-comparison.md` -- academic and industrial MDE comparison
