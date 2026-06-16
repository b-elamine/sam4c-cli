package sam4c.light.model;

/**
 * A connection point on a component.
 *
 * @param name      the port name (e.g. "http_in"); the part after the dot in a Link's portRef
 * @param number    the port number (e.g. 8080); null if unspecified
 * @param protocol  the wire protocol (tcp | udp | http | grpc); null if unspecified
 *
 * number/protocol are what a generator needs to emit a Service, firewall rule, or
 * security-group entry. They are optional so the simple form `ports: [http_in]` still works.
 */
public record Port(String name, Integer number, String protocol) {

    /** Back-compat: a bare port has no number/protocol. */
    public Port(String name) {
        this(name, null, null);
    }
}
