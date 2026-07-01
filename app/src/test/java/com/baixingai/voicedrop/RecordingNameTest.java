package com.baixingai.voicedrop;

import com.baixingai.voicedrop.core.RecordingName;

import org.junit.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.Assert.*;

public class RecordingNameTest {
    @Test
    public void makeMatchesIosRichAsciiFilename() {
        ZonedDateTime start = ZonedDateTime.ofInstant(
                Instant.parse("2026-06-18T06:30:52Z"),
                ZoneId.of("Asia/Shanghai"));

        String name = RecordingName.make(start, 33.2, "Shanghai-Xuhui");

        assertEquals("VoiceDrop-2026-06-18-143052-0m33s-Thu-Afternoon-Shanghai-Xuhui.m4a", name);
    }

    @Test
    public void periodMappingMatchesIosBoundaries() {
        assertEquals("LateNight", RecordingName.period(hour(4)));
        assertEquals("EarlyMorning", RecordingName.period(hour(5)));
        assertEquals("Morning", RecordingName.period(hour(9)));
        assertEquals("Noon", RecordingName.period(hour(12)));
        assertEquals("Afternoon", RecordingName.period(hour(14)));
        assertEquals("Evening", RecordingName.period(hour(18)));
        assertEquals("Night", RecordingName.period(hour(20)));
        assertEquals("LateNight", RecordingName.period(hour(23)));
    }

    @Test
    public void parseExtractsSessionDurationTimeAndPlace() {
        RecordingName.Parsed parsed = RecordingName.parse(
                "VoiceDrop-2026-06-18-143052-0m33s-Thu-Afternoon-Shanghai-Xuhui");

        assertNotNull(parsed);
        assertEquals("2026-06-18-143052", parsed.sessionTs);
        assertEquals(Integer.valueOf(6), parsed.month);
        assertEquals(Integer.valueOf(18), parsed.day);
        assertEquals("14:30", parsed.hhmm);
        assertEquals("0m33s", parsed.duration);
        assertEquals("Xuhui", parsed.place);
    }

    @Test
    public void recordingPredicateOnlyAcceptsFinalVoiceDropM4aFiles() {
        assertTrue(RecordingName.isRecordingFile("VoiceDrop-2026-06-18-143052-0m33s-Thu-Afternoon.m4a"));
        assertFalse(RecordingName.isRecordingFile("recording-2026-06-18-143052.m4a"));
        assertFalse(RecordingName.isRecordingFile("VoiceDrop-2026-06-18-143052.wav"));
    }

    @Test
    public void photoKeyUsesSessionOffsetAndThreeBase36Characters() {
        String key = RecordingName.photoKey("2026-06-18-143052", -3);

        assertTrue(key.matches("^photos/2026-06-18-143052/0-[0-9a-z]{3}\\.jpg$"));
    }

    private static ZonedDateTime hour(int hour) {
        return ZonedDateTime.of(2026, 6, 18, hour, 0, 0, 0, ZoneId.of("Asia/Shanghai"));
    }
}
