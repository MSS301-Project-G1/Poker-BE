package com.msspoker.shopservice.validator;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Validator interface tập trung toàn bộ các quy tắc nghiệp vụ cho CardDeck.
 */
public interface CardDeckValidator {

    String CARD_BACK_CODE = "BACK";

    String[] RANKS = {"2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A"};
    String[] SUITS = {"S", "H", "D", "C"}; // Spades, Hearts, Diamonds, Clubs

    Set<String> CORE_CARD_CODES = Collections.unmodifiableSet(initCoreCodes());

    private static Set<String> initCoreCodes() {
        Set<String> codes = new LinkedHashSet<>();
        for (String rank : RANKS) {
            for (String suit : SUITS) {
                codes.add(rank + suit);
            }
        }
        codes.add(CARD_BACK_CODE);
        return codes;
    }

    Set<String> getCoreCardCodes();

    String extractCardCode(String filename);

    void validateCoreCards(Set<String> presentCodes);

    void validateZipEntry(String entryName);
}
