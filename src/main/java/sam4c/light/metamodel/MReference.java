package sam4c.light.metamodel;

public record MReference(
        String name,
        String targetClass,
        boolean containment,
        int lowerBound,
        int upperBound
) {
    public boolean required() { return lowerBound >= 1; }
    public boolean many()     { return upperBound == -1 || upperBound > 1; }
}
