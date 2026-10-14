package com.msspoker.competitionservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "competition")
public class CompetitionProperties {
    private Room room = new Room();
    private Tournament tournament = new Tournament();

    @Getter
    @Setter
    public static class Room {
        private int minPlayers = 2;
        /** Must not exceed game-service's CUSTOM maxPlayers, which caps the table. */
        private int maxPlayers = 6;
        /** custom_rooms stores blinds only; chips and turn time follow SPEC §8 defaults. */
        private long startingChips = 1000;
        private int turnTimeSeconds = 20;
        private int codeLength = 6;
    }

    /** SPEC §2.4: 32 players, 4 tables of 8, top 2 of each table reach an 8-player final. */
    @Getter
    @Setter
    public static class Tournament {
        private int slots = 32;
        /** Must not exceed game-service's TOURNAMENT maxPlayers. */
        private int tableSize = 8;
        private int advancePerTable = 2;
        /** Fewer players cannot start; at startAt such a tournament is cancelled and fees are refunded. */
        private int minPlayers = 4;
        /** SPEC §7.7: ELITE invitations go out this long before startAt. */
        private int inviteLeadMinutes = 30;
        /** SPEC §7.7: invitations still unconfirmed this long before startAt go to the next ranks. */
        private int confirmDeadlineMinutes = 10;
        /** SPEC §8: prize pool share of final places 1, 2, 3 in percent. */
        private List<Integer> prizeShares = List.of(50, 30, 20);

        /** Largest field whose qualifier winners still fit one final table. */
        public int maxSlots() {
            return tableSize * (tableSize / advancePerTable);
        }
    }
}
