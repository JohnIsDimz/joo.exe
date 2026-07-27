package com.tether.parent.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Utility untuk format tanggal & waktu.
 * File ini Java (bukan Kotlin) - demonstrasi mixed-language project.
 */
public final class DateUtils {

    private static final SimpleDateFormat ISO_FORMAT;
    private static final SimpleDateFormat DISPLAY_FORMAT;
    private static final SimpleDateFormat TIME_FORMAT;
    private static final SimpleDateFormat DATE_ONLY_FORMAT;
    private static final SimpleDateFormat DAY_MONTH_FORMAT;

    static {
        ISO_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        ISO_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));

        DISPLAY_FORMAT = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US);
        TIME_FORMAT = new SimpleDateFormat("HH:mm", Locale.US);
        DATE_ONLY_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        DAY_MONTH_FORMAT = new SimpleDateFormat("MMM dd", Locale.US);
    }

    private DateUtils() {
        // Utility class - prevent instantiation
    }

    public static String formatTimestamp(String isoTimestamp) {
        try {
            Date date = ISO_FORMAT.parse(isoTimestamp);
            if (date != null) {
                return DISPLAY_FORMAT.format(date);
            }
            return isoTimestamp;
        } catch (Exception e) {
            return isoTimestamp;
        }
    }

    public static String formatTime(String isoTimestamp) {
        try {
            Date date = ISO_FORMAT.parse(isoTimestamp);
            if (date != null) {
                return TIME_FORMAT.format(date);
            }
            return isoTimestamp;
        } catch (Exception e) {
            return isoTimestamp;
        }
    }

    public static String formatDateOnly(String isoTimestamp) {
        try {
            Date date = ISO_FORMAT.parse(isoTimestamp);
            if (date != null) {
                return DAY_MONTH_FORMAT.format(date);
            }
            return isoTimestamp;
        } catch (Exception e) {
            return isoTimestamp;
        }
    }

    public static String getRelativeTime(String isoTimestamp) {
        try {
            Date date = ISO_FORMAT.parse(isoTimestamp);
            if (date == null) {
                return isoTimestamp;
            }

            long now = new Date().getTime();
            long diff = now - date.getTime();

            if (diff < 60_000L) {
                return "just now";
            } else if (diff < 3_600_000L) {
                return (diff / 60_000L) + "m ago";
            } else if (diff < 86_400_000L) {
                return (diff / 3_600_000L) + "h ago";
            } else if (diff < 604_800_000L) {
                return (diff / 86_400_000L) + "d ago";
            } else {
                return formatDateOnly(isoTimestamp);
            }
        } catch (Exception e) {
            return isoTimestamp;
        }
    }

    public static String getTodayDate() {
        return DATE_ONLY_FORMAT.format(new Date());
    }

    public static String formatDuration(long minutes) {
        long hours = minutes / 60;
        long mins = minutes % 60;
        if (hours > 0) {
            return hours + "h " + mins + "m";
        } else {
            return mins + "m";
        }
    }
}
