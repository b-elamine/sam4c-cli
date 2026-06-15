package sam4c.light.pipeline;

import sam4c.light.loader.ArchLoader;
import sam4c.light.loader.DslParser;
import sam4c.light.merger.ModelMerger;
import sam4c.light.merger.SemanticValidator;
import sam4c.light.metamodel.ConformanceChecker;
import sam4c.light.metamodel.Sam4cMetamodel;
import sam4c.light.model.Architecture;
import sam4c.light.model.SecurityModel;
import sam4c.light.model.UnifiedModel;
import sam4c.light.registry.ComponentRegistry;
import sam4c.light.registry.PropertyRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * The end-to-end pipeline, operating on raw content (not files).
 *
 * This is the single orchestration point shared by every entry point -- the CLI
 * and the web server both call {@link #run}. It encapsulates the full sequence:
 * load, parse, conformance-check, merge, semantic-validate.
 *
 * Working on String content (rather than File) is what makes it reusable: the
 * CLI reads files into strings; the web server receives strings over HTTP.
 */
public final class Pipeline {

    private final ComponentRegistry componentRegistry;
    private final PropertyRegistry propertyRegistry;
    private final ConformanceChecker conformanceChecker;

    public Pipeline() {
        this(ComponentRegistry.withDefaults(), PropertyRegistry.withDefaults());
    }

    public Pipeline(ComponentRegistry componentRegistry, PropertyRegistry propertyRegistry) {
        this.componentRegistry = componentRegistry;
        this.propertyRegistry = propertyRegistry;
        this.conformanceChecker = new ConformanceChecker(Sam4cMetamodel.INSTANCE);
    }

    /**
     * Run the pipeline on architecture YAML and security DSL content.
     *
     * @param archYaml    raw .arch.yaml content
     * @param secdsl      raw .secdsl content
     * @param archName    fallback name if the YAML has no `name:` field
     */
    public PipelineResult run(String archYaml, String secdsl, String archName) throws Exception {
        Architecture arch = new ArchLoader(componentRegistry).load(archYaml, archName);
        SecurityModel sec = DslParser.parse(secdsl, propertyRegistry);

        List<String> conformanceErrors = new ArrayList<>();
        conformanceErrors.addAll(conformanceChecker.check(arch));
        conformanceErrors.addAll(conformanceChecker.check(sec));
        if (!conformanceErrors.isEmpty()) {
            return new PipelineResult(null, conformanceErrors, List.of());
        }

        UnifiedModel unified = ModelMerger.merge(arch, sec);
        List<String> warnings = SemanticValidator.validate(unified);
        return new PipelineResult(unified, List.of(), warnings);
    }
}
