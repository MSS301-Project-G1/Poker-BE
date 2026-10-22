package com.msspoker.gameservice.websocket;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/** Scheduling stays enabled here: the deadlock only appears when the bounded taskScheduler bean exists. */
@ActiveProfiles("dev")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:h2:mem:poker-stomp-executor;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "game.scheduling-enabled=true"})
class StompChannelExecutorTest {
    private static final int WAITING_HANDLERS = 8;

    @Autowired @Qualifier("clientInboundChannel") ExecutorSubscribableChannel inbound;
    @Autowired @Qualifier("clientOutboundChannel") ExecutorSubscribableChannel outbound;

    @Test
    void publishingWhileHoldingATableLockIsNotStarvedByInboundHandlersWaitingForThatLock() throws Exception {
        ReentrantLock tableLock = new ReentrantLock();
        CountDownLatch handlersStarted = new CountDownLatch(WAITING_HANDLERS);
        CompletableFuture<Void> tick = CompletableFuture.runAsync(() -> {
            tableLock.lock();
            try {
                for (int i = 0; i < WAITING_HANDLERS; i++) {
                    inbound.getExecutor().execute(() -> {
                        handlersStarted.countDown();
                        tableLock.lock();
                        tableLock.unlock();
                    });
                }
                handlersStarted.await();
                outbound.send(snapshotMessage());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                tableLock.unlock();
            }
        });

        tick.get(5, TimeUnit.SECONDS);
    }

    private static org.springframework.messaging.Message<byte[]> snapshotMessage() {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setSessionId("no-such-session");
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }
}
