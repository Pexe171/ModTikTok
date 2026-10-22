package br.com.modtiktok.tiktokchaos.rule;

import br.com.modtiktok.tiktokchaos.live.LiveEvent;
import br.com.modtiktok.tiktokchaos.live.LiveEventType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleConditionTest {
    @Test
    void nameMatchingIsExactIgnoringCaseAndSurroundingWhitespace() {
        RuleCondition condition = new RuleCondition();
        condition.giftName = "  rOsE  ";
        assertTrue(condition.isSpecificGift());
        assertTrue(condition.matches(LiveEvent.gift("Ana", 1, " Rose ", 1, 1)));
        assertFalse(condition.matches(LiveEvent.gift("Ana", 1, "Rose Bear", 1, 1)));
        assertFalse(condition.matches(LiveEvent.gift("Ana", 1, "", 1, 1)));
    }

    @Test
    void bothNameAndIdMustMatchWhenBothAreSet() {
        RuleCondition condition = new RuleCondition();
        condition.giftName = "Rose";
        condition.giftId = 5655;
        assertTrue(condition.matches(LiveEvent.gift("Ana", 5655, "Rose", 1, 1)));
        assertFalse(condition.matches(LiveEvent.gift("Ana", 777, "Rose", 1, 1)));
        assertFalse(condition.matches(LiveEvent.gift("Ana", 5655, "Other", 1, 1)));
    }

    @Test
    void blankOrMissingNamesKeepExistingIdAndValueConditions() {
        RuleCondition condition = new RuleCondition();
        for (String name : new String[]{"", "  ", null}) {
            condition.giftName = name;
            assertFalse(condition.isSpecificGift());
            assertTrue(condition.matches(LiveEvent.gift("Ana", 5655, "Rose", 1, 1)));
        }
        condition.giftId = 5655;
        assertTrue(condition.isSpecificGift());
        assertTrue(condition.matches(LiveEvent.gift("Ana", 5655, "Rose", 1, 1)));
        assertFalse(condition.matches(LiveEvent.gift("Ana", 777, "Rose", 1, 1)));
        condition.minGiftValue = 10;
        assertFalse(condition.matches(LiveEvent.gift("Ana", 5655, "Rose", 1, 1)));
    }

    @Test
    void inactiveEventFiltersDoNotBlockRulesAfterSwitchingEventType() {
        RuleCondition condition = new RuleCondition();
        condition.commentCommand = "!novo";
        assertTrue(condition.matches(LiveEvent.gift("Ana", 5655, "Rose", 1, 1)));
        assertTrue(condition.matches(LiveEvent.simple(LiveEventType.FOLLOW, "Ana")));
        assertFalse(condition.matches(LiveEvent.comment("Ana", "!outro")));
        assertTrue(condition.matches(LiveEvent.comment("Ana", "!novo")));
        condition.giftId = 5655;
        condition.giftName = "Rose";
        condition.minGiftValue = 100;
        assertTrue(condition.matches(LiveEvent.comment("Ana", "!novo")));
        assertTrue(condition.matches(LiveEvent.like("Ana", 100)));
    }
}
