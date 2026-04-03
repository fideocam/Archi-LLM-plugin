/**
 * Splits a large ArchiMate XML string into chunks for multi-pass LLM analysis.
 */
package com.archimatetool.archigpt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("nls")
public final class ModelXmlChunker {

    /** Maximum number of HTTP round-trips for chunked analysis. */
    public static final int MAX_CHUNKS = 12;

    private static final String VIEW_END = "</view>";

    private ModelXmlChunker() {}

    /**
     * Split {@code xml} into at most {@link #MAX_CHUNKS} parts, preferring cuts after {@code </view>}.
     */
    public static List<String> split(String xml, int maxChunkChars) {
        if (xml == null || xml.isEmpty()) {
            return Collections.emptyList();
        }
        if (maxChunkChars < 512) {
            maxChunkChars = 512;
        }
        if (xml.length() <= maxChunkChars) {
            return Collections.singletonList(xml);
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < xml.length() && chunks.size() < MAX_CHUNKS) {
            int end = Math.min(start + maxChunkChars, xml.length());
            if (end >= xml.length()) {
                chunks.add(xml.substring(start));
                start = xml.length();
                break;
            }
            int cut = xml.lastIndexOf(VIEW_END, end - 1);
            if (cut >= start && cut + VIEW_END.length() <= end) {
                end = cut + VIEW_END.length();
            } else {
                int nl = xml.lastIndexOf('\n', end - 1);
                if (nl > start) {
                    end = nl + 1;
                }
            }
            if (end <= start) {
                end = Math.min(start + maxChunkChars, xml.length());
            }
            chunks.add(xml.substring(start, end));
            start = end;
        }
        if (start < xml.length()) {
            if (chunks.isEmpty()) {
                chunks.add(xml.substring(start));
            } else {
                int last = chunks.size() - 1;
                chunks.set(last, chunks.get(last) + xml.substring(start));
            }
        }
        return chunks;
    }
}
