package sam4c.light;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import sam4c.light.loader.ArchLoader;
import sam4c.light.model.Architecture;
import sam4c.light.model.UnifiedModel;
import sam4c.light.metamodel.MClass;
import sam4c.light.metamodel.MPackage;
import sam4c.light.metamodel.Sam4cMetamodel;
import sam4c.light.output.HtmlReportGenerator;
import sam4c.light.output.ModelInspector;
import sam4c.light.output.ModelSerializer;
import sam4c.light.output.SecDslScaffolder;
import sam4c.light.pipeline.Pipeline;
import sam4c.light.pipeline.PipelineResult;
import sam4c.light.registry.ComponentRegistry;
import sam4c.light.web.WebServer;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.Callable;

@Command(
        name = "s4clight",
        mixinStandardHelpOptions = true,
        version = "1.0",
        description = "Merge an architecture (YAML) and security rules (.secdsl) into a unified model."
)
public class Main implements Callable<Integer> {

    @Parameters(index = "0", description = "Architecture file (.arch.yaml)", arity = "0..1")
    private File archFile;

    @Parameters(index = "1", description = "Security rules file (.secdsl)", arity = "0..1")
    private File rulesFile;

    @Option(names = {"-o", "--output"}, description = "Output file (.sam4c.json). Defaults to <arch-name>.sam4c.json")
    private File output;

    @Option(names = {"--inspect"}, description = "Print a full object-graph traversal showing every rule's resolved components")
    private boolean inspect;

    @Option(names = {"--metamodel"}, description = "Print the M2 metamodel definition and exit")
    private boolean printMetamodel;

    @Option(names = {"--validate"}, description = "Print resolution report and exit without writing output")
    private boolean validateOnly;

    @Option(names = {"--strict"}, description = "Treat semantic warnings (unmet/violated security rules) as failures: exit non-zero and write nothing")
    private boolean strict;

    @Option(names = {"--html"}, description = "Generate an interactive HTML graph from the in-memory object graph")
    private boolean generateHtml;

    @Option(names = {"--init-secdsl"}, description = "Generate a starter .secdsl file from the architecture's attributes and exit")
    private boolean initSecdsl;

    @Option(names = {"--serve"}, description = "Start the interactive web modeller (browser editor + live graph)")
    private boolean serve;

    @Option(names = {"--port"}, description = "Port for --serve (default 8080)")
    private int port = 8080;

