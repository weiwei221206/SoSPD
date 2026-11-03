package io.github.sospd.domain;

import org.junit.Test;
import static org.junit.Assert.*;

public class SearchPartitionsTest {
    @Test public void allWorkerCountsCoverTheWholeSpaceExactlyOnceAcrossWraparound() {
        for (long start : new long[]{0, 666, SeedCodec.TOTAL_SEEDS - 1}) for (int count = 1; count <= 4; count++) {
            var checkpoint = SearchPartitions.create(start, count);
            long length = 0;
            for (int i = 0; i < count; i++) {
                var lane = checkpoint.lanes().get(i);
                assertEquals((start + length) % SeedCodec.TOTAL_SEEDS, lane.start());
                length += lane.length();
                assertEquals(lane.start(), lane.nextSeed());
            }
            assertEquals(SeedCodec.TOTAL_SEEDS, length);
            assertEquals(0, checkpoint.scanned());
        }
    }
    @Test public void laneResumeAndDeferredMatchesNeverSkipTheCandidate() {
        var lane = SearchPartitions.create(SeedCodec.TOTAL_SEEDS - 1, 2).lanes().get(0);
        var advanced = lane.advance();
        assertEquals(0, advanced.nextSeed());
        assertEquals(lane, advanced.retryLast());
        var finished = new SearchPartitions.Lane(lane.start(), lane.length(), lane.length());
        assertTrue(finished.exhausted());
    }
    @Test(expected = IllegalArgumentException.class) public void overlappingCheckpointIsRejected() {
        var lane = SearchPartitions.create(666, 2).lanes().get(0);
        new SearchPartitions.Checkpoint(java.util.List.of(lane, lane));
    }
}
