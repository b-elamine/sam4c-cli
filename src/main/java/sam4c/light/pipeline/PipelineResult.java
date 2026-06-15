package sam4c.light.pipeline;

import sam4c.light.model.UnifiedModel;

import java.util.List;

/**
 * Outcome of running the full pipeline on a pair of inputs.
 *
 * @param model              the merged unified model (null if conformance failed)
 * @param conformanceErrors  structural M2 violations -- non-empty means the model was rejected
 * @param warnings           semantic warnings (empty rules, unmatched contexts) -- advisory
 */
public record PipelineResult(
        UnifiedModel model,
        List<String> conformanceErrors,
        List<String> warnings
) {
    public boolean ok() {
        return conformanceErrors.isEmpty() && model != null;
    }
}
