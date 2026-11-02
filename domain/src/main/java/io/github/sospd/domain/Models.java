package io.github.sospd.domain;

import java.util.List;

public final class Models {
    private Models() {}

    public enum Category {
        EQUIPMENT("装备"), RING("戒指"), WAND("法杖"), ARTIFACT("神器"),
        POTION("药水"), SCROLL("卷轴"), TRINKET("饰品"), OTHER("其他"), ROOM("房间");
        public final String title;
        Category(String title) { this.title = title; }
    }
    public record CatalogEntry(String id, String name, String description, Category category,
                               boolean upgradeable, int image) {}
    public record FoundItem(String id, String name, Category category, int level, boolean cursed,
                            String source, String appearance, int image, boolean reliable) {}
    public record FoundRoom(String id, String name, String type) {}
    public record Floor(int depth, String feeling, List<FoundItem> items, List<FoundRoom> rooms) {}
    public record DungeonReport(String input, String seedCode, long seed, String dailyDate,
                                int challenges, List<Floor> floors) {}
    public record Condition(String id, Category category, Integer minimumLevel) {}
    public record SearchDraft(List<Condition> conditions, boolean matchAll) {
        public SearchDraft {
            conditions = conditions == null ? List.of() : List.copyOf(conditions.stream()
                    .filter(c -> !ItemRules.excluded(c.id())).collect(java.util.stream.Collectors.toList()));
        }
    }
    public record ScoutRequest(String input, String dailyDate, int floors, int challenges, String dailyZone) {
        public ScoutRequest(String input, String dailyDate, int floors, int challenges) {
            this(input, dailyDate, floors, challenges, dailyDate == null ? null : java.time.ZoneId.systemDefault().getId());
        }
        public ScoutRequest {
            if (floors < 1 || floors > 24) throw new IllegalArgumentException("探查深度应为 1—24 层");
            if (challenges < 0 || challenges > 511) throw new IllegalArgumentException("挑战设置不正确");
            if (dailyDate != null) {
                if (dailyZone == null) dailyZone = java.time.ZoneId.systemDefault().getId();
                java.time.ZoneId.of(dailyZone);
            }
        }
    }
    public record SearchRequest(List<Condition> conditions, boolean matchAll, int floors, int challenges) {
        public SearchRequest {
            if (conditions == null || conditions.isEmpty()) throw new IllegalArgumentException("请先添加找种条件");
            if (conditions.stream().anyMatch(c -> ItemRules.excluded(c.id()))) throw new IllegalArgumentException("搜索条件包含已排除的物品，请重新选择");
            conditions = List.copyOf(conditions);
            if (floors < 1 || floors > 24) throw new IllegalArgumentException("搜索深度应为 1—24 层");
            if (challenges < 0 || challenges > 511) throw new IllegalArgumentException("挑战设置不正确");
        }
    }
}

