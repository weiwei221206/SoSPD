package io.github.sospd.engine;

import com.shatteredpixel.shatteredpixeldungeon.utils.DungeonSeed;
import io.github.sospd.domain.Models.*;
import io.github.sospd.domain.SeedCodec;
import org.junit.Test;
import java.io.File;
import java.util.concurrent.CancellationException;
import static org.junit.Assert.*;

public class DungeonEngineTest {
    private DungeonEngine engine() { return new DungeonEngine(new File("build/test-runtime")); }
    @Test public void generatesWithoutGraphicsAndIsIndependentOfPreviousRun() {
        DungeonEngine engine = engine();
        ScoutRequest request = new ScoutRequest("ABC-DEF-GHI", null, 4, 0);
        DungeonReport first = engine.scout(request, () -> false, f -> {});
        engine.scout(new ScoutRequest("ZZZ-ZZZ-ZZZ", null, 3, 511), () -> false, f -> {});
        engine.catalog();
        DungeonReport second = engine.scout(request, () -> false, f -> {});
        assertEquals(first, second);
        assertEquals(4, first.floors().size());
        assertFalse(first.floors().get(0).items().isEmpty());
        assertFalse(first.floors().get(0).rooms().isEmpty());
        assertNull(com.badlogic.gdx.Gdx.gl);
    }
    @Test public void dailyAndFullDepthRun() {
        DungeonReport report = engine().scout(new ScoutRequest("", "2026-11-02", 24, 0), () -> false, f -> {});
        assertEquals(SeedCodec.dailySeed(java.time.LocalDate.of(2026, 11, 2)), report.seed());
        assertEquals(24, report.floors().size());
        assertEquals(24, report.floors().get(23).depth());
    }
    @Test public void codecMatchesPinnedUpstreamForNonemptyPositiveInputs() {
        for (String text : new String[]{"ABC-DEF-GHI", "abc-def-ghi", "ABCDEFGHI", "abcdefghi", "42", "破碎地牢", "SoSPD"}) {
            assertEquals(DungeonSeed.convertFromText(text), SeedCodec.fromText(text));
        }
    }
    @Test(expected = CancellationException.class) public void cancelledBeforeGeneration() {
        engine().scout(new ScoutRequest("0", null, 4, 0), () -> true, f -> {});
    }
    @Test public void catalogContainsChineseItemsAndRooms() {
        var catalog = engine().catalog();
        assertTrue(catalog.size() > 150);
        assertTrue(catalog.stream().anyMatch(i -> i.name().contains("神射")));
        assertTrue(catalog.stream().anyMatch(i -> i.category() == Category.ROOM));
        assertTrue(catalog.stream().noneMatch(i -> i.name().contains("NO TEXT")));
    }
}
