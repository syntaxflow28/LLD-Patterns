package com.lld.patterns.practical.registry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * REGISTRY / PLUGIN — replace the switch that everyone keeps editing.
 *
 * <p>Almost every LLD design grows a method like this:
 *
 * <pre>{@code
 * switch (format) {
 *     case "csv"  -> exportCsv(rows);
 *     case "json" -> exportJson(rows);
 *     case "pdf"  -> exportPdf(rows);
 *     default     -> throw new IllegalArgumentException(format);
 * }
 * }</pre>
 *
 * <p>It works. The problem is what happens next: every new format edits this method, so every new
 * format re-tests and can re-break every existing format. That is the Open-Closed Principle
 * violation interviewers are listening for, and the fix is a registry - a {@code Map} from a key to
 * an implementation, populated once at startup, queried at request time.
 *
 * <p><b>Registry vs Factory - the distinction that gets asked.</b> A Factory <em>decides</em> which
 * object to create; the decision logic lives inside it, so the factory changes when the set of
 * products changes. A Registry <em>looks up</em> an implementation someone else already handed it;
 * it never knows the concrete types at all, so it never changes. If your factory is a bare switch
 * over a string, it wants to be a registry.
 *
 * <p><b>Registry vs Service Locator - the distinction that separates a good design from an
 * anti-pattern.</b> They are the same data structure and are judged completely differently:
 * <ul>
 *   <li><b>Fine:</b> a registry keyed by <em>runtime data</em>. The format arrives in the HTTP
 *       request; there is no way to inject the right exporter at construction time because nobody
 *       knows which one it will be. Lookup is the only option.</li>
 *   <li><b>Anti-pattern:</b> a class reaching into a global registry to fetch its <em>own fixed
 *       dependencies</em> ({@code Locator.get(EmailService.class)}). Those are known at construction
 *       time, so hiding them turns a compile-time error into a runtime one and makes the class
 *       untestable without global setup. Inject those instead.</li>
 * </ul>
 * The dividing question is: <em>could the caller have known this at construction time?</em> If yes,
 * inject. If it is genuinely chosen by data, look it up.
 *
 * <p><b>In the wild:</b> {@link java.util.ServiceLoader} plus {@code META-INF/services} is the JDK's
 * own version of this - it is how JDBC finds drivers, how SLF4J finds a logging backend, and how
 * {@code Charset} and {@code FileSystem} providers are discovered. Spring's bean-name lookup,
 * Jackson's module registration and servlet filter chains are all registries too.
 */
interface ReportExporter {

    /** The key clients use. Owned by the implementation - not by a switch somewhere else. */
    String format();

    String export(List<Map<String, String>> rows);
}

class CsvExporter implements ReportExporter {

    @Override
    public String format() {
        return "csv";
    }

    @Override
    public String export(List<Map<String, String>> rows) {
        if (rows.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(String.join(",", rows.get(0).keySet()));
        for (Map<String, String> row : rows) {
            out.append('\n').append(String.join(",", row.values()));
        }
        return out.toString();
    }
}

class JsonExporter implements ReportExporter {

    @Override
    public String format() {
        return "json";
    }

    @Override
    public String export(List<Map<String, String>> rows) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append('{');
            boolean first = true;
            for (Map.Entry<String, String> field : rows.get(i).entrySet()) {
                if (!first) {
                    out.append(',');
                }
                out.append('"').append(field.getKey()).append("\":\"").append(field.getValue()).append('"');
                first = false;
            }
            out.append('}');
        }
        return out.append(']').toString();
    }
}

/** The new requirement that arrives on day 40. Note that nothing existing had to change for it. */
class MarkdownExporter implements ReportExporter {

    @Override
    public String format() {
        return "markdown";
    }

    @Override
    public String export(List<Map<String, String>> rows) {
        if (rows.isEmpty()) {
            return "";
        }
        Set<String> headers = rows.get(0).keySet();
        StringBuilder out = new StringBuilder("| " + String.join(" | ", headers) + " |\n");
        out.append("|").append(" --- |".repeat(headers.size()));
        for (Map<String, String> row : rows) {
            out.append("\n| ").append(String.join(" | ", row.values())).append(" |");
        }
        return out.toString();
    }
}

class ExporterRegistry {

    // LinkedHashMap so supportedFormats() reads in registration order - error messages and /formats
    // endpoints that reshuffle between runs are a small but real annoyance.
    private final Map<String, ReportExporter> exporters = new LinkedHashMap<>();

    /**
     * Duplicate policy is a real design decision, not a detail. The two options:
     * <ul>
     *   <li><b>Reject</b> (chosen here): two plugins claiming {@code "csv"} is a packaging mistake,
     *       and failing at startup is far better than silently exporting the wrong format in
     *       production, where which one wins depends on classpath order.</li>
     *   <li><b>Last-one-wins</b>: correct when overriding is the point - test doubles replacing real
     *       implementations, or a customer-specific plugin shadowing the default.</li>
     * </ul>
     * Say which one you picked and why. "I'd reject, because a silent override is a bug you find in
     * production" is the answer that lands.
     */
    void register(ReportExporter exporter) {
        ReportExporter clash = exporters.putIfAbsent(exporter.format(), exporter);
        if (clash != null) {
            throw new IllegalStateException("format '" + exporter.format() + "' is already registered by "
                    + clash.getClass().getSimpleName());
        }
    }

