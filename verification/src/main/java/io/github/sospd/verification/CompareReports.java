package io.github.sospd.verification;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class CompareReports {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        List<String> expected = Files.readAllLines(root.resolve("official.tsv"), StandardCharsets.UTF_8);
        List<String> actual = Files.readAllLines(root.resolve("toolkit.tsv"), StandardCharsets.UTF_8);
        List<String> mismatches = new ArrayList<>();
        List<String> variableLoot = new ArrayList<>();
        String[] channels = {"case", "floor", "terrain", "items", "mobs", "rooms", "quests", "appearances"};
        if (expected.size() != actual.size()) mismatches.add("Row count differs: " + expected.size() + " / " + actual.size());
        for (int i = 0; i < Math.min(expected.size(), actual.size()); i++) {
            if (expected.get(i).equals(actual.get(i))) continue;
            String[] a = expected.get(i).split("\t"), b = actual.get(i).split("\t");
            List<String> changed = new ArrayList<>();
            for (int j = 0; j < channels.length; j++) if (!a[j].equals(b[j])) changed.add(channels[j]);
            if (!changed.isEmpty()) mismatches.add(a[0] + " floor " + a[1] + ": " + String.join(", ", changed));
            if (!a[8].equals(b[8])) variableLoot.add(a[0] + " floor " + a[1] + ": runtime/inventory-dependent secret consumables or shop bags");
        }
        Files.write(root.resolve("mismatches.txt"), mismatches, StandardCharsets.UTF_8);
        Files.write(root.resolve("runtime-dependent-loot.txt"), variableLoot, StandardCharsets.UTF_8);
        String summary = "Official v4.0.2 reference: " + expected.size() + " floors; " + (expected.size() / 24)
                + " cases; challenge masks 0..511; mismatched stable floors: " + mismatches.size()
                + "; runtime/inventory-dependent loot differences: " + variableLoot.size();
        Files.writeString(root.resolve("summary.txt"), summary + "\n", StandardCharsets.UTF_8);
        System.out.println(summary);
        if (!mismatches.isEmpty()) {
            mismatches.stream().limit(12).forEach(System.err::println);
            throw new IllegalStateException("Generation differs from the official game. See verification/build/reports/mismatches.txt");
        }
    }
}
