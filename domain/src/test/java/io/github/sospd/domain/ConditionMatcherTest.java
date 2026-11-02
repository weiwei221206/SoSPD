package io.github.sospd.domain;

import io.github.sospd.domain.Models.*;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class ConditionMatcherTest {
    private final DungeonReport report = new DungeonReport("0", "AAA-AAA-AAA", 0, null, 0, List.of(
            new Floor(1, "普通楼层", List.of(new FoundItem("ring", "神射之戒 +2", Category.RING, 2,
                    false, "地面", "翡翠戒指", 0, true)), List.of(new FoundRoom("garden", "花园房间", "特殊")))));
    @Test public void allAndAnyAndMinimumLevel() {
        Condition ring = new Condition("ring", Category.RING, 2);
        Condition missing = new Condition("missing", Category.WAND, null);
        assertTrue(ConditionMatcher.matches(report, new SearchRequest(List.of(ring, missing), false, 1, 0)));
        assertFalse(ConditionMatcher.matches(report, new SearchRequest(List.of(ring, missing), true, 1, 0)));
        assertFalse(ConditionMatcher.matches(report, new SearchRequest(List.of(new Condition("ring", Category.RING, 3)), true, 1, 0)));
    }
    @Test public void roomUsesStableId() {
        assertTrue(ConditionMatcher.matches(report, new SearchRequest(List.of(new Condition("garden", Category.ROOM, null)), true, 1, 0)));
    }
    @Test public void runtimeDependentLootCannotGuaranteeASearchMatch() {
        var uncertain = new DungeonReport("0", "AAA-AAA-AAA", 0, null, 0, List.of(
                new Floor(1, "普通楼层", List.of(new FoundItem("potion", "治疗药剂", Category.POTION, 0,
                        false, "隐藏实验室", "红色药剂", 0, false)), List.of())));
        assertFalse(ConditionMatcher.matches(uncertain,
                new SearchRequest(List.of(new Condition("potion", Category.POTION, null)), true, 1, 0)));
    }
    @Test(expected = IllegalArgumentException.class) public void emptyConditionsRejected() {
        new SearchRequest(List.of(), true, 1, 0);
    }
}

