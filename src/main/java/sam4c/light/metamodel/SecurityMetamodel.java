package sam4c.light.metamodel;

import static sam4c.light.metamodel.MDataType.STRING;

/**
 * Security sub-metamodel.
 * Defines every concept needed to express security requirements.
 * Equivalent to the "security" subpackage in sam4c.ecore.
 *
 * Structure:
 *
 *   SecurityModel                        -- root container
 *     ├── AttributeType [0..*]           -- e.g. Domain = (frontend, backend)
 *     ├── NamedContext  [0..*]           -- named predicate over architecture elements
 *     └── SecurityRule  [0..*] (abstract)
 *           ├── Confidentiality(sctx, tctx?)
 *           ├── Integrity(sctx, tctx?)
 *           ├── Isolation(sctx, tctx?)
 *           └── Authentication(sctx, actx, tctx)
 *
 *   Ref (abstract)                       -- reference inside a rule or context
 *     ├── NamedRef                       -- reference by name ("frontendCtx", "Nginx")
 *     ├── ValuedAttrRef                  -- inline predicate "(Domain=frontend)"
 *     └── ComposedRef                    -- conjunction "(Domain=frontend):(Role=admin)"
 *
 * AbstractContext is the shared interface between NamedContext and architecture
 * elements (ContextualElement). Security rules reference AbstractContext, which
 * is satisfied by both named contexts and architecture components directly.
 *
 * To add a new security property:
 *   1. Add its MClass here (extends SecurityRule, declare refs)
 *   2. Add the corresponding Java record in model/rule/
 *   3. Register a RuleFactory in PropertyRegistry
 *   4. Add a case to ModelMerger.resolveRule() and ConformanceChecker.checkRule()
 */
public final class SecurityMetamodel {

    public static final MPackage INSTANCE = define();

    private SecurityMetamodel() {}

    private static MPackage define() {
        return new MPackage("security", "http://avalon.inria.fr/sam4c/security/",
                java.util.List.of(

            MClass.builder("AbstractContext").abstractClass().build(),

            MClass.builder("SecurityModel")
                .ref("attributes", "AttributeType", true, 0, -1)
                .ref("contexts",   "NamedContext",  true, 0, -1)
                .ref("rules",      "SecurityRule",  true, 0, -1)
                .build(),

            MClass.builder("AttributeType").superType("AbstractContext")
                .attr("name",   STRING, 1,  1)
                .attr("values", STRING, 0, -1)
                .build(),

            MClass.builder("NamedContext").superType("AbstractContext")
                .attr("name", STRING, 1, 1)
                .ref("conditions", "Ref", true, 1, -1)
                .build(),

            MClass.builder("SecurityRule").abstractClass().build(),

            MClass.builder("Confidentiality").superType("SecurityRule")
                .ref("sctx", "Ref", true, 1, 1)
                .ref("tctx", "Ref", true, 0, 1)
                .build(),

            MClass.builder("Integrity").superType("SecurityRule")
                .ref("sctx", "Ref", true, 1, 1)
                .ref("tctx", "Ref", true, 0, 1)
                .build(),

            MClass.builder("Isolation").superType("SecurityRule")
                .ref("sctx", "Ref", true, 1, 1)
                .ref("tctx", "Ref", true, 0, 1)
                .build(),

            MClass.builder("Authentication").superType("SecurityRule")
                .ref("sctx", "Ref", true, 1, 1)
                .ref("actx", "Ref", true, 1, 1)
                .ref("tctx", "Ref", true, 1, 1)
                .build(),

            MClass.builder("Ref").abstractClass().build(),

            MClass.builder("NamedRef").superType("Ref")
                .attr("name", STRING, 1, 1)
                .build(),

            MClass.builder("ValuedAttrRef").superType("Ref")
                .attr("attribute", STRING, 1, 1)
                .attr("value",     STRING, 1, 1)
                .build(),

            MClass.builder("ComposedRef").superType("Ref")
                .ref("conditions", "Ref", true, 2, -1)
                .build()
        ));
    }
}
