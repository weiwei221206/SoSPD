package io.github.sospd.domain;

/** Identity-based exclusions; artifact SkeletonKey belongs to items.artifacts, not items.keys. */
public final class ItemRules {
    private ItemRules() {}
    public static boolean excluded(String id) {
        return id.equals("items.Gold") || id.equals("items.EnergyCrystal") || id.startsWith("items.keys.")
                || id.startsWith("items.journal.") || id.startsWith("items.bags.");
    }
}
