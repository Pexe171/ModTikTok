package br.com.modtiktok.tiktokchaos.rule;

import br.com.modtiktok.tiktokchaos.live.LiveEvent;

public final class RuleCondition {
    public String commentCommand = "";
    public int giftId = -1;
    public int minGiftValue = 0;
    public int maxGiftValue = Integer.MAX_VALUE;
    public int threshold = 1;

    public boolean matches(LiveEvent event) {
        if (!commentCommand.isBlank() && !event.normalizedComment().equals(commentCommand.strip().toLowerCase())) {
            return false;
        }
        if (giftId >= 0 && event.giftId() != giftId) {
            return false;
        }
        return event.giftValue() >= minGiftValue && event.giftValue() <= maxGiftValue;
    }

    public boolean isSpecificGift() {
        return giftId >= 0;
    }
}
