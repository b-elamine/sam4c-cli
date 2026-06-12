package sam4c.light.model.rule;

import sam4c.light.model.ref.Ref;

public record Authentication(Ref sctx, Ref actx, Ref tctx) implements SecurityRule {}
