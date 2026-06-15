package sam4c.light.model;

/**
 * Wires a component port to a connector, with a flow direction.
 *
 * portRef is "ComponentName.portName"; connectorName is the connector.
 * direction is the flow from the component's perspective (see {@link Direction}).
 */
public record Link(String portRef, String connectorName, Direction direction) {

    /** Back-compat: a link with no explicit direction is bidirectional. */
    public Link(String portRef, String connectorName) {
        this(portRef, connectorName, Direction.INOUT);
    }
}
