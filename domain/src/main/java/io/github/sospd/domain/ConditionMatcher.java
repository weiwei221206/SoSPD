package io.github.sospd.domain;

import io.github.sospd.domain.Models.*;

public final class ConditionMatcher {
    private ConditionMatcher() {}
    public static boolean matches(DungeonReport report, SearchRequest request) {
        return request.matchAll()
                ? request.conditions().stream().allMatch(c -> contains(report, c, request.excludeImpQuest()))
                : request.conditions().stream().anyMatch(c -> contains(report, c, request.excludeImpQuest()));
    }
    private static boolean contains(DungeonReport report, Condition condition, boolean excludeImpQuest) {
        for (Floor floor : report.floors()) {
            if (condition.category() == Category.ROOM) {
                if (floor.rooms().stream().anyMatch(r -> r.id().equals(condition.id()))) return true;
            } else {
                if (floor.items().stream().anyMatch(i -> i.reliable() && !(excludeImpQuest && i.impQuestReward()) && i.id().equals(condition.id())
                        && (condition.minimumLevel() == null || i.level() >= condition.minimumLevel()))) return true;
            }
        }
        return false;
    }
}
