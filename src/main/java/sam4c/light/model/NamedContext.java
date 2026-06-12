package sam4c.light.model;

import sam4c.light.model.ref.Ref;
import java.util.List;

public record NamedContext(String name, List<Ref> conditions) {}
