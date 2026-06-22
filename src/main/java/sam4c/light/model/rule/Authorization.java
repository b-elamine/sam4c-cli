package sam4c.light.model.rule;

import sam4c.light.model.ref.Ref;
import java.util.List;

// subject may perform `actions` on resource (think RBAC / IAM). One rule can grant
// several actions at once, e.g. Authorization((Role=administrator), dbCtx, read, write).
public record Authorization(Ref subject, Ref resource, List<String> actions) implements SecurityRule {}
