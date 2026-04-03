package com.archimatetool.archigpt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class ModelXmlChunkerTest {

    @Test
    public void singleChunkWhenSmall() {
        List<String> c = ModelXmlChunker.split("<model>hi</model>", 10000);
        assertEquals(1, c.size());
        assertEquals("<model>hi</model>", c.get(0));
    }

    @Test
    public void splitsAtViewBoundary() {
        StringBuilder sb = new StringBuilder();
        sb.append("<root>");
        for (int i = 0; i < 80; i++) {
            sb.append("<view>n").append(i).append("</view>");
        }
        sb.append("</root>");
        String xml = sb.toString();
        List<String> c = ModelXmlChunker.split(xml, 200);
        assertTrue(c.size() >= 2);
        assertEquals(xml, String.join("", c));
    }
}
