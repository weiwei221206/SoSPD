package io.github.sospd.domain;

import org.junit.Test;
import java.time.LocalDate;
import static org.junit.Assert.*;

public class SeedCodecTest {
    @Test public void codeBoundariesAndRoundTrip() {
        assertEquals(0, SeedCodec.fromCode("AAA-AAA-AAA"));
        assertEquals(SeedCodec.TOTAL_SEEDS - 1, SeedCodec.fromCode("ZZZ-ZZZ-ZZZ"));
        for (long seed : new long[]{0, 1, 25, 26, 123456789, SeedCodec.TOTAL_SEEDS - 1}) {
            assertEquals(seed, SeedCodec.fromCode(SeedCodec.toCode(seed)));
        }
        assertEquals(0, SeedCodec.next(SeedCodec.TOTAL_SEEDS - 1));
    }
    @Test public void textBehaviorPreservesGameSemantics() {
        assertEquals(SeedCodec.fromCode("ABC-DEF-GHI"), SeedCodec.fromText("abc-def-ghi"));
        assertEquals(123, SeedCodec.fromText("1 2 3"));
        assertEquals(96354, SeedCodec.fromText("abc"));
        assertNotEquals(SeedCodec.fromText("abcdefghi"), SeedCodec.fromText("ABCDEFGHI"));
    }
    @Test public void dailySeedsUseUtcDayAndDoNotOverlapOrdinarySeeds() {
        assertEquals(SeedCodec.TOTAL_SEEDS, SeedCodec.dailySeed(LocalDate.of(1970, 1, 1)));
        assertEquals(86400000, SeedCodec.dailySeed(LocalDate.of(2026, 11, 3))
                - SeedCodec.dailySeed(LocalDate.of(2026, 11, 2)));
    }
    @Test(expected = IllegalArgumentException.class) public void emptySeedRejected() { SeedCodec.fromText("  "); }
    @Test(expected = IllegalArgumentException.class) public void negativeSeedRejected() { SeedCodec.fromText("-123"); }
}

