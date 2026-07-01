package com.baixingai.voicedrop.core;

import java.security.SecureRandom;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

public final class RecordingName {
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss", Locale.US);
    private static final String[] WEEKDAYS = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
    private static final char[] BASE36 = "0123456789abcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern DURATION = Pattern.compile("^\\d+m\\d+s$");

    private RecordingName() {}

    public static String make(ZonedDateTime start, double durationSeconds, String place) {
        StringBuilder builder = new StringBuilder();
        builder.append("VoiceDrop")
                .append('-').append(timestamp(start))
                .append('-').append(durationTag(durationSeconds))
                .append('-').append(weekday(start))
                .append('-').append(period(start));
        if (place != null && !place.isEmpty()) {
            builder.append('-').append(place);
        }
        return builder.append(".m4a").toString();
    }

    public static Parsed parse(String stem) {
        String[] p = stem.split("-");
        if (p.length < 5 || !"VoiceDrop".equals(p[0]) || p[1].length() != 4) {
            return null;
        }
        String sessionTs = p[1] + "-" + p[2] + "-" + p[3] + "-" + p[4];
        String hhmm = null;
        if (p[4].length() == 6) {
            hhmm = p[4].substring(0, 2) + ":" + p[4].substring(2, 4);
        }
        String duration = null;
        for (String item : p) {
            if (DURATION.matcher(item).matches()) {
                duration = item;
                break;
            }
        }
        String place = p.length >= 10 ? p[9] : (p.length >= 9 ? p[8] : null);
        return new Parsed(sessionTs, parseInt(p[2]), parseInt(p[3]), hhmm, duration, place);
    }

    public static boolean isRecordingFile(String name) {
        return name != null && name.startsWith("VoiceDrop-") && name.endsWith(".m4a");
    }

    public static String timestamp(ZonedDateTime dateTime) {
        return TIMESTAMP.format(dateTime);
    }

    public static String photoKey(String sessionTs, int offset) {
        return "photos/" + sessionTs + "/" + Math.max(0, offset) + "-" + randomTag(3) + ".jpg";
    }

    public static String randomTag(int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(BASE36[RANDOM.nextInt(BASE36.length)]);
        }
        return builder.toString();
    }

    public static String durationTag(double seconds) {
        int total = Math.max(0, (int) Math.round(seconds));
        return (total / 60) + "m" + (total % 60) + "s";
    }

    public static String weekday(ZonedDateTime dateTime) {
        int javaValue = dateTime.getDayOfWeek().getValue();
        int iosWeekday = javaValue == 7 ? 1 : javaValue + 1;
        return WEEKDAYS[(iosWeekday - 1) % 7];
    }

    public static String period(ZonedDateTime dateTime) {
        int hour = dateTime.getHour();
        if (hour >= 5 && hour < 9) return "EarlyMorning";
        if (hour >= 9 && hour < 12) return "Morning";
        if (hour >= 12 && hour < 14) return "Noon";
        if (hour >= 14 && hour < 18) return "Afternoon";
        if (hour >= 18 && hour < 20) return "Evening";
        if (hour >= 20 && hour < 23) return "Night";
        return "LateNight";
    }

    private static Integer parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static final class Parsed {
        public final String sessionTs;
        public final Integer month;
        public final Integer day;
        public final String hhmm;
        public final String duration;
        public final String place;

        public Parsed(String sessionTs, Integer month, Integer day, String hhmm, String duration, String place) {
            this.sessionTs = sessionTs;
            this.month = month;
            this.day = day;
            this.hhmm = hhmm;
            this.duration = duration;
            this.place = place;
        }
    }
}
