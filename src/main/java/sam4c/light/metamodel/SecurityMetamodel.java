package sam4c.light.metamodel;

import static sam4c.light.metamodel.MDataType.STRING;

// The security metamodel. A SecurityModel holds attribute declarations, named
// contexts (predicates over components) and rules. Rules and contexts point at
// components through Refs (by name, by an attribute condition, or an AND of those).
// Adding a property: MClass here + record in model/rule + PropertyRegistry factory
// + cases in ModelMerger.resolveRule and ConformanceChecker.checkRule.
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

            MClass.builder("Authorization").superType("SecurityRule")
                .ref("subject",  "Ref", true, 1, 1)   // who
                .ref("resource", "Ref", true, 1, 1)   // what
                .attr("action",  STRING, 1, -1)       // one or more: read | write | admin | ...
                .build(),

            MClass.builder("Availability").superType("SecurityRule")
                .ref("target", "Ref", true, 1, 1)                    // the context being constrained
                .attr("level", STRING, 1, 1, "high", "medium", "low")
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
