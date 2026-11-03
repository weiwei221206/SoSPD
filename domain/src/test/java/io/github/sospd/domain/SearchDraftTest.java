package io.github.sospd.domain;

import io.github.sospd.domain.Models.*;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class SearchDraftTest {
    @Test public void draftRetainsArtifactKeyAndRemovesOrdinaryKeysAndGold() {
        var artifact = new Condition("items.artifacts.SkeletonKey", Category.ARTIFACT, null);
        var draft = new SearchDraft(List.of(artifact,
                new Condition("items.keys.CrystalKey", Category.OTHER, null),
                new Condition("items.EnergyCrystal", Category.OTHER, null),
                new Condition("items.Gold", Category.OTHER, null)), false);
        assertEquals(List.of(artifact), draft.conditions());
        assertFalse(draft.matchAll());
    }
    @Test(expected = IllegalArgumentException.class) public void oldGoldSearchCannotBeResumedAsValidRequest() {
        new SearchRequest(List.of(new Condition("items.Gold", Category.OTHER, null)), true, 4, 0);
    }
    @Test(expected = IllegalArgumentException.class) public void energyCrystalSearchCannotBeResumedAsValidRequest() {
        new SearchRequest(List.of(new Condition("items.EnergyCrystal", Category.OTHER, null)), true, 4, 0);
    }
}
