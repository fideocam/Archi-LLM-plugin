package com.archimatetool.archigpt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/**
 * {@link ModelContextChunkPlanner#filterChunksForPrimaryDiagram} keeps view XML for the editor’s diagram
 * and drops folder-only excerpts during chunked analysis.
 */
public class ModelContextChunkPlannerFilterTest {

    @Test
    public void filter_keepsOnlyChunksContainingNamedView() {
        List<ModelContextChunkPlanner.PlannedChunk> all = new ArrayList<>();
        all.add(new ModelContextChunkPlanner.PlannedChunk("Folder \"Business\"",
                "<folder name=\"Business\"><element type=\"BusinessActor\" name=\"A\" id=\"id-a\"/></folder>"));
        all.add(new ModelContextChunkPlanner.PlannedChunk("View: Integration",
                "<viewsAndDiagrams><view name=\"Integration\" viewpoint=\"\"><node/></view></viewsAndDiagrams>"));
        all.add(new ModelContextChunkPlanner.PlannedChunk("Folder \"Application\"", "<folder name=\"Application\"/>"));

        List<ModelContextChunkPlanner.PlannedChunk> f = ModelContextChunkPlanner.filterChunksForPrimaryDiagram(all, "Integration");
        assertEquals(1, f.size());
        assertEquals("View: Integration", f.get(0).title);
    }

    @Test
    public void filter_emptyName_returnsOriginalList() {
        List<ModelContextChunkPlanner.PlannedChunk> all = new ArrayList<>();
        all.add(new ModelContextChunkPlanner.PlannedChunk("a", "x"));
        assertSame(all, ModelContextChunkPlanner.filterChunksForPrimaryDiagram(all, ""));
        assertSame(all, ModelContextChunkPlanner.filterChunksForPrimaryDiagram(all, "   "));
    }

    @Test
    public void filter_noMatch_returnsOriginalList() {
        List<ModelContextChunkPlanner.PlannedChunk> all = new ArrayList<>();
        all.add(new ModelContextChunkPlanner.PlannedChunk("Folder \"X\"", "<folder/>"));
        assertSame(all, ModelContextChunkPlanner.filterChunksForPrimaryDiagram(all, "NonexistentView"));
    }

    @Test
    public void filter_escapesSpecialCharactersInDiagramName() {
        List<ModelContextChunkPlanner.PlannedChunk> all = new ArrayList<>();
        all.add(new ModelContextChunkPlanner.PlannedChunk("View: A & B",
                "<viewsAndDiagrams><view name=\"A &amp; B\" viewpoint=\"\"/></viewsAndDiagrams>"));
        List<ModelContextChunkPlanner.PlannedChunk> f = ModelContextChunkPlanner.filterChunksForPrimaryDiagram(all, "A & B");
        assertEquals(1, f.size());
    }

    @Test
    public void filter_multiViewBatch_keepsWhenXmlContainsNamedView() {
        List<ModelContextChunkPlanner.PlannedChunk> all = new ArrayList<>();
        all.add(new ModelContextChunkPlanner.PlannedChunk("Views: Alpha, Beta",
                "<viewsAndDiagrams><view name=\"Alpha\"/><view name=\"Beta\"/></viewsAndDiagrams>"));
        List<ModelContextChunkPlanner.PlannedChunk> f = ModelContextChunkPlanner.filterChunksForPrimaryDiagram(all, "Beta");
        assertEquals(1, f.size());
    }
}
