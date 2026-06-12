package sam4c.light.metamodel;

import java.util.ArrayList;
import java.util.List;

/**
 * Root SAM4C metamodel -- composes core, architecture, and security.
 *
 * Mirrors the structure of the original sam4c.ecore which had three
 * subpackages: core, architecture, security.
 *
 * MDE hierarchy:
 *
 *   M3   MPackage / MClass / MAttribute / MReference    (this framework)
 *         |
 *   M2   CoreMetamodel       "http://avalon.inria.fr/sam4c/core/"
 *        ArchMetamodel       "http://avalon.inria.fr/sam4c/architecture/"
 *        SecurityMetamodel   "http://avalon.inria.fr/sam4c/security/"
 *        Sam4cMetamodel      "http://avalon.inria.fr/sam4c/"  (root, this class)
 *         |
 *   M1   Architecture + SecurityModel instances (loaded from YAML + .secdsl)
 *         |
 *   M0   the running system
 *
 * The bridge between the two domain metamodels:
 *   ContextualElement (core) is a supertype of Component (architecture)
 *   AbstractContext (security) is the target type of security rule references
 *   Both ContextualElement and NamedContext extend AbstractContext, which
 *   means architecture elements can be referenced directly in security rules.
 */
public final class Sam4cMetamodel {

    public static final MPackage CORE     = CoreMetamodel.INSTANCE;
    public static final MPackage ARCH     = ArchMetamodel.INSTANCE;
    public static final MPackage SECURITY = SecurityMetamodel.INSTANCE;

    /** Flat unified package -- all classes from all sub-metamodels in one view. */
    public static final MPackage INSTANCE = compose();

    private Sam4cMetamodel() {}

    private static MPackage compose() {
        List<MClass> all = new ArrayList<>();
        all.addAll(CORE.classes());
        all.addAll(ARCH.classes());
        all.addAll(SECURITY.classes());
        return new MPackage("sam4c", "http://avalon.inria.fr/sam4c/", all);
    }
}
