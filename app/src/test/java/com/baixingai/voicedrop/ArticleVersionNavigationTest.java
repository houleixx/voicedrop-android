package com.baixingai.voicedrop;

import com.baixingai.voicedrop.ui.ArticleVersionNavigation;

import org.junit.Assert;
import org.junit.Test;

public class ArticleVersionNavigationTest {
    @Test public void middleVersionCanUndoAndRedo() {
        ArticleVersionNavigation nav = new ArticleVersionNavigation(1, 3, false);

        Assert.assertTrue(nav.canUndo());
        Assert.assertTrue(nav.canRedo());
    }

    @Test public void firstVersionCannotUndo() {
        ArticleVersionNavigation nav = new ArticleVersionNavigation(0, 3, false);

        Assert.assertFalse(nav.canUndo());
        Assert.assertTrue(nav.canRedo());
    }

    @Test public void latestVersionCannotRedo() {
        ArticleVersionNavigation nav = new ArticleVersionNavigation(2, 3, false);

        Assert.assertTrue(nav.canUndo());
        Assert.assertFalse(nav.canRedo());
    }

    @Test public void editingDisablesBothDirections() {
        ArticleVersionNavigation nav = new ArticleVersionNavigation(1, 3, true);

        Assert.assertFalse(nav.canUndo());
        Assert.assertFalse(nav.canRedo());
    }

    @Test public void navigatesUsingVersionIdsInsteadOfArrayIndexes() {
        ArticleVersionNavigation nav = new ArticleVersionNavigation(5, new int[] {2, 5, 9}, false);

        Assert.assertTrue(nav.canUndo());
        Assert.assertTrue(nav.canRedo());
        Assert.assertEquals(Integer.valueOf(2), nav.undoHead());
        Assert.assertEquals(Integer.valueOf(9), nav.redoHead());
    }
}
