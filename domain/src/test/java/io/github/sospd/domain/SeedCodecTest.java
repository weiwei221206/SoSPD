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
    @Test public void dailySeedsFollowTheDeviceTimeZone() {
        var date = LocalDate.of(1970, 1, 1);
        assertEquals(date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() + SeedCodec.TOTAL_SEEDS, SeedCodec.dailySeed(date));
        assertEquals(86400000, SeedCodec.dailySeed(LocalDate.of(2026, 11, 3))
                - SeedCodec.dailySeed(LocalDate.of(2026, 11, 2)));
    }
    @Test public void dailySeedsRespectExplicitZonesAndDaylightSaving() {
        var date = LocalDate.of(2026, 11, 3);
        assertNotEquals(SeedCodec.dailySeed(date, java.time.ZoneId.of("Asia/Shanghai")),
                SeedCodec.dailySeed(date, java.time.ZoneId.of("America/Los_Angeles")));
        var zone = java.time.ZoneId.of("America/Los_Angeles");
        assertEquals(23 * 3_600_000L, SeedCodec.dailySeed(LocalDate.of(2026, 3, 9), zone)
                - SeedCodec.dailySeed(LocalDate.of(2026, 3, 8), zone));
    }
    @Test(expected = IllegalArgumentException.class) public void emptySeedRejected() { SeedCodec.fromText("  "); }
    @Test(expected = IllegalArgumentException.class) public void negativeSeedRejected() { SeedCodec.fromText("-123"); }
}

