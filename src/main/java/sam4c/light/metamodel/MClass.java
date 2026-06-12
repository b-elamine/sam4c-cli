package sam4c.light.metamodel;

import java.util.List;

public record MClass(
        String name,
        boolean abstractClass,
        List<String> superTypes,
        List<MAttribute> attributes,
        List<MReference> references
) {
    public static Builder builder(String name) { return new Builder(name); }

    public static class Builder {
        private final String name;
        private boolean abstractClass = false;
        private final java.util.List<String> superTypes  = new java.util.ArrayList<>();
        private final java.util.List<MAttribute> attrs   = new java.util.ArrayList<>();
        private final java.util.List<MReference> refs    = new java.util.ArrayList<>();

        Builder(String name) { this.name = name; }

        public Builder abstractClass()            { this.abstractClass = true; return this; }
        public Builder superType(String s)        { superTypes.add(s); return this; }

        public Builder attr(String n, MDataType t, int lo, int hi) {
            attrs.add(new MAttribute(n, t, lo, hi)); return this;
        }
        public Builder ref(String n, String target, boolean containment, int lo, int hi) {
            refs.add(new MReference(n, target, containment, lo, hi)); return this;
        }

        public MClass build() {
            return new MClass(name, abstractClass,
                    java.util.List.copyOf(superTypes),
                    java.util.List.copyOf(attrs),
                    java.util.List.copyOf(refs));
        }
    }
}
