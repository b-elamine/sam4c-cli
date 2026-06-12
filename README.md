# s4cLight

A command-line tool for modelling cloud application architectures and their security requirements, then merging them into a single unified model you can feed into a generator.


**1. Your architecture** - what VMs, services, and data stores you have, how they connect, and what security attributes each component carries.

**2. Your security rules** - what confidentiality, integrity, isolation, and authentication requirements apply between parts of the system.

The tool merges them. After the merge, every security rule directly points to the actual architecture components it governs.

---

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

The security rules file (.secdsl)

```
#attribute Domain = (backend, database, frontend);

#context backendCtx = (Domain=backend);
#context dbCtx      = (Domain=database);

#property Confidentiality(dbCtx, backendCtx);
#property Integrity(backendCtx, dbCtx);
#property Isolation(dbCtx, (Domain=frontend));
```

**Output**

A `.sam4c.json` file with four sections:

- `architecture` -- the full component tree as loaded from YAML
- `security` -- attributes, contexts, and rules as parsed from the DSL (with type names)
- `coverage` -- for each named context, the full component objects that matched its predicate
- `resolvedRules` -- each rule with the full component objects for sctx, tctx, and actx embedded inline
