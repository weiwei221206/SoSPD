package io.github.sospd.engine;

import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.WaterOfAwareness;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.WaterOfHealth;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.Room;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.secret.SecretWellRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.MagicWellRoom;
import io.github.sospd.domain.Models.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class RoomLabels {
    private static final Properties LABELS = new Properties();
    static {
        try (InputStream stream = RoomLabels.class.getResourceAsStream("/sospd/rooms_zh.properties")) {
            if (stream == null) throw new IllegalStateException("缺少房间中文资源");
            LABELS.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException e) { throw new ExceptionInInitializerError(e); }
    }

    static FoundRoom snapshot(Room room) {
        String id = DungeonEngine.id(room.getClass());
        String name = LABELS.getProperty(id, "特殊房间");
        if (room instanceof MagicWellRoom || room instanceof SecretWellRoom) {
            if (room.generatedWellWater == WaterOfHealth.class) name += " · 生命之泉";
            else if (room.generatedWellWater == WaterOfAwareness.class) name += " · 觉察之泉";
        }
        String type = id.contains(".secret.") ? "隐藏" : id.contains(".special.") ? "特殊"
                : id.contains(".quest.") ? "任务" : id.contains(".entrance.") ? "入口"
                : id.contains(".exit.") ? "出口" : "普通";
        return new FoundRoom(id, name, type);
    }

    static Map<String, CatalogEntry> catalog() {
        Map<String, CatalogEntry> result = new TreeMap<>();
        for (String id : LABELS.stringPropertyNames()) {
            result.put(id, new CatalogEntry(id, LABELS.getProperty(id), "可作为找种条件；仅搜索指定深度内生成的房间。",
                    Category.ROOM, false, -1));
        }
        return result;
    }
}

