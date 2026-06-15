package sam4c.light.model;

/**
 * Flow direction of a Link, from the COMPONENT's point of view.
 *
 *   OUT   -- the component sends through this port INTO the connector  (port -> connector)
 *   IN    -- the component receives from the connector through this port (connector -> port)
 *   INOUT -- bidirectional (default)
 *
 * Replaces the original SAM4C In / Out / InOut association subclasses with a
 * single enum, while keeping the same expressivity. Component-centric naming
 * matches the usual port convention (http_in is IN, api_out is OUT).
 */
public enum Direction {
    IN, OUT, INOUT;

    /** Parse from YAML/text (case-insensitive). Blank/null -> INOUT. */
    public static Direction parse(String s) {
        if (s == null || s.isBlank()) return INOUT;
        return switch (s.trim().toLowerCase()) {
            case "in"  -> IN;
            case "out" -> OUT;
            case "inout", "both" -> INOUT;
            default -> throw new IllegalArgumentException(
                "Unknown link direction '" + s + "' (expected in, out, or inout)");
        };
    }

    /** Lowercase token for YAML output. */
    public String token() {
        return name().toLowerCase();
    }
}
