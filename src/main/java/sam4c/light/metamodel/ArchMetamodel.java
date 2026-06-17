package sam4c.light.metamodel;

import static sam4c.light.metamodel.MDataType.*;

/**
 * Architecture sub-metamodel.
 * Defines every concept needed to describe a cloud application architecture.
 * Equivalent to the "architecture" subpackage in sam4c.ecore.
 *
 * Inheritance:
 *
 *   ContextualElement (from core)
 *     └── ElementApp (abstract)   -- base of all architecture elements
 *           ├── Component (abstract)
 *           │     ├── VM          -- virtual machine or grouping element
 *           │     ├── App         -- running application / microservice
 *           │     └── Data        -- data store
 *           ├── Connector         -- communication channel
 *           └── Port              -- connection point on a component
 *
 *   Architecture                  -- root container
 *   Link                          -- wires a port to a connector
 *
 * The key design choice: Component extends ContextualElement (inherited from
 * sam4c.ecore). This means any architecture element IS a valid context target
 * in security rules -- the bridge between the two metamodels.
 */
public final class ArchMetamodel {

    public static final MPackage INSTANCE = define();

    private ArchMetamodel() {}

    private static MPackage define() {
        return new MPackage("architecture", "http://avalon.inria.fr/sam4c/architecture/",
                java.util.List.of(

            MClass.builder("ElementApp").abstractClass()
                .superType("ContextualElement")
                .build(),

            MClass.builder("Component").abstractClass()
                .superType("ElementApp")
                .attr("type",     STRING,  1, 1)
                .attr("external", BOOLEAN, 0, 1)
                .ref("ports",    "Port",      true, 0, -1)
                .ref("children", "Component", true, 0, -1)
                .build(),

            // Workload (abstract): something that RUNS. Carries deployment semantics.
            // Every editable field is declared here -> the loader, conformance, Studio form,
            // and serializer all derive from these declarations (single source of truth).
            MClass.builder("Workload").abstractClass().superType("Component")
                .attr("runtime",   STRING, 0, 1, "container", "process", "function")
                .attr("exposure",  STRING, 0, 1, "none", "internal", "external")
                .attr("lifecycle", STRING, 0, 1, "continuous", "batch", "scheduled")
                .attr("schedule",  STRING, 0, 1)   // cron expression (when lifecycle=scheduled)
                .attr("image",     STRING, 0, 1)   // artifact/image reference
                .attr("scale",     MAP,    0, 1)   // {replicas, min, max, metric}
                .attr("resources", MAP,    0, 1)   // {cpu, memory}
                .attr("config",    MAP,    0, 1)   // {KEY: value}
                .attr("secrets",   LIST,   0, 1)   // [NAME] references
                .attr("health",    MAP,    0, 1)   // {path, port}
                .attr("trigger",   MAP,    0, 1)   // {kind, source}
                .attr("placement", MAP,    0, 1)   // {zone, affinity, scope}
                .ref("deployedOn", "Host", false, 0, 1)   // placement (reference, not containment)
                .build(),

            MClass.builder("App") .superType("Workload").build(),   // stateless
            MClass.builder("Data").superType("Workload")            // stateful
                .attr("persistent", BOOLEAN, 0, 1)
                .attr("storage",    MAP,     0, 1)   // {size, class}
                .build(),

            // Host (abstract): something workloads RUN ON.
            MClass.builder("Host").abstractClass().superType("Component")
                .attr("capacity", MAP, 0, 1)   // {cpu, memory}
                .build(),
            MClass.builder("VM")             .superType("Host").build(),
            MClass.builder("PM").superType("Host").build(),
            MClass.builder("Worker")    .superType("Host").build(),

            // Group (abstract): CONTAINS components (via the children tree -> single parent).
            MClass.builder("Group").abstractClass().superType("Component").build(),
            MClass.builder("Zone").superType("Group")
                .attr("boundary", STRING, 0, 1)        // logical/isolation boundary -> namespace/VPC
                .build(),
            MClass.builder("Colocation").superType("Group")
                .attr("shareNetwork", BOOLEAN, 0, 1)   // co-located, shared net/storage -> pod
                .attr("shareStorage", BOOLEAN, 0, 1)
                .attr("scale",        MAP,     0, 1)   // the group scales as a unit (R-F5)
                .build(),
            MClass.builder("HostPool").superType("Group").build(),   // a pool of Hosts -> cluster

            MClass.builder("Port").superType("ContextualElement")
                .attr("number",   INT,    0, 1)
                .attr("protocol", STRING, 0, 1, "tcp", "udp", "http", "grpc")
                .build(),

            MClass.builder("Connector").superType("ElementApp")
                .attr("external", BOOLEAN, 0, 1)
                .attr("protocol", STRING,  0, 1, "tcp", "udp", "http", "grpc")
                .build(),

            MClass.builder("Link")
                .attr("portRef",       STRING, 1, 1)
                .attr("connectorName", STRING, 1, 1)
                .attr("direction",     STRING, 0, 1)   // in | out | inout (default inout)
                .build(),

            MClass.builder("Architecture").superType("ElementApp")
                .ref("components", "Component", true, 0, -1)
                .ref("connectors", "Connector", true, 0, -1)
                .ref("links",      "Link",      true, 0, -1)
                .build()
        ));
    }
}
