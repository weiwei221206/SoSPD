package io.github.sospd.engine;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

public class ChineseResourcesTest {
    private Properties read(Path file) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { properties.load(reader); }
        return properties;
    }
    @Test public void everyUpstreamKeyHasChineseTextAndNoEnglishFallbackBundle() throws Exception {
        Path reference = Path.of("../upstream-reference/4.0.2/en");
        try (var files = Files.walk(reference)) {
            for (Path english : files.filter(p -> p.toString().endsWith(".properties")).toList()) {
                Path relative = reference.relativize(english);
                Path base = Path.of("src/main/resources/messages").resolve(relative);
                Properties translated = read(base);
                Properties source = read(english);
                for (String key : source.stringPropertyNames()) {
                    assertTrue("Missing Chinese key: " + key, translated.containsKey(key));
                    if (!source.getProperty(key).isBlank()) assertFalse("Empty Chinese value: " + key, translated.getProperty(key).isBlank());
                }
                Path chinese = base.resolveSibling(base.getFileName().toString().replace(".properties", "_zh.properties"));
                assertEquals("Base bundle must contain only the same Chinese text", translated, read(chinese));
            }
        }
    }
    @Test public void guideAndStoryPagesAreExcluded() {
        DungeonEngine engine = new DungeonEngine(new File("build/test-runtime"));
        var report = engine.scout(new io.github.sospd.domain.Models.ScoutRequest("ABC-DEF-GHI", null, 24, 0), () -> false, f -> {});
        assertTrue(report.floors().stream().flatMap(f -> f.items().stream())
                .noneMatch(i -> i.id().startsWith("items.journal.")));
        assertTrue(engine.catalog().stream().noneMatch(i -> i.id().startsWith("items.journal.")));
        assertTrue(report.floors().stream().flatMap(f -> f.items().stream())
                .noneMatch(i -> i.source().equals("商店") && i.id().startsWith("items.bags.")));
    }
}
