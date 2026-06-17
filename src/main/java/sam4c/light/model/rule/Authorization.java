package sam4c.light.model.rule;

import sam4c.light.model.ref.Ref;
import java.util.List;

/**
 * Authorization: the {@code subject} may perform {@code actions} on the {@code resource}.
 *
 * Authentication answers "who are you"; Authorization answers "what may you do" once in.
 * One rule can grant several actions: Authorization((Role=administrator), dbCtx, read, write).
 * Generates RBAC / IAM: subject may perform the listed actions on resource.
 */
public record Authorization(Ref subject, Ref resource, List<String> actions) implements SecurityRule {}
