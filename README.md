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



### The security rules file (.secdsl)

```
#attribute Domain = (backend, database, frontend);

#context backendCtx = (Domain=backend);
#context dbCtx      = (Domain=database);

#property Confidentiality(dbCtx, backendCtx);
#property Integrity(backendCtx, dbCtx);
#property Isolation(dbCtx, (Domain=frontend));
```

property types:

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

The in-memory model (before JSON serialization) has real Java object references -- `rule.sctxComponents()`A generator reading the model navigates it like a proper object graph.

---

