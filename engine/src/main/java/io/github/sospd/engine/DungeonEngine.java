package io.github.sospd.engine;

import com.shatteredpixel.shatteredpixeldungeon.*;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.*;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.*;
import com.shatteredpixel.shatteredpixeldungeon.items.*;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.Artifact;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.journal.DocumentPage;
import com.shatteredpixel.shatteredpixeldungeon.items.journal.Guidebook;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.Bag;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.Key;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.Ring;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.*;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon;
import com.shatteredpixel.shatteredpixeldungeon.levels.*;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.Room;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.watabou.utils.Random;
import io.github.sospd.domain.Models.*;
import io.github.sospd.domain.SeedCodec;
import io.github.sospd.domain.ItemRules;
import java.io.File;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/** All access to upstream global state is serialized, including catalog creation. */
public final class DungeonEngine {
    private static final Object LOCK = new Object();
    private static List<CatalogEntry> catalog;

    public DungeonEngine(File storage) { HeadlessRuntime.initialize(storage); }

    public DungeonReport scout(ScoutRequest request, BooleanSupplier cancelled, IntConsumer progress) {
        return scout(request, cancelled, progress, level -> {});
    }

    // Package-private observer for independent verification; the application receives only immutable DTOs.
    DungeonReport scout(ScoutRequest request, BooleanSupplier cancelled, IntConsumer progress,
                        java.util.function.Consumer<Level> observer) {
        synchronized (LOCK) {
            checkCancelled(cancelled);
            initializeRun(request);
            List<PendingFloor> generated = new ArrayList<>();
            boolean ghostLogged = false, wandmakerLogged = false, impLogged = false;
            for (int depth = 1; depth <= request.floors(); depth++) {
                checkCancelled(cancelled);
                RegularLevel.roomList = null;
                Level level = Dungeon.newLevel();
                observer.accept(level);
                List<Supplier<FoundItem>> items = new ArrayList<>();
                for (Heap heap : level.heaps.valueList()) {
                    for (Item item : heap.items) {
                        if (item instanceof DocumentPage || item instanceof Guidebook) continue;
                        if (item instanceof Gold || item instanceof Key) continue;
                        if (heap.type == Heap.Type.FOR_SALE && item instanceof Bag) continue;
                        boolean reliable = isReliableLoot(level, heap, item);
                        String source = reliable ? heapLabel(heap.type) : "隐藏房间 · 类型可能变化";
                        items.add(() -> snapshot(item, source, reliable));
                    }
                }
                for (Mob mob : level.mobs) {
                    if (mob instanceof ArmoredStatue statue) {
                        items.add(defer(statue.armor, "石像"));
                        items.add(defer(statue.weapon, "石像"));
                    } else if (mob instanceof Statue statue) {
                        items.add(defer(statue.weapon, "石像"));
                    } else if (mob instanceof Mimic mimic) {
                        for (Item item : mimic.items) items.add(defer(item,
                                mimic instanceof CrystalMimic ? "水晶宝箱怪" : mimic instanceof GoldenMimic ? "金色宝箱怪" : "宝箱怪"));
                    }
                }
                if (level.sacrificialFireItem != null) items.add(defer(level.sacrificialFireItem, "献祭之火"));
                if (!ghostLogged && Ghost.Quest.armor != null) {
                    Armor armor = Ghost.Quest.armor; Armor.Glyph glyph = Ghost.Quest.glyph;
                    Weapon weapon = Ghost.Quest.weapon; Weapon.Enchantment enchant = Ghost.Quest.enchant;
                    items.add(() -> snapshot(((Armor) armor.duplicate()).inscribe(glyph), "悲伤幽灵任务"));
                    items.add(() -> snapshot(((Weapon) weapon.duplicate()).enchant(enchant), "悲伤幽灵任务"));
                    ghostLogged = true;
                }
                if (!wandmakerLogged && Wandmaker.Quest.wand1 != null) {
                    items.add(defer(Wandmaker.Quest.wand1, "老杖匠任务"));
                    items.add(defer(Wandmaker.Quest.wand2, "老杖匠任务"));
                    wandmakerLogged = true;
                }
                if (!impLogged && (Imp.Quest.reward != null || !Imp.Quest.rewardOptions.isEmpty())) {
                    if (Imp.Quest.reward != null) items.add(defer(Imp.Quest.reward, "野心勃勃的小恶魔任务"));
                    for (Item item : Imp.Quest.rewardOptions) items.add(defer(item, "小恶魔可选奖励"));
                    impLogged = true;
                }
                List<FoundRoom> rooms = new ArrayList<>();
                if (level instanceof RegularLevel && RegularLevel.roomList != null) {
                    for (Room room : RegularLevel.roomList) rooms.add(RoomLabels.snapshot(room));
                }
                rooms.sort(Comparator.comparing(FoundRoom::name));
                generated.add(new PendingFloor(depth, feeling(level, depth), items, List.copyOf(rooms)));
                Dungeon.depth++;
                progress.accept(depth);
            }
            // Identification, descriptions and previews may change upstream state. Extract only after every floor is generated.
            List<Floor> floors = new ArrayList<>();
            for (PendingFloor floor : generated) {
                checkCancelled(cancelled);
                List<FoundItem> items = new ArrayList<>();
                for (Supplier<FoundItem> supplier : floor.items()) {
                    FoundItem item = supplier.get();
                    if (!ItemRules.excluded(item.id())) items.add(item);
                }
                items.sort(Comparator.comparing((FoundItem i) -> i.category().ordinal()).thenComparing(FoundItem::name));
                floors.add(new Floor(floor.depth(), floor.feeling(), List.copyOf(items), floor.rooms()));
            }
            // Preview catalysts in a fresh isolated run; they must not perturb actual floor generation.
            initializeRun(request);
            List<FoundItem> firstItems = new ArrayList<>(floors.get(0).items());
            firstItems.addAll(rollTrinkets());
            Floor first = floors.get(0);
            floors.set(0, new Floor(first.depth(), first.feeling(), List.copyOf(firstItems), first.rooms()));
            return new DungeonReport(request.input(), request.dailyDate() == null ? SeedCodec.toCode(Dungeon.seed) : "每日挑战",
                    Dungeon.seed, request.dailyDate(), request.challenges(), List.copyOf(floors));
        }
    }

