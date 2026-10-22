package br.com.modtiktok.tiktokchaos.rule;

import br.com.modtiktok.tiktokchaos.live.LiveEvent;
import br.com.modtiktok.tiktokchaos.live.LiveEventType;

import java.util.Locale;

public final class RuleCondition {
    public String commentCommand = "";
    public int giftId = -1;
    public String giftName = "";
    public int minGiftValue = 0;
    public int maxGiftValue = Integer.MAX_VALUE;
    public int threshold = 1;

    public boolean matches(LiveEvent event) {
        if (event.type() == LiveEventType.COMMENT && commentCommand != null && !commentCommand.isBlank()
                && !event.normalizedComment().equals(commentCommand.strip().toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (event.type() != LiveEventType.GIFT) return true;
        if (giftId >= 0 && event.giftId() != giftId) {
            return false;
        }
        if (giftName != null && !giftName.isBlank()
                && !giftName.strip().equalsIgnoreCase(event.giftName().strip())) {
            return false;
        }
        return event.giftValue() >= minGiftValue && event.giftValue() <= maxGiftValue;
    }

    public boolean isSpecificGift() {
        return giftId >= 0 || (giftName != null && !giftName.isBlank());
    }
}
