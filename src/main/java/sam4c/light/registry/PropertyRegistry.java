package sam4c.light.registry;

import sam4c.light.model.ref.Ref;
import sam4c.light.model.rule.*;

import java.util.*;

public class PropertyRegistry {

    private final Map<String, RuleFactory> factories = new LinkedHashMap<>();

    public static PropertyRegistry withDefaults() {
        PropertyRegistry r = new PropertyRegistry();
        r.register(new RuleFactory() {
            public String keyword() { return "Confidentiality"; }
            public SecurityRule create(List<Ref> args, Ref ret) {
                return new Confidentiality(require(args, 0, "Confidentiality"), get(args, 1));
            }
        });
        r.register(new RuleFactory() {
            public String keyword() { return "Integrity"; }
            public SecurityRule create(List<Ref> args, Ref ret) {
                return new Integrity(require(args, 0, "Integrity"), get(args, 1));
            }
        });
        r.register(new RuleFactory() {
            public String keyword() { return "Isolation"; }
            public SecurityRule create(List<Ref> args, Ref ret) {
                return new Isolation(require(args, 0, "Isolation"), get(args, 1));
            }
        });
        r.register(new RuleFactory() {
            public String keyword() { return "Authentication"; }
            public SecurityRule create(List<Ref> args, Ref ret) {
                return new Authentication(
                        require(args, 0, "Authentication"),
                        require(args, 1, "Authentication"),
                        Objects.requireNonNull(ret, "Authentication requires a -> target"));
            }
        });
        return r;
    }

    public void register(RuleFactory factory) {
        factories.put(factory.keyword(), factory);
    }

    public SecurityRule create(String keyword, List<Ref> args, Ref returnRef) {
        RuleFactory factory = factories.get(keyword);
        if (factory == null)
            throw new IllegalArgumentException(
                    "Unknown property '" + keyword + "'. Known: " + factories.keySet());
        return factory.create(args, returnRef);
    }

    public Set<String> keywords() {
        return Collections.unmodifiableSet(factories.keySet());
    }

    private static Ref require(List<Ref> args, int i, String keyword) {
        if (i >= args.size())
            throw new IllegalArgumentException(keyword + ": missing argument at position " + i);
        return args.get(i);
    }

    private static Ref get(List<Ref> args, int i) {
        return i < args.size() ? args.get(i) : null;
    }
}
