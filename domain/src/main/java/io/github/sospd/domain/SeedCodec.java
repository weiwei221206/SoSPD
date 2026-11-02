package io.github.sospd.domain;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;

/** Seed semantics follow Shattered Pixel Dungeon 4.0.2 DungeonSeed (GPL-3.0-or-later). */
public final class SeedCodec {
    public static final long TOTAL_SEEDS = 5_429_503_678_976L;
    public static final ZoneOffset DAILY_ZONE = ZoneOffset.ofHours(8);
    private SeedCodec() {}

    public static long fromCode(String code) {
        if (code.length() == 11 && code.charAt(3) == '-' && code.charAt(7) == '-') {
            code = code.toUpperCase(Locale.ROOT);
        }
        code = code.replaceAll("[-\\s]", "");
        if (code.length() != 9) throw new IllegalArgumentException("种子码应包含九个英文字母");
        long value = 0;
        for (char c : code.toCharArray()) {
            if (c < 'A' || c > 'Z') throw new IllegalArgumentException("种子码格式不正确");
            value = value * 26 + c - 'A';
        }
        return value;
    }

    public static String toCode(long seed) {
        if (seed < 0 || seed >= TOTAL_SEEDS) throw new IllegalArgumentException("种子数值超出范围");
        char[] code = new char[9];
        for (int i = 8; i >= 0; i--) {
            code[i] = (char) ('A' + seed % 26);
            seed /= 26;
        }
        String text = new String(code);
        return text.substring(0, 3) + "-" + text.substring(3, 6) + "-" + text.substring(6);
    }

    /** Preserve the game's distinction between formatted codes, numeric and arbitrary text seeds. */
    public static long fromText(String input) {
        if (input == null || input.isBlank()) throw new IllegalArgumentException("请输入种子");
        try { return fromCode(input); } catch (IllegalArgumentException ignored) { }
        try {
            long seed = Long.parseLong(input.replaceAll("\\s", "")) % TOTAL_SEEDS;
            if (seed < 0) throw new IllegalArgumentException("种子数值不能为负数");
            return seed;
        } catch (NumberFormatException ignored) { }
        long total = 0;
        for (char c : input.toCharArray()) total = 31 * total + c;
        if (total < 0) total += Long.MAX_VALUE;
        return total % TOTAL_SEEDS;
    }

    public static long dailySeed(LocalDate date) {
        return date.atStartOfDay(DAILY_ZONE).toInstant().toEpochMilli() + TOTAL_SEEDS;
    }

    public static long next(long seed) { return (seed + 1) % TOTAL_SEEDS; }
}

