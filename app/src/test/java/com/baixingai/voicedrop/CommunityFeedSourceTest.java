package com.baixingai.voicedrop;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class CommunityFeedSourceTest {
    @Test
    public void communityFeedUsesNativeStaggeredGridAndAllTabs() throws Exception {
        String source = read("src/main/java/com/baixingai/voicedrop/ui/CommunityFeedView.java");
        assertTrue(source.contains("StaggeredGridLayoutManager"));
        assertTrue(source.contains("CommunityFeedPresentation.Tab.RECOMMENDED"));
        assertTrue(source.contains("CommunityFeedPresentation.Tab.LATEST"));
        assertTrue(source.contains("CommunityFeedPresentation.Tab.REPLIES"));
        assertTrue(source.contains("setOnLongClickListener"));
        assertTrue(source.contains("PhotoService.thumbnail"));
        assertTrue(source.contains("newFixedThreadPool(4)"));
        assertTrue(source.contains("card.setClipToOutline(true)"));
        assertFalse(source.contains("RoundedImageView image ="));
        assertFalse(source.contains("card.setElevation"));
    }

    @Test
    public void bothCommunityEntryPointsUseTheSharedFeedView() throws Exception {
        String recordings = read("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");
        String community = read("src/main/java/com/baixingai/voicedrop/CommunityActivity.java");
        assertTrue(recordings.contains("new CommunityFeedView"));
        assertTrue(community.contains("new CommunityFeedView"));
    }

    @Test
    public void pullRefreshKeepsFeedTabsFixedAndPlacesSpinnerBelowThem() throws Exception {
        String feed = read("src/main/java/com/baixingai/voicedrop/ui/CommunityFeedView.java");
        String refresh = read("src/main/java/com/baixingai/voicedrop/ui/PullRefreshLayout.java");
        String recordings = read("src/main/java/com/baixingai/voicedrop/RecordingsActivity.java");
        String community = read("src/main/java/com/baixingai/voicedrop/CommunityActivity.java");

        assertTrue(feed.contains("public View refreshTarget()"));
        assertTrue(feed.contains("public int refreshSpinnerOffset()"));
        assertTrue(refresh.contains("setRefreshTarget(View target, int spinnerTopOffsetPx)"));
        assertTrue(refresh.contains("scrollTarget.canScrollVertically(-1)"));
        assertTrue(refresh.contains("translationTarget.setTranslationY"));
        assertTrue(recordings.contains("refresher.setRefreshTarget(feedView.refreshTarget(), feedView.refreshSpinnerOffset())"));
        assertTrue(community.contains("refresher.setRefreshTarget(feedView.refreshTarget(), feedView.refreshSpinnerOffset())"));
    }

    @Test
    public void replyBadgeMatchesTheIosAccentCapsule() throws Exception {
        String source = read("src/main/java/com/baixingai/voicedrop/ui/CommunityFeedView.java");
        assertTrue(source.contains("private View replyBadge()"));
        assertTrue(source.contains("replyBadgeIcon.setImageResource(R.drawable.ic_community_reply)"));
        assertTrue(source.contains("replyBadgeIcon.setColorFilter(Theme.ACCENT)"));
        assertTrue(source.contains("badgeBackground.setColor(Theme.ACCENT_SOFT)"));
        assertTrue(source.contains("badgeBackground.setCornerRadius(dp(99))"));
        assertTrue(source.contains("badge.setPadding(dp(8), dp(2), dp(8), dp(2))"));
    }

    @Test
    public void heartUsesTheIosAccentVectorInsteadOfAColorEmoji() throws Exception {
        String source = read("src/main/java/com/baixingai/voicedrop/ui/CommunityFeedView.java");
        String vector = read("src/main/res/drawable/ic_community_heart.xml");
        assertTrue(source.contains("R.drawable.ic_community_heart"));
        assertFalse(source.contains("text(\"♥ "));
        assertTrue(vector.contains("#FFD8593B"));
    }

    @Test
    public void repliesUseTheProvidedTurnArrowSvgInsteadOfTextGlyphs() throws Exception {
        String source = read("src/main/java/com/baixingai/voicedrop/ui/CommunityFeedView.java");
        String vector = read("src/main/res/drawable/ic_community_reply.xml");
        assertTrue(source.contains("R.drawable.ic_community_reply"));
        assertFalse(source.contains("text(\"  ◌ "));
        assertFalse(source.contains("text(\"↶  回应"));
        assertTrue(vector.contains("#FF8A8175"));
        assertTrue(vector.contains("android:viewportWidth=\"1024\""));
        assertTrue(vector.contains("M434.18,290.746"));
        assertTrue(vector.contains("C624.66,636.09 727.6,676.66 774.481,715.72"));
        assertTrue(source.contains("if (replies > 0)"));
    }

    private String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), "UTF-8");
    }
}
