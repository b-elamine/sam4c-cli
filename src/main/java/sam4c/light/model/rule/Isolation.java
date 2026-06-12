package sam4c.light.model.rule;

import sam4c.light.model.ref.Ref;

public record Isolation(Ref sctx, Ref tctx) implements SecurityRule {}
