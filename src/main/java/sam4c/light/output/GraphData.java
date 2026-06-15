package sam4c.light.output;

/**
 * Cytoscape-ready graph data derived from a UnifiedModel.
 *
 * Each field is a JavaScript literal fragment, ready to drop into a page:
 *   nodesJson    -- array body of node objects (without the surrounding [])
 *   edgesJson    -- array body of edge objects (without the surrounding [])
 *   coverageJson -- object literal: { "ctxName": ["Comp1","Comp2"], ... }
 *
 * Produced by GraphBuilder, consumed by both HtmlReportGenerator (static file)
 * and the web server (live response), so both render the identical graph.
 */
public record GraphData(String nodesJson, String edgesJson, String coverageJson) {

    /** Convenience: the full `{ nodes: [...], edges: [...] }` elements literal. */
    public String elementsJson() {
        return "{ nodes: [" + nodesJson + "], edges: [" + edgesJson + "] }";
    }
}
