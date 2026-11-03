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
                            String source, String appearance, int image, boolean reliable, boolean impQuestReward) {
        public FoundItem(String id, String name, Category category, int level, boolean cursed,
                         String source, String appearance, int image, boolean reliable) {
            this(id, name, category, level, cursed, source, appearance, image, reliable, false);
        }
    }
    public record FoundRoom(String id, String name, String type) {}
    public record Floor(int depth, String feeling, List<FoundItem> items, List<FoundRoom> rooms) {}
    public record DungeonReport(String input, String seedCode, long seed, String dailyDate,
                                int challenges, List<Floor> floors) {}
    public record Condition(String id, Category category, Integer minimumLevel) {}
    public record ScanRegion(double left, double top, double right, double bottom, int screenWidth, int screenHeight) {
        public ScanRegion {
            if (!Double.isFinite(left + top + right + bottom) || left < 0 || top < 0 || right > 1 || bottom > 1
                    || left >= right || top >= bottom || screenWidth <= 0 || screenHeight <= 0)
                throw new IllegalArgumentException("识别范围无效，请重新框选");
        }
        public boolean matchesDisplay(int width, int height) {
            return width > 0 && height > 0 && Math.abs((double) width / height / ((double) screenWidth / screenHeight) - 1) < 0.01;
        }
        public static ScanRegion fromPixels(int left, int top, int right, int bottom, int width, int height) {
            if (right - left < 16 || bottom - top < 16) throw new IllegalArgumentException("识别范围太小，请重新框选");
            return new ScanRegion((double) left / width, (double) top / height, (double) right / width,
                    (double) bottom / height, width, height);
        }
    }
    public record SearchDraft(List<Condition> conditions, boolean matchAll, boolean excludeImpQuest) {
        public SearchDraft(List<Condition> conditions, boolean matchAll) { this(conditions, matchAll, false); }
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
    public record SearchRequest(List<Condition> conditions, boolean matchAll, int floors, int challenges, boolean excludeImpQuest, int parallelism) {
        public SearchRequest(List<Condition> conditions, boolean matchAll, int floors, int challenges) {
            this(conditions, matchAll, floors, challenges, false, 1);
        }
        public SearchRequest(List<Condition> conditions, boolean matchAll, int floors, int challenges, boolean excludeImpQuest) {
            this(conditions, matchAll, floors, challenges, excludeImpQuest, 1);
        }
        public SearchRequest {
            if (conditions == null || conditions.isEmpty()) throw new IllegalArgumentException("请先添加找种条件");
            if (conditions.stream().anyMatch(c -> ItemRules.excluded(c.id()))) throw new IllegalArgumentException("搜索条件包含已排除的物品，请重新选择");
            conditions = List.copyOf(conditions);
            if (floors < 1 || floors > 24) throw new IllegalArgumentException("搜索深度应为 1—24 层");
            if (parallelism == 0) parallelism = 1; // Old saved requests remain sequential.
            if (parallelism < 1 || parallelism > 4) throw new IllegalArgumentException("并行搜索应为 1—4 路");
            if (challenges < 0 || challenges > 511) throw new IllegalArgumentException("挑战设置不正确");
        }
    }
}

