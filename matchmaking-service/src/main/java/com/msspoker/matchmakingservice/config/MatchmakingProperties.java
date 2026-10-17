package com.msspoker.matchmakingservice.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Component
@Validated
@ConfigurationProperties(prefix = "matchmaking")
public class MatchmakingProperties {
    @Valid private final Normal normal = new Normal();
    @Valid private final Rank rank = new Rank();
    @Valid private final Queue queue = new Queue();
    @Valid private final Finder finder = new Finder();
    @Valid private final Game game = new Game();
    @Valid private final Recovery recovery = new Recovery();
    @Valid private final Party party = new Party();
    @Valid private final Ranking ranking = new Ranking();
    @Valid private final Restriction restriction = new Restriction();
    @Valid private final Wallet wallet = new Wallet();

    public Normal getNormal() { return normal; }
    public Rank getRank() { return rank; }
    public Queue getQueue() { return queue; }
    public Finder getFinder() { return finder; }
    public Game getGame() { return game; }
    public Recovery getRecovery() { return recovery; }
    public Party getParty() { return party; }
    public Ranking getRanking() { return ranking; }
    public Restriction getRestriction() { return restriction; }
    public Wallet getWallet() { return wallet; }

    public static class Normal {
        @NotNull private Duration fillTimeout = Duration.ofSeconds(10);
        public Duration getFillTimeout() { return fillTimeout; }
        public void setFillTimeout(Duration fillTimeout) { this.fillTimeout = fillTimeout; }

        @AssertTrue(message = "matchmaking.normal.fillTimeout phải lớn hơn 0")
        public boolean isValid() {
            return fillTimeout != null && !fillTimeout.isZero() && !fillTimeout.isNegative();
        }
    }

    public static class Rank {
        @Min(0) private int eloWindow = 100;
        @Min(0) private long entryFee = 100;
        @NotNull private Duration fillTimeout = Duration.ofSeconds(10);
        @NotNull private Duration acceptTimeout = Duration.ofSeconds(10);
        @NotNull private Duration cooldown = Duration.ofMinutes(2);
        public int getEloWindow() { return eloWindow; }
        public void setEloWindow(int eloWindow) { this.eloWindow = eloWindow; }
        public long getEntryFee() { return entryFee; }
        public void setEntryFee(long entryFee) { this.entryFee = entryFee; }
        public Duration getFillTimeout() { return fillTimeout; }
        public void setFillTimeout(Duration fillTimeout) { this.fillTimeout = fillTimeout; }
        public Duration getAcceptTimeout() { return acceptTimeout; }
        public void setAcceptTimeout(Duration acceptTimeout) { this.acceptTimeout = acceptTimeout; }
        public Duration getCooldown() { return cooldown; }
        public void setCooldown(Duration cooldown) { this.cooldown = cooldown; }

        @AssertTrue(message = "matchmaking.rank fillTimeout/acceptTimeout/cooldown phải lớn hơn 0")
        public boolean isValid() {
            return fillTimeout != null && !fillTimeout.isZero() && !fillTimeout.isNegative()
                    && acceptTimeout != null && !acceptTimeout.isZero() && !acceptTimeout.isNegative()
                    && cooldown != null && !cooldown.isZero() && !cooldown.isNegative();
        }
    }

    public static class Queue {
        @NotNull private Duration entryTtl = Duration.ofHours(24);
        @NotNull private Duration reservationTtl = Duration.ofMinutes(2);
        public Duration getEntryTtl() { return entryTtl; }
        public void setEntryTtl(Duration entryTtl) { this.entryTtl = entryTtl; }
        public Duration getReservationTtl() { return reservationTtl; }
        public void setReservationTtl(Duration reservationTtl) { this.reservationTtl = reservationTtl; }
    }

    public static class Finder {
        private boolean enabled = true;
        @Min(100) private long fixedDelayMs = 1000;
        @NotNull private Duration lockTtl = Duration.ofSeconds(5);
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public long getFixedDelayMs() { return fixedDelayMs; }
        public void setFixedDelayMs(long fixedDelayMs) { this.fixedDelayMs = fixedDelayMs; }
        public Duration getLockTtl() { return lockTtl; }
        public void setLockTtl(Duration lockTtl) { this.lockTtl = lockTtl; }
    }

    public static class Game {
        @NotNull private Duration timeout = Duration.ofSeconds(3);
        @NotNull private Duration sessionTtl = Duration.ofHours(24);
        @NotBlank private String publicHost = "localhost";
        @Min(1) @Max(65535) private int publicPort = 8082;
        @NotBlank private String websocketPath = "/ws/game";
        private boolean stubEnabled;
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
        public Duration getSessionTtl() { return sessionTtl; }
        public void setSessionTtl(Duration sessionTtl) { this.sessionTtl = sessionTtl; }
        public String getPublicHost() { return publicHost; }
        public void setPublicHost(String publicHost) { this.publicHost = publicHost; }
        public int getPublicPort() { return publicPort; }
        public void setPublicPort(int publicPort) { this.publicPort = publicPort; }
        public String getWebsocketPath() { return websocketPath; }
        public void setWebsocketPath(String websocketPath) { this.websocketPath = websocketPath; }
        public boolean isStubEnabled() { return stubEnabled; }
        public void setStubEnabled(boolean stubEnabled) { this.stubEnabled = stubEnabled; }
    }

    public static class Recovery {
        @Min(1000) private long fixedDelayMs = 5000;
        @NotNull private Duration retryAfter = Duration.ofSeconds(10);
        public long getFixedDelayMs() { return fixedDelayMs; }
        public void setFixedDelayMs(long fixedDelayMs) { this.fixedDelayMs = fixedDelayMs; }
        public Duration getRetryAfter() { return retryAfter; }
        public void setRetryAfter(Duration retryAfter) { this.retryAfter = retryAfter; }
    }

    public static class Party {
        @Min(2) @Max(6) private int maxMembers = 6;
        @NotNull private Duration inviteTtl = Duration.ofMinutes(15);
        public int getMaxMembers() { return maxMembers; }
        public void setMaxMembers(int maxMembers) { this.maxMembers = maxMembers; }
        public Duration getInviteTtl() { return inviteTtl; }
        public void setInviteTtl(Duration inviteTtl) { this.inviteTtl = inviteTtl; }
    }

    public static class Ranking {
        @NotNull private Duration timeout = Duration.ofSeconds(3);
        private boolean stubEnabled;
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
        public boolean isStubEnabled() { return stubEnabled; }
        public void setStubEnabled(boolean stubEnabled) { this.stubEnabled = stubEnabled; }
    }

    public static class Restriction {
        @NotNull private Duration timeout = Duration.ofSeconds(3);
        private boolean stubEnabled;
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
        public boolean isStubEnabled() { return stubEnabled; }
        public void setStubEnabled(boolean stubEnabled) { this.stubEnabled = stubEnabled; }
    }

    public static class Wallet {
        @NotNull private Duration timeout = Duration.ofSeconds(3);
        private boolean stubEnabled;
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
        public boolean isStubEnabled() { return stubEnabled; }
        public void setStubEnabled(boolean stubEnabled) { this.stubEnabled = stubEnabled; }
    }
}
