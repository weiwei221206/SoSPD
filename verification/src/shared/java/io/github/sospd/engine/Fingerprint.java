package io.github.sospd.engine;

import com.shatteredpixel.shatteredpixeldungeon.*;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Blob;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.SacrificialFire;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.*;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.*;
import com.shatteredpixel.shatteredpixeldungeon.items.*;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.journal.DocumentPage;
import com.shatteredpixel.shatteredpixeldungeon.items.journal.Guidebook;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.Bag;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.Ring;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon;
import com.shatteredpixel.shatteredpixeldungeon.levels.*;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.Room;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Read-only canonical capture, compiled separately against each project's own game classes. */
final class Fingerprint {
    static String capture(String key, Level level) {
        try {
            List<String> items = new ArrayList<>(), mobs = new ArrayList<>(), rooms = new ArrayList<>(), quests = new ArrayList<>(), runtimeLoot = new ArrayList<>();
            List<Room> generatedRooms = new ArrayList<>();
            if (level instanceof RegularLevel) {
                var field = RegularLevel.class.getDeclaredField("rooms"); field.setAccessible(true);
                generatedRooms.addAll((ArrayList<Room>) field.get(level));
            }
            for (Heap heap : level.heaps.valueList()) {
                for (Item item : heap.items) {
                    if (item instanceof DocumentPage || item instanceof Guidebook) continue;
                    if (heap.type == Heap.Type.FOR_SALE && item instanceof Bag) continue;
                    boolean variable = false;
                    int x = heap.pos % level.width(), y = heap.pos / level.width();
                    for (Room room : generatedRooms) {
                        if (x <= room.left || x >= room.right || y <= room.top || y >= room.bottom) continue;
                        variable |= (item instanceof Potion && room.getClass().getSimpleName().equals("SecretLaboratoryRoom"))
                                || (item instanceof Scroll && room.getClass().getSimpleName().equals("SecretLibraryRoom"));
                    }
                    (variable ? runtimeLoot : items).add(heap.pos + ":" + heap.type + ":" + item(item));
                }
            }
            List<String> blobs = new ArrayList<>();
            for (Blob blob : level.blobs.values()) {
                blobs.add(blob.getClass().getName() + ":" + Arrays.toString(blob.cur));
                if (blob instanceof SacrificialFire) items.add("sacrifice:" + item((Item) readField(blob, "prize")));
            }
            for (Mob mob : level.mobs) {
                List<String> buffs = new ArrayList<>();
                for (Buff buff : mob.buffs()) buffs.add(buff.getClass().getName());
                Collections.sort(buffs);
                mobs.add(mob.getClass().getName() + ":" + mob.pos + ":" + mob.HP + ":" + mob.HT + ":" + buffs);
                if (mob instanceof Statue statue) items.add("statue:" + mob.pos + ":" + item((Item) readField(statue, "weapon")));
                if (mob instanceof ArmoredStatue statue) items.add("statue-armor:" + mob.pos + ":" + item((Item) readField(statue, "armor")));
                if (mob instanceof Mimic mimic) for (Item item : mimic.items) items.add("mimic:" + mob.pos + ":" + item(item));
            }
            if (level instanceof RegularLevel) {
                var field = RegularLevel.class.getDeclaredField("rooms"); field.setAccessible(true);
                for (Room room : (ArrayList<Room>) field.get(level)) {
                    rooms.add(room.getClass().getName() + ":" + room.left + ":" + room.top + ":" + room.right + ":" + room.bottom);
                }
            }
            quests.add("ghost-armor:" + item(Ghost.Quest.armor));
            quests.add("ghost-weapon:" + item(Ghost.Quest.weapon));
            quests.add("ghost-glyph:" + cls(Ghost.Quest.glyph));
            quests.add("ghost-enchant:" + cls(Ghost.Quest.enchant));
            quests.add("wand1:" + item(Wandmaker.Quest.wand1));
            quests.add("wand2:" + item(Wandmaker.Quest.wand2));
            quests.add("imp:" + item(Imp.Quest.reward));
            for (Item option : Imp.Quest.rewardOptions) quests.add("imp-option:" + item(option));
            List<String> labels = new ArrayList<>();
            for (Generator.Category category : new Generator.Category[]{Generator.Category.POTION, Generator.Category.SCROLL, Generator.Category.RING}) {
                for (Class<?> type : category.classes) {
                    Item item = (Item) type.getDeclaredConstructor().newInstance();
                    labels.add(type.getName() + ":" + item.image);
                }
            }
            Collections.sort(items); Collections.sort(mobs); Collections.sort(rooms); Collections.sort(quests); Collections.sort(labels); Collections.sort(blobs); Collections.sort(runtimeLoot);
            String terrain = level.getClass().getName() + ":" + level.width() + ":" + level.height() + ":" + level.feeling + ":" + Arrays.toString(level.map) + ":" + blobs;
            if (key.equals("ABC-DEF-GHI/0")) {
                String file = System.getProperty("sospd.capture");
                if (file != null) java.nio.file.Files.writeString(java.nio.file.Path.of(file + (Dungeon.depth == 1 ? "" : "." + Dungeon.depth)),
                        "seed=" + Dungeon.seed + "\nterrain=" + terrain + "\nitems=" + items + "\nmobs=" + mobs + "\nrooms=" + rooms + "\nquests=" + quests + "\nlabels=" + labels);
            }
            return key + "\t" + Dungeon.depth + "\t" + hash(terrain) + "\t" + hash(items.toString()) + "\t" + hash(mobs.toString())
                    + "\t" + hash(rooms.toString()) + "\t" + hash(quests.toString()) + "\t" + hash(labels.toString()) + "\t" + hash(runtimeLoot.toString());
        } catch (ReflectiveOperationException | java.io.IOException e) { throw new IllegalStateException(e); }
    }

    private static String item(Item item) {
        if (item == null) return "null";
        return item.getClass().getName() + ":" + item.quantity() + ":" + item.level() + ":" + item.cursed
                + ":" + (item instanceof Weapon weapon ? cls(weapon.enchantment) : "")
                + ":" + (item instanceof Armor armor ? cls(armor.glyph) : "");
    }
    private static String cls(Object object) { return object == null ? "null" : object.getClass().getName(); }
    private static Object readField(Object instance, String name) throws ReflectiveOperationException {
        Class<?> type = instance.getClass();
        while (type != null) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(instance); }
            catch (NoSuchFieldException e) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }
    private static String hash(String text) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