    private record PendingFloor(int depth, String feeling, List<Supplier<FoundItem>> items, List<FoundRoom> rooms) {}
    private static Supplier<FoundItem> defer(Item item, String source) { return () -> snapshot(item, source); }

    public List<CatalogEntry> catalog() {
        synchronized (LOCK) {
            if (catalog != null) return catalog;
            initializeRun(new ScoutRequest("AAA-AAA-AAA", null, 1, 0));
            LinkedHashMap<String, CatalogEntry> entries = new LinkedHashMap<>();
            for (Generator.Category category : Generator.Category.values()) {
                for (Class<?> cls : category.classes) {
                    if (!Item.class.isAssignableFrom(cls) || Modifier.isAbstract(cls.getModifiers())) continue;
                    try {
                        Item item = ((Item) cls.getDeclaredConstructor().newInstance()).identify();
                        if (item instanceof DocumentPage || item instanceof Guidebook) continue;
                        if (item instanceof Bag) continue;
                        if (item instanceof Gold || item instanceof Key) continue;
                        String name = clean(item.name());
                        if (name.contains(Messages.NO_TEXT_FOUND)) throw new IllegalStateException("物品名称资源缺失：" + id(cls));
                        String description = clean(item.desc());
                        entries.putIfAbsent(id(item.getClass()), new CatalogEntry(id(item.getClass()), name,
                                description, category(item), item.isUpgradable(), item.image));
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException("无法创建物品图鉴", e);
                    }
                }
            }
            entries.putAll(RoomLabels.catalog());
            catalog = List.copyOf(entries.values());
            return catalog;
        }
    }

