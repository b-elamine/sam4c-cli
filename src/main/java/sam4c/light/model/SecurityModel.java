package sam4c.light.model;

import sam4c.light.model.rule.SecurityRule;
import java.util.List;

public record SecurityModel(
        List<AttributeType> attributes,
        List<NamedContext> contexts,
        List<SecurityRule> rules
) {}
