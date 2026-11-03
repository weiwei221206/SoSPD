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
    @Test public void dailyAndCustomRunsDoNotShareImpRewards() {
        var engine = engine();
        com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Imp.Quest.rewardOptions.clear();
        var request = new ScoutRequest("666", null, 24, 0);
        var expected = engine.scout(request, () -> false, f -> {});
        var dailyRequest = new ScoutRequest("", "2026-11-03", 24, 0);
        var daily = engine.scout(dailyRequest, () -> false, f -> {});
        assertEquals(expected, engine.scout(request, () -> false, f -> {}));
        assertEquals(daily, engine.scout(dailyRequest, () -> false, f -> {}));
    }
    @Test public void impRewardsBelongToTheQuestFloorAndNeverLeakIntoShortRuns() {
        var engine = engine();
        var questFloors = new java.util.ArrayList<Integer>();
        var report = engine.scout(new ScoutRequest("666", null, 24, 0), () -> false, f -> {}, level -> {
            if (level.mobs.stream().anyMatch(mob -> mob instanceof com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Imp))
                questFloors.add(com.shatteredpixel.shatteredpixeldungeon.Dungeon.depth);
        });
        assertEquals(1, questFloors.size());
        var rewards = report.floors().stream().filter(f -> f.items().stream()
                .anyMatch(i -> i.source().contains("小恶魔"))).toList();
        assertEquals(questFloors, rewards.stream().map(Floor::depth).toList());
        assertEquals(6, rewards.get(0).items().stream().filter(FoundItem::impQuestReward).count());
        var shortRun = engine.scout(new ScoutRequest("666", null, 4, 0), () -> false, f -> {});
        assertTrue(shortRun.floors().stream().flatMap(f -> f.items().stream()).noneMatch(i -> i.source().contains("小恶魔")));
    }
    @Test public void energyCrystalsAreGeneratedNormallyButExcludedFromReportsAndCatalog() {
        var seenCrystals = new java.util.concurrent.atomic.AtomicInteger();
        var report = engine().scout(new ScoutRequest("666", null, 24, 0), () -> false, f -> {}, level -> {
            for (var heap : level.heaps.valueList()) for (var item : heap.items)
                if (item instanceof com.shatteredpixel.shatteredpixeldungeon.items.EnergyCrystal) seenCrystals.incrementAndGet();
        });
        assertTrue(seenCrystals.get() > 0);
        assertFalse(report.floors().stream().flatMap(f -> f.items().stream()).anyMatch(i -> i.id().equals("items.EnergyCrystal")));
        assertFalse(engine().catalog().stream().anyMatch(i -> i.id().equals("items.EnergyCrystal")));
    }
    @Test public void reportedSeed666WorksPastFirstBossAndAtFullDepth() {
        for (int depth : new int[]{4, 5, 6, 24}) {
            assertEquals(depth, engine().scout(new ScoutRequest("666", null, depth, 0), () -> false, f -> {}).floors().size());
        }
    }
    @Test public void nullPrizeDoesNotCreateAGameSprite() {
        engine();
        var level = new com.shatteredpixel.shatteredpixeldungeon.levels.SewerLevel();
        assertNull(level.drop(null, 0).sprite);
        assertNull(com.badlogic.gdx.Gdx.gl);
    }
    @Test public void keysAndGoldAreExcludedButSkeletonKeyRemains() {
        var catalog = engine().catalog();
        assertTrue(catalog.stream().anyMatch(i -> i.id().equals("items.artifacts.SkeletonKey")));
        assertFalse(catalog.stream().anyMatch(i -> i.id().equals("items.Gold") || i.id().startsWith("items.keys.")));
        var report = engine().scout(new ScoutRequest("666", null, 24, 0), () -> false, f -> {});
        assertFalse(report.floors().stream().flatMap(f -> f.items().stream())
                .anyMatch(i -> i.id().equals("items.Gold") || i.id().startsWith("items.keys.")));
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