    /** Optional, not null, and not a throw: the caller decides whether an unknown format is fatal. */
    Optional<ReportExporter> lookup(String format) {
        return Optional.ofNullable(exporters.get(format));
    }

    Set<String> supportedFormats() {
        return exporters.keySet();
    }
}

/** The class that used to hold the switch. It now holds nothing but a lookup. */
class ReportService {

    private final ExporterRegistry registry;

    ReportService(ExporterRegistry registry) {
        this.registry = registry;
    }

    String export(String format, List<Map<String, String>> rows) {
        return registry.lookup(format)
                // The error message lists what IS supported. A bare "unknown format: xlsx" makes the
                // caller go read your source; this one answers the next question before it is asked.
                .orElseThrow(() -> new IllegalArgumentException(
                        "unsupported format '" + format + "'; supported: " + registry.supportedFormats()))
                .export(rows);
    }
}

public class PluginRegistryDemo {

    public static void main(String[] args) {
        List<Map<String, String>> rows = List.of(
                orderedRow("id", "1", "name", "Priya", "city", "Pune"),
                orderedRow("id", "2", "name", "Rahul", "city", "Delhi"));

        // The composition root - the ONE place that knows the concrete implementations. Everything
        // downstream sees only the ReportExporter interface.
        ExporterRegistry registry = new ExporterRegistry();
        registry.register(new CsvExporter());
        registry.register(new JsonExporter());
        ReportService service = new ReportService(registry);

        section("1. Lookup instead of switch");
        System.out.println("  supported: " + registry.supportedFormats());
        System.out.println("  csv  ->\n" + indent(service.export("csv", rows)));
        System.out.println("  json ->\n" + indent(service.export("json", rows)));

        section("2. Adding a format costs one class and one line");
        registry.register(new MarkdownExporter());
        System.out.println("  supported: " + registry.supportedFormats());
        System.out.println("  markdown ->\n" + indent(service.export("markdown", rows)));
        System.out.println("  ReportService was not opened. Neither were CsvExporter or JsonExporter.");
        System.out.println("  That is Open-Closed with a diff to prove it: +1 file, +1 registration line,");
        System.out.println("  0 edits to working code. A switch statement cannot make that claim.");

        section("3. Unknown formats fail with a usable message");
        try {
            service.export("xlsx", rows);
        } catch (IllegalArgumentException expected) {
            System.out.println("      " + expected.getMessage());
        }
        System.out.println("  Listing the supported formats turns a support ticket into a self-fix.");

        section("4. Duplicate registration is rejected, loudly and early");
        try {
            registry.register(new CsvExporter());
        } catch (IllegalStateException expected) {
            System.out.println("      " + expected.getMessage());
        }
        System.out.println("  This fires at startup, not at 2am on the one request that hit the shadowed");
        System.out.println("  exporter. If overriding IS the goal (test doubles, per-tenant plugins),");
        System.out.println("  make last-one-wins an explicit register(exporter, ALLOW_OVERRIDE) choice");
        System.out.println("  rather than the silent default.");

        section("5. Self-registration is tempting and usually wrong");
        System.out.println("  The 'clever' version has each exporter register itself in a static block.");
        System.out.println("  It breaks in a way that is hard to debug: static initialisers only run when");
        System.out.println("  the class is first loaded, and if nothing references MarkdownExporter, it is");
        System.out.println("  never loaded, never registers, and the format silently does not exist.");
        System.out.println("  Explicit registration in one composition root is boring and always works.");

        section("6. How the JDK does this: ServiceLoader");
        System.out.println("  Instead of hard-coding the register(...) calls, put the implementation names");
        System.out.println("  in META-INF/services/com.lld.patterns.practical.registry.ReportExporter (or");
        System.out.println("  use 'provides ... with ...' in module-info) and populate the registry with:");
        System.out.println();
        System.out.println("      ServiceLoader.load(ReportExporter.class).forEach(registry::register);");
        System.out.println();
        System.out.println("  Now dropping a jar on the classpath adds a format with no code change at all.");
        System.out.println("  This is exactly how JDBC finds drivers and how SLF4J finds a backend.");

        section("7. Registry vs Service Locator");
        System.out.println("  Same Map, opposite verdicts. The test is: could the caller have known this");
        System.out.println("  dependency at construction time?");
        System.out.println("    - format arrives in the HTTP request  -> nobody could know it -> look it up.");
        System.out.println("    - ReportService needs an EmailService -> known at wiring time  -> inject it.");
        System.out.println("  A class that reaches into a global registry for its own fixed collaborators");
        System.out.println("  has hidden its dependencies: the constructor lies, the compiler cannot help,");
        System.out.println("  and every test needs global setup. That is Service Locator, and that is the");
        System.out.println("  version to argue against.");

        System.out.println("\nDone.");
    }

    private static Map<String, String> orderedRow(String... keyValuePairs) {
        Map<String, String> row = new LinkedHashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            row.put(keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return row;
    }

    private static String indent(String block) {
        return "      " + block.replace("\n", "\n      ");
    }

    private static void section(String title) {
        System.out.println("\n--- " + title + " ---");
    }
}
