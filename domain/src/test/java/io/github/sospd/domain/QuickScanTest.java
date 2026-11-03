package io.github.sospd.domain;

import io.github.sospd.domain.Models.ScanRegion;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class QuickScanTest {
    @Test public void onlyFormattedStandardCodesAreAccepted() {
        assertEquals(List.of("ABC-DEF-GHI"), OcrSeedParser.candidates("种子：abc–def－ghi\nABC-DEF-GHI"));
        assertEquals(List.of("ABC-DEF-GHI", "ZZZ-ZZZ-ZZZ"), OcrSeedParser.candidates("ABC - DEF - GHI\nZZZ-ZZZ-ZZZ"));
        for (String input : new String[]{"666", "ABCDEFGHI", "破碎地牢", "AB1-DEF-GHI", "ABCD-DEF-GHI", "ABC-DEF-GHIJ"})
            assertTrue(input, OcrSeedParser.candidates(input).isEmpty());
    }
    @Test public void regionScalesForTheSameAspectRatioButRejectsRotation() {
        var region = ScanRegion.fromPixels(100, 200, 700, 350, 1080, 2400);
        assertEquals(100d / 1080, region.left(), 0.0001);
        assertTrue(region.matchesDisplay(720, 1600));
        assertFalse(region.matchesDisplay(2400, 1080));
        assertFalse(region.matchesDisplay(1080, 1920));
    }
    @Test(expected = IllegalArgumentException.class) public void tinyRegionIsRejected() {
        ScanRegion.fromPixels(10, 20, 12, 25, 1080, 2400);
    }
    @Test(expected = IllegalArgumentException.class) public void corruptRegionIsRejected() {
        new ScanRegion(Double.NaN, 0, 1, 1, 1080, 2400);
    }
}
