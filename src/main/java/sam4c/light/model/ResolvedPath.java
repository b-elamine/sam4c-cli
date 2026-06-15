package sam4c.light.model;

import java.util.List;

/**
 * A concrete network path that connects a security rule's source side to its
 * target side, through one connector.
 *
 * A rule like Confidentiality(dbCtx, backendCtx) resolves its sctx/tctx to
 * components -- but a generator also needs to know WHERE the protected traffic
 * actually flows. That is this: the connector that both sides attach to, plus
 * the links (with direction) on each side.
 *
 * Example for Confidentiality(db, backend) in the clinic model:
 *   connector  = "BE_to_DB"
 *   sctxLinks  = [ PatientDB.db_in (IN) ]      -- the db side touching BE_to_DB
 *   tctxLinks  = [ SpringAPI.db_out (OUT) ]    -- the backend side touching BE_to_DB
 *
 * A generator reads this directly:
 *   - Confidentiality -> enable TLS on connector BE_to_DB
 *   - Isolation       -> a non-empty path here is a VIOLATION (they share a connector)
 *   - Authentication  -> gate the ingress link on the target side
 *
 * Direction rides along on each Link, so directional properties (e.g. Integrity)
 * can consult the flow without any extra resolution step.
 */
public record ResolvedPath(
        String connector,
        List<Link> sctxLinks,
        List<Link> tctxLinks
) {}
