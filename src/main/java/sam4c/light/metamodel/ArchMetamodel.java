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

            MClass.builder("VM")  .superType("Component").build(),
            MClass.builder("App") .superType("Component").build(),
            MClass.builder("Data").superType("Component").build(),

            MClass.builder("Port").superType("ContextualElement").build(),

            MClass.builder("Connector").superType("ElementApp")
                .attr("external", BOOLEAN, 0, 1)
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
