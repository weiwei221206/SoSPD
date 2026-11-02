package io.github.sospd.engine;

import com.shatteredpixel.shatteredpixeldungeon.*;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.utils.DungeonSeed;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/** The oracle initializes and advances the official game directly; it does not invoke DungeonEngine. */
public final class OfficialProbe {
    public static void main(String[] args) throws Exception {
        File output = new File(args[0]); output.getParentFile().mkdirs();
        System.setProperty("sospd.capture", new File(output.getParentFile(), "official-first.txt").getPath());
        HeadlessRuntime.initialize(new File(output.getParentFile(), "official-runtime"));
        try (PrintWriter writer = new PrintWriter(output, StandardCharsets.UTF_8)) {
            int count = 0;
            for (var request : VerificationMatrix.cases()) {
                SPDSettings.challenges(request.challenges());
                Dungeon.daily = request.date() != null; Dungeon.dailyReplay = false;
                if (Dungeon.daily) SPDSettings.lastDaily(LocalDate.parse(request.date()).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
                else SPDSettings.customSeed(request.input());
                Dungeon.initSeed();
                GamesInProgress.selectedClass = HeroClass.WARRIOR;
                Dungeon.init();
                for (int depth = 1; depth <= 24; depth++) {
                    Level level = Dungeon.newLevel();
                    writer.println(Fingerprint.capture(request.key(), level));
                    Dungeon.depth++;
                }
                if (++count % 64 == 0) System.out.println("Official reference: " + count + " cases completed");
            }
        }
    }
}
