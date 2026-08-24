package br.com.modtiktok.tiktokchaos.live;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public record LiveEvent(
        String id,
        LiveEventType type,
        long occurredAt,
        String userId,
        String userName,
        int amount,
        int total,
        int giftId,
        String giftName,
        int giftValue,
        String comment,
        String avatarUrl
) {
    public LiveEvent {
        id = clean(id, UUID.randomUUID().toString());
        userId = clean(userId, "");
        userName = clean(userName, "Anônimo");
        giftName = clean(giftName, "");
        comment = clean(comment, "");
        avatarUrl = cleanUrl(avatarUrl);
        amount = Math.max(0, amount);
        total = Math.max(0, total);
        giftValue = Math.max(0, giftValue);
    }

    public LiveEvent(String id, LiveEventType type, long occurredAt, String userId, String userName, int amount,
                     int total, int giftId, String giftName, int giftValue, String comment) {
        this(id, type, occurredAt, userId, userName, amount, total, giftId, giftName, giftValue, comment, "");
    }

    public static LiveEvent simple(LiveEventType type, String userName) {
        return new LiveEvent(UUID.randomUUID().toString(), type, Instant.now().toEpochMilli(), "", userName,
                1, 0, -1, "", 0, "");
    }

    public static LiveEvent like(String userName, int amount) {
        return new LiveEvent(UUID.randomUUID().toString(), LiveEventType.LIKE, Instant.now().toEpochMilli(), "",
                userName, amount, amount, -1, "", 0, "");
    }

    public static LiveEvent gift(String userName, int giftId, String giftName, int giftValue, int combo) {
        return new LiveEvent(UUID.randomUUID().toString(), LiveEventType.GIFT, Instant.now().toEpochMilli(), "",
                userName, Math.max(1, combo), 0, giftId, giftName, giftValue * Math.max(1, combo), "");
    }

    public static LiveEvent comment(String userName, String comment) {
        return new LiveEvent(UUID.randomUUID().toString(), LiveEventType.COMMENT, Instant.now().toEpochMilli(), "",
                userName, 1, 0, -1, "", 0, comment);
    }

    public String normalizedComment() {
        return comment.strip().toLowerCase(Locale.ROOT);
    }

    public int priority() {
        return switch (type) {
            case GIFT, SUBSCRIBE -> 0;
            case FOLLOW, SHARE -> 1;
            case COMMENT -> 2;
            case LIKE -> 3;
            default -> 4;
        };
    }

    public String summary() {
        return switch (type) {
            case LIKE -> userName + " enviou " + amount + " curtidas";
            case GIFT -> userName + " enviou " + giftName + " x" + amount;
            case COMMENT -> userName + ": " + comment;
            case FOLLOW -> userName + " seguiu a LIVE";
            case SHARE -> userName + " compartilhou a LIVE";
            case SUBSCRIBE -> userName + " assinou a LIVE";
            case JOIN -> userName + " entrou na LIVE";
            case ROOM_STATS -> total + " espectadores na LIVE";
            case LIVE_STARTED -> "LIVE conectada";
            case LIVE_ENDED -> "LIVE encerrada";
        };
    }

    private static String clean(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String withoutControls = value.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "").strip();
        return withoutControls.length() > 160 ? withoutControls.substring(0, 160) : withoutControls;
    }

    private static String cleanUrl(String value) {
        if (value == null || value.isBlank()) return "";
        String clean = value.replaceAll("[\\p{Cntrl}\\s]", "");
        return clean.length() > 2_048 ? "" : clean;
    }
}
