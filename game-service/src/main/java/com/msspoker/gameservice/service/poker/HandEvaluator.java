package com.msspoker.gameservice.service.poker;

import com.msspoker.gameservice.model.poker.Card;
import com.msspoker.gameservice.model.poker.HandRank;

import java.util.List;

public interface HandEvaluator {
    HandRank bestOfSeven(List<Card> cards);
}