    private static void initializeRun(ScoutRequest request) {
        SPDSettings.challenges(request.challenges());
        Dungeon.daily = request.dailyDate() != null;
        Dungeon.dailyReplay = false;
        if (Dungeon.daily) SPDSettings.lastDaily(SeedCodec.dailySeed(LocalDate.parse(request.dailyDate()),
                java.time.ZoneId.of(request.dailyZone())) - SeedCodec.TOTAL_SEEDS);
        else SPDSettings.customSeed(SeedCodec.toCode(SeedCodec.fromText(request.input())));
        Dungeon.initSeed();
        GamesInProgress.selectedClass = HeroClass.WARRIOR;
        Dungeon.init();
    }

    private static List<FoundItem> rollTrinkets() {
        TrinketCatalyst catalyst = new TrinketCatalyst();
        while (catalyst.rolledTrinkets.size() < TrinketCatalyst.WndTrinket.NUM_TRINKETS) {
            catalyst.rolledTrinkets.add((Trinket) Generator.random(Generator.Category.TRINKET));
        }
        List<FoundItem> result = new ArrayList<>();
        for (Trinket trinket : catalyst.rolledTrinkets) result.add(snapshot(trinket, "饰品催化剂候选"));
        return result;
    }

    private static FoundItem snapshot(Item item, String source) {
        return snapshot(item, source, true);
    }

    private static FoundItem snapshot(Item item, String source, boolean reliable) {
        item.identify();
        String appearance = item instanceof Potion || item instanceof Scroll || item instanceof Ring
                ? clean(item.anonymousName()) : "";
        return new FoundItem(id(item.getClass()), clean(item.title()), category(item), item.level(), item.cursed,
                source, appearance, item.image, reliable);
    }

    private static boolean isReliableLoot(Level level, Heap heap, Item item) {
        if (!(level instanceof RegularLevel) || RegularLevel.roomList == null) return true;
        int x = heap.pos % level.width(), y = heap.pos / level.width();
        for (Room room : RegularLevel.roomList) {
            if (x <= room.left || x >= room.right || y <= room.top || y >= room.bottom) continue;
            if (item instanceof Potion && room.getClass().getSimpleName().equals("SecretLaboratoryRoom")) return false;
            if (item instanceof Scroll && room.getClass().getSimpleName().equals("SecretLibraryRoom")) return false;
        }
        return true;
    }

    static String id(Class<?> cls) { return cls.getName().replace("com.shatteredpixel.shatteredpixeldungeon.", ""); }
    static String clean(String value) { return value.replace("_", "").replace("**", ""); }

    static Category category(Item item) {
        if (item instanceof Ring) return Category.RING;
        if (item instanceof Wand) return Category.WAND;
        if (item instanceof Artifact) return Category.ARTIFACT;
        if (item instanceof Potion) return Category.POTION;
        if (item instanceof Scroll) return Category.SCROLL;
        if (item instanceof Trinket) return Category.TRINKET;
        if (item instanceof Weapon || item instanceof Armor) return Category.EQUIPMENT;
        return Category.OTHER;
    }

    private static String heapLabel(Heap.Type type) {
        return switch (type.name()) {
            case "FOR_SALE" -> "商店";
            case "CHEST" -> "宝箱";
            case "LOCKED_CHEST" -> "上锁宝箱";
            case "CRYSTAL_CHEST" -> "水晶宝箱";
            case "TOMB" -> "墓穴";
            case "SKELETON" -> "遗骸";
            case "REMAINS" -> "残骸";
            default -> "地面";
        };
    }

    private static String feeling(Level level, int depth) {
        if (depth == 5) return "粘咕";
        if (depth == 10) return "天狗";
        if (depth == 15) return "机械巨像";
        if (depth == 20) return "矮人国王";
        return switch (level.feeling.name()) {
            case "CHASM" -> "深渊遍布";
            case "WATER" -> "积水较多";
            case "GRASS" -> "草木繁茂";
            case "DARK" -> "黑暗笼罩";
            case "LARGE" -> "空间宽广";
            case "TRAPS" -> "陷阱密布";
            case "SECRETS" -> "隐藏秘密";
            default -> "普通楼层";
        };
    }

    private static void checkCancelled(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new CancellationException();
    }
}
