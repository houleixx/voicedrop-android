package com.baixingai.voicedrop;

import com.baixingai.voicedrop.update.GitHubRelease;

import org.junit.Test;

import static org.junit.Assert.*;

public class GitHubReleaseTest {
    @Test
    public void parsesLatestReleaseAndSelectsApkAsset() throws Exception {
        String json = "{"
                + "\"tag_name\":\"v0.1.1\","
                + "\"html_url\":\"https://github.com/houleixx/voicedrop-android/releases/tag/v0.1.1\","
                + "\"body\":\"Release v0.1.1\","
                + "\"prerelease\":false,"
                + "\"draft\":false,"
                + "\"assets\":["
                + "{\"name\":\"source.zip\",\"browser_download_url\":\"https://example.com/source.zip\",\"size\":10},"
                + "{\"name\":\"voicedrop-0.1.1.apk\",\"browser_download_url\":\"https://example.com/app.apk\",\"size\":123456}"
                + "]"
                + "}";

        GitHubRelease release = GitHubRelease.parse(json);

        assertEquals("v0.1.1", release.tagName);
        assertEquals("https://example.com/app.apk", release.apkDownloadUrl);
        assertEquals("voicedrop-0.1.1.apk", release.apkName);
        assertEquals(123456, release.apkSize);
        assertFalse(release.prerelease);
        assertFalse(release.draft);
    }

    @Test
    public void selectsApkAssetWhenGitHubAssetContainsNestedUploader() throws Exception {
        String json = "{"
                + "\"tag_name\":\"v0.1.2\","
                + "\"assets\":[{"
                + "\"name\":\"voicedrop-0.1.2.apk\","
                + "\"uploader\":{\"login\":\"github-actions[bot]\"},"
                + "\"size\":654321,"
                + "\"browser_download_url\":\"https://github.com/example.apk\""
                + "}]"
                + "}";

        GitHubRelease release = GitHubRelease.parse(json);

        assertEquals("voicedrop-0.1.2.apk", release.apkName);
        assertEquals("https://github.com/example.apk", release.apkDownloadUrl);
        assertEquals(654321, release.apkSize);
    }
}
