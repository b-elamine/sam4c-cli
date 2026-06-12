package sam4c.light.registry;

import sam4c.light.model.ref.Ref;
import sam4c.light.model.rule.SecurityRule;

import java.util.List;

/**
 * Implement this interface to add a new security property.
 *
 * Example -- adding NonRepudiation:
 *
 *   public class NonRepudiationFactory implements RuleFactory {
 *       public String keyword() { return "NonRepudiation"; }
 *       public SecurityRule create(List<Ref> args, Ref returnRef) {
 *           return new NonRepudiation(args.get(0));
 *       }
 *   }
 *
 * Then register it:
 *   PropertyRegistry registry = PropertyRegistry.withDefaults();
 *   registry.register(new NonRepudiationFactory());
 */
public interface RuleFactory {
    String keyword();
    SecurityRule create(List<Ref> args, Ref returnRef);
}