    @Override
    public Integer call() {
        try {
            if (printMetamodel) { printMetamodel(); return 0; }

            if (initSecdsl) {
                if (archFile == null) {
                    System.err.println("Usage: s4clight <arch.yaml> --init-secdsl");
                    return 1;
                }
                if (!archFile.exists()) { System.err.println("Architecture file not found: " + archFile); return 1; }

                Architecture arch = new ArchLoader(ComponentRegistry.withDefaults()).load(archFile);
                File secOut = output != null ? output
                        : new File(archFile.getParent(),
                                   archFile.getName().replaceAll("\\.arch\\.yaml$|\\.yaml$", "") + ".secdsl");
                if (secOut.exists()) {
                    System.err.println("Refusing to overwrite existing file: " + secOut);
                    System.err.println("Delete it first or pass -o <file> to write elsewhere.");
                    return 1;
                }
                SecDslScaffolder.write(arch, secOut);
                System.out.println("Wrote starter security model to: " + secOut);
                return 0;
            }

            if (serve) {
                new WebServer().start(port);
                Thread.currentThread().join(); // block until Ctrl+C
                return 0;
            }

            if (archFile == null || rulesFile == null) {
                System.err.println("Usage: s4clight <arch.yaml> <rules.secdsl> [options]");
                return 1;
            }
            if (!archFile.exists()) { System.err.println("Architecture file not found: " + archFile); return 1; }
            if (!rulesFile.exists()) { System.err.println("Rules file not found: " + rulesFile); return 1; }

            System.out.println("Loading architecture: " + archFile);
            System.out.println("Parsing security rules: " + rulesFile);
            String archContent  = Files.readString(archFile.toPath());
            String rulesContent = Files.readString(rulesFile.toPath());

            System.out.println("Running pipeline (conformance + merge + semantic checks)...");
            PipelineResult result = new Pipeline().run(archContent, rulesContent, archFile.getName());

            if (!result.ok()) {
                System.err.println("Conformance violations:");
                result.conformanceErrors().forEach(e -> System.err.println("  " + e));
                return 1;
            }
            System.out.println("  Conformance OK");
            UnifiedModel unified = result.model();

            if (inspect) {
                ModelInspector.inspect(unified);
            } else {
                printReport(unified);
            }

            if (!result.warnings().isEmpty()) {
                System.out.println("\n-- Semantic warnings --");
                result.warnings().forEach(w -> System.out.println("  ! " + w));
            }

            if (!unified.unresolved().isEmpty()) {
                System.err.println("\nWARNING: " + unified.unresolved().size()
                        + " unresolved reference(s). Check names in your .secdsl file.");
            }

            // --strict: a security warning or an unresolved ref blocks (write nothing, fail)
            if (strict && (!result.warnings().isEmpty() || !unified.unresolved().isEmpty())) {
                System.err.println("\nFAILED (--strict): " + result.warnings().size()
                        + " semantic warning(s), " + unified.unresolved().size()
                        + " unresolved reference(s). No output written.");
                return 1;
            }

            if (validateOnly) return unified.unresolved().isEmpty() ? 0 : 1;


            File out = output != null ? output
                    : new File(archFile.getParent(),
                               archFile.getName().replaceAll("\\.arch\\.yaml$|\\.yaml$", "") + ".sam4c.json");

            ModelSerializer.write(unified, out);
            System.out.println("\nWrote unified model to: " + out);

            if (generateHtml) {
                File htmlOut = new File(out.getParent(),
                        out.getName().replace(".sam4c.json", "") + ".sam4c.html");
                HtmlReportGenerator.write(unified, htmlOut);
                System.out.println("Wrote interactive graph to: " + htmlOut);
            }

            return 0;

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private void printReport(UnifiedModel unified) {
        System.out.println("\n-- Architecture --");
        System.out.println("  Name       : " + unified.architecture().name());
        System.out.println("  Components : " + unified.architecture().components().size());
        System.out.println("  Connectors : " + unified.architecture().connectors().size());
        System.out.println("  Links      : " + unified.architecture().links().size());

        System.out.println("\n-- Security --");
        System.out.println("  Attributes : " + unified.security().attributes().size());
        System.out.println("  Contexts   : " + unified.security().contexts().size());
        System.out.println("  Rules      : " + unified.security().rules().size());

        System.out.println("\n-- Coverage (context -> matched components) --");
        unified.coverage().forEach((ctx, comps) -> {
            String names = comps.isEmpty() ? "(no match)"
                    : comps.stream().map(sam4c.light.model.Component::name)
                           .collect(java.util.stream.Collectors.joining(", "));
            System.out.printf("  %-25s -> [%s]%n", ctx, names);
        });

        System.out.println("\n-- Resolved rules --");
        unified.resolvedRules().forEach(r -> {
            String type = r.rule().getClass().getSimpleName();
            String sctx = names(r.sctxComponents());
            String tctx = names(r.tctxComponents());
            String actx = names(r.actxComponents());
            System.out.printf("  %-20s sctx=[%s]%n", type, sctx);
            if (!r.tctxComponents().isEmpty())
                System.out.printf("  %-20s tctx=[%s]%n", "", tctx);
            if (!r.actxComponents().isEmpty())
                System.out.printf("  %-20s actx=[%s]%n", "", actx);
        });

        if (!unified.unresolved().isEmpty()) {
            System.out.println("\n-- Unresolved references --");
            unified.unresolved().forEach(n -> System.out.println("  x " + n));
        }
    }

    private static void printMetamodel() {
        printPackage("CORE        ", Sam4cMetamodel.CORE);
        printPackage("ARCHITECTURE", Sam4cMetamodel.ARCH);
        printPackage("SECURITY    ", Sam4cMetamodel.SECURITY);
    }

    private static void printPackage(String label, MPackage mm) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  " + label + "  nsURI=" + mm.nsURI());
        System.out.println("=".repeat(60));
        for (MClass cls : mm.classes()) {
            String header = (cls.abstractClass() ? "abstract " : "") + "class " + cls.name();
            if (!cls.superTypes().isEmpty())
                header += " extends " + String.join(", ", cls.superTypes());
            System.out.println("\n  " + header);
            for (var a : cls.attributes())
                System.out.printf("    attr  %-20s : %-8s [%s..%s]%n",
                        a.name(), a.type(),
                        a.lowerBound(), a.upperBound() == -1 ? "*" : a.upperBound());
            for (var r : cls.references())
                System.out.printf("    ref   %-20s -> %-20s [%s..%s]%s%n",
                        r.name(), r.targetClass(),
                        r.lowerBound(), r.upperBound() == -1 ? "*" : r.upperBound(),
                        r.containment() ? " (containment)" : "");
        }
    }

    private static String names(java.util.List<sam4c.light.model.Component> comps) {
        return comps.stream().map(sam4c.light.model.Component::name)
                .collect(java.util.stream.Collectors.joining(", "));
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new Main()).execute(args));
    }
}
