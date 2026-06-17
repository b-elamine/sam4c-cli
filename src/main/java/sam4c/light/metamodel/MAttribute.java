package sam4c.light.metamodel;

import java.util.List;

/**
 * A scalar (or small structured) feature of an MClass.
 *
 * @param allowed  for a STRING field, the closed set of permitted values (an enum);
 *                 empty means free-form. Lets the metamodel describe enums so the
 *                 form, conformance, and docs all derive from one declaration.
 */
public record MAttribute(
        String name,
        MDataType type,
        int lowerBound,
        int upperBound,
        List<String> allowed
) {
    /** Convenience: a field with no enum constraint. */
    public MAttribute(String name, MDataType type, int lowerBound, int upperBound) {
        this(name, type, lowerBound, upperBound, List.of());
    }

    public boolean required()  { return lowerBound >= 1; }
    public boolean many()      { return upperBound == -1 || upperBound > 1; }
    public boolean isEnum()    { return type == MDataType.STRING && !allowed.isEmpty(); }
}
