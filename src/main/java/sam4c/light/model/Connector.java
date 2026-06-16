package sam4c.light.model;

/**
 * A named communication channel components attach to via their ports.
 *
 * @param name      the connector name
 * @param external  true if the channel originates outside the system (e.g. the internet)
 * @param protocol  the channel protocol (tcp | udp | http | grpc); null if unspecified
 *
 * `protocol` is a structural fact about the channel and belongs to the architecture.
 * Security requirements on the channel (e.g. must be encrypted) are NOT declared here --
 * they come from the security model (e.g. a Confidentiality rule) and are bound to this
 * connector during the merge, via path resolution.
 */
public record Connector(String name, boolean external, String protocol) {

    /** Back-compat: a connector with no protocol declared. */
    public Connector(String name, boolean external) {
        this(name, external, null);
    }
}
