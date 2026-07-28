package com.lld.patterns.behavioral.template;

/**
 * TEMPLATE METHOD — define the skeleton of an algorithm in a base class, deferring some steps to
 * subclasses. Subclasses vary specific steps without changing the algorithm's structure.
 *
 * When to use in LLD:
 *   - Fixed multi-step workflows with pluggable steps: data pipelines (extract/transform/load),
 *     report generation, game turns, request-processing lifecycles.
 *
 * Contrast with Strategy: Template Method uses inheritance to vary steps; Strategy uses
 * composition to swap whole algorithms.
 */

abstract class DataProcessor {

    /** The template method: fixed order, final so subclasses can't reshuffle the skeleton. */
    final void process() {
        readData();
        Object data = parseData();     // step varies per subclass
        Object result = transform(data);
        writeData(result);             // step varies per subclass
        if (shouldNotify()) notifyDone();   // optional "hook" step
    }

    // Steps common to all -> concrete here.
    private void readData()  { System.out.println("Reading raw bytes"); }
    private Object transform(Object data) { System.out.println("Transforming " + data); return "TRANSFORMED(" + data + ")"; }
    private void notifyDone() { System.out.println("Notify: processing complete"); }

    // Steps that vary -> abstract, implemented by subclasses.
    protected abstract Object parseData();
    protected abstract void writeData(Object result);

    // Hook with a default -> subclasses may override.
    protected boolean shouldNotify() { return false; }
}

class CsvProcessor extends DataProcessor {
    protected Object parseData() { System.out.println("Parsing CSV"); return "csv-rows"; }
    protected void writeData(Object result) { System.out.println("Writing to database: " + result); }
    @Override protected boolean shouldNotify() { return true; } // opt into the hook
}

class JsonProcessor extends DataProcessor {
    protected Object parseData() { System.out.println("Parsing JSON"); return "json-tree"; }
    protected void writeData(Object result) { System.out.println("Writing to S3: " + result); }
}

public class TemplateDemo {
    public static void main(String[] args) {
        System.out.println("== CSV ==");
        new CsvProcessor().process();

        System.out.println("== JSON ==");
        new JsonProcessor().process();
    }
}
