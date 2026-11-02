package io.github.sospd.engine;

import java.util.*;

final class VerificationMatrix {
    record Case(String input, String date, int challenges) {
        String key() { return (date == null ? input : "daily:" + date) + "/" + challenges; }
    }
    static List<Case> cases() {
        List<Case> result = new ArrayList<>();
        for (int mask = 0; mask <= 511; mask++) result.add(new Case("ABC-DEF-GHI", null, mask));
        for (String seed : new String[]{"AAA-AAA-AAA", "ZZZ-ZZZ-ZZZ"}) {
            for (int mask : new int[]{0, 1, 2, 4, 8, 16, 32, 64, 128, 256, 511, 96, 160, 320, 448}) result.add(new Case(seed, null, mask));
        }
        for (String date : new String[]{"2026-11-02", "2026-11-03"}) {
            for (int mask : new int[]{0, 64, 511}) result.add(new Case("", date, mask));
        }
        return List.copyOf(result);
    }
}
