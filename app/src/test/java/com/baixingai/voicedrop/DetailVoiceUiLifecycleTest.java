package com.baixingai.voicedrop;

import org.junit.Test;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.Assert.assertEquals;

/** Exercises actual detail lifecycle methods with lightweight view doubles.
 * No microphone, network request or production article mutation is involved. */
public class DetailVoiceUiLifecycleTest {
    @Test public void cancelThenSpeakAgainUpdatesTheVisibleTranscriptBubble() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get("src/main/java/com/baixingai/voicedrop/RecordingDetailActivity.java")), java.nio.charset.StandardCharsets.UTF_8);
        String reset = body(source, "protected void resetHoldArticleEditButton()");
        String update = body(source, "protected void updateHoldArticleEditTranscriptBubble()");
        String harness = "public class VoiceUiHarness {\n"
                + "static class View { static int GONE=8,VISIBLE=0; int visibility; String text; void setColorFilter(int c){} void setAlpha(float a){} void setVisibility(int v){visibility=v;} void setText(String t){text=t;} }\n"
                + "static class Theme { static int INK=0; }\n"
                + "static class Transcript { String value=\"first\"; String bestText(){return value;} String bubbleText(){return value;} }\n"
                + "View holdEditButton=new View(),holdEditMicIcon=new View(),holdEditTranscriptBubble=new View(),holdEditTranscriptText=new View();\n"
                + "String holdEditPromptText; boolean holdEditCanceled,holdEditFinishing; java.util.List<String> editQueue=new java.util.ArrayList<>(); Transcript holdEditTranscript=new Transcript();\n"
                + "void updateHoldArticleEditButton(View v,String t,int b,int c){} void setArticleLocatorsVisible(boolean b){} void applyDeferredArticleRenderIfIdle(){} String highlightHoldArticleEditTranscript(String s){return s;}\n"
                + "void resetHoldArticleEditButton()" + reset
                + "void updateHoldArticleEditTranscriptBubble()" + update
                + "public static void verify(){ VoiceUiHarness h=new VoiceUiHarness(); View bubble=h.holdEditTranscriptBubble, text=h.holdEditTranscriptText; h.updateHoldArticleEditTranscriptBubble(); h.resetHoldArticleEditButton(); h.holdEditButton=new View(); h.holdEditTranscript.value=\"second command\"; h.updateHoldArticleEditTranscriptBubble(); if(bubble.visibility!=View.VISIBLE || !\"second command\".equals(text.text)) throw new AssertionError(\"Second utterance must update the same visible bubble after cancellation\"); }\n}";
        Path temp = Files.createTempDirectory("voicedrop-voice-ui-");
        Path javaFile = temp.resolve("VoiceUiHarness.java");
        Files.write(javaFile, harness.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(0, new ProcessBuilder(System.getProperty("java.home") + "/bin/javac", "-d", temp.toString(), javaFile.toString()).inheritIO().start().waitFor());
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{temp.toUri().toURL()})) {
            loader.loadClass("VoiceUiHarness").getMethod("verify").invoke(null);
        } finally {
            try (java.util.stream.Stream<Path> paths=Files.walk(temp)) {
                for (Path p: (Iterable<Path>)paths.sorted(java.util.Comparator.reverseOrder())::iterator) Files.deleteIfExists(p);
            }
        }
    }
    private static String body(String source,String signature) {
        int start=source.indexOf('{',source.indexOf(signature)), depth=0;
        for(int i=start;i<source.length();i++) {
            if(source.charAt(i)=='{') depth++;
            if(source.charAt(i)=='}' && --depth==0) return source.substring(start,i+1);
        }
        throw new AssertionError(signature);
    }
}
