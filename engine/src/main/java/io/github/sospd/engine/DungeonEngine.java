package io.github.sospd.engine;

import com.shatteredpixel.shatteredpixeldungeon.*;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.*;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.*;
import com.shatteredpixel.shatteredpixeldungeon.items.*;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.Artifact;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
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
import java.io.File;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/** All access to upstream global state is serialized, including catalog creation. */
public final class DungeonEngine {
    private static final Object LOCK = new Object();
    private static List<CatalogEntry> catalog;

    public DungeonEngine(File storage) { HeadlessRuntime.initialize(storage); }

    public DungeonReport scout(ScoutRequest request, BooleanSupplier cancelled, IntConsumer progress) {
        synchronized (LOCK) {
            checkCancelled(cancelled);
            initializeRun(request);
            List<Floor> floors = new ArrayList<>();
            // Match the upstream toolkit's pre-floor catalyst preview; never tie generation to display filters.
            List<FoundItem> trinkets = rollTrinkets();
            boolean ghostLogged = false, wandmakerLogged = false, impLogged = false;
            for (int depth = 1; depth <= request.floors(); depth++) {
                checkCancelled(cancelled);
                RegularLevel.roomList = null;
                Level level = Dungeon.newLevel();
                List<FoundItem> items = new ArrayList<>();
                if (depth == 1) items.addAll(trinkets);
                for (Heap heap : level.heaps.valueList()) {
                    for (Item item : heap.items) items.add(snapshot(item, heapLabel(heap.type)));
                }
                for (Mob mob : level.mobs) {
                    if (mob instanceof ArmoredStatue statue) {
                        items.add(snapshot(statue.armor, "石像"));
                        items.add(snapshot(statue.weapon, "石像"));
                    } else if (mob instanceof Statue statue) {
                        items.add(snapshot(statue.weapon, "石像"));
                    } else if (mob instanceof Mimic mimic) {
                        for (Item item : mimic.items) items.add(snapshot(item,
                                mimic instanceof CrystalMimic ? "水晶宝箱怪" : mimic instanceof GoldenMimic ? "金色宝箱怪" : "宝箱怪"));
                    }
                }
                if (level.sacrificialFireItem != null) items.add(snapshot(level.sacrificialFireItem, "献祭之火"));
                if (!ghostLogged && Ghost.Quest.armor != null) {
                    items.add(snapshot(Ghost.Quest.armor.inscribe(Ghost.Quest.glyph), "悲伤幽灵任务"));
                    items.add(snapshot(Ghost.Quest.weapon.enchant(Ghost.Quest.enchant), "悲伤幽灵任务"));
                    ghostLogged = true;
                }
                if (!wandmakerLogged && Wandmaker.Quest.wand1 != null) {
                    items.add(snapshot(Wandmaker.Quest.wand1, "老杖匠任务"));
                    items.add(snapshot(Wandmaker.Quest.wand2, "老杖匠任务"));
                    wandmakerLogged = true;
                }
                if (!impLogged && (Imp.Quest.reward != null || !Imp.Quest.rewardOptions.isEmpty())) {
                    if (Imp.Quest.reward != null) items.add(snapshot(Imp.Quest.reward, "野心勃勃的小恶魔任务"));
                    for (Item item : Imp.Quest.rewardOptions) items.add(snapshot(item, "小恶魔可选奖励"));
                    impLogged = true;
                }
                List<FoundRoom> rooms = new ArrayList<>();
                if (level instanceof RegularLevel && RegularLevel.roomList != null) {
                    for (Room room : RegularLevel.roomList) rooms.add(RoomLabels.snapshot(room));
                }
                items.sort(Comparator.comparing((FoundItem i) -> i.category().ordinal()).thenComparing(FoundItem::name));
                rooms.sort(Comparator.comparing(FoundRoom::name));
                floors.add(new Floor(depth, feeling(level, depth), List.copyOf(items), List.copyOf(rooms)));
                Dungeon.depth++;
                progress.accept(depth);
            }
            return new DungeonReport(request.input(), request.dailyDate() == null ? SeedCodec.toCode(Dungeon.seed) : "每日挑战",
                    Dungeon.seed, request.dailyDate(), request.challenges(), List.copyOf(floors));
        }
    }

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
                        String name = clean(item.name());
                        if (name.contains(Messages.NO_TEXT_FOUND)) throw new IllegalStateException("物品名称缺少中文翻译：" + id(cls));
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
        if (Dungeon.daily) SPDSettings.lastDaily(LocalDate.parse(request.dailyDate()).toEpochDay() * 86_400_000L);
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
        item.identify();
        String appearance = item instanceof Potion || item instanceof Scroll || item instanceof Ring
                ? clean(item.anonymousName()) : "";
        return new FoundItem(id(item.getClass()), clean(item.title()), category(item), item.level(), item.cursed,
                source, appearance, item.image);
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
