package io.github.sospd.domain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Accept only explicitly formatted game codes, never arbitrary OCR text as a custom seed. */
public final class OcrSeedParser {
    private static final Pattern CODE = Pattern.compile("(?<![A-Z0-9])([A-Z]{3})\\s*-\\s*([A-Z]{3})\\s*-\\s*([A-Z]{3})(?![A-Z0-9])");
    private OcrSeedParser() {}

    public static List<String> candidates(String text) {
        if (text == null) return List.of();
        var normalized = text.toUpperCase(Locale.ROOT).replaceAll("[‐‑‒–—−﹣－]", "-");
        var matcher = CODE.matcher(normalized);
        var result = new LinkedHashSet<String>();
        while (matcher.find()) result.add(matcher.group(1) + "-" + matcher.group(2) + "-" + matcher.group(3));
        return List.copyOf(result);
    }
}
