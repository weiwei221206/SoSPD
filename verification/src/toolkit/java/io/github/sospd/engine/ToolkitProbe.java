package io.github.sospd.engine;

import io.github.sospd.domain.Models.ScoutRequest;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Captures the actual adapter's generated levels, including its extraction and pre-floor behavior. */
public final class ToolkitProbe {
    public static void main(String[] args) throws Exception {
        File output = new File(args[0]); output.getParentFile().mkdirs();
        System.setProperty("sospd.capture", new File(output.getParentFile(), "toolkit-first.txt").getPath());
        DungeonEngine engine = new DungeonEngine(new File(output.getParentFile(), "toolkit-runtime"));
        try (PrintWriter writer = new PrintWriter(output, StandardCharsets.UTF_8)) {
            int count = 0;
            for (var request : VerificationMatrix.cases()) {
                engine.scout(new ScoutRequest(request.input(), request.date(), 24, request.challenges()), () -> false,
                        floor -> {}, level -> writer.println(Fingerprint.capture(request.key(), level)));
                if (++count % 64 == 0) System.out.println("SoSPD adapter: " + count + " cases completed");
            }
        }
    }
}
