package com.msspoker.gameservice.realtime;

import com.msspoker.gameservice.engine.ActionType;
import com.msspoker.gameservice.exception.GameApiException;
import com.msspoker.gameservice.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("dev")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:h2:mem:poker-realtime;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "game.scheduling-enabled=false"})
class GameRealtimeTest {
    @LocalServerPort int port;
    @Autowired DevTableService dev;
    @Autowired GameplayService gameplay;
    @Autowired TableRegistry registry;
    @Autowired JsonMapper json;

    @Test
    void personalSnapshotsHideOpponentsAndReconnectKeepsTheSameCards() {
        var table = dev.create(2, 0);
        UUID first = table.humanIds().getFirst();
        var snapshot = gameplay.snapshot(table.tableId(), first);
        assertEquals(2, snapshot.seats().getFirst().cards().size());
        assertTrue(snapshot.seats().get(1).cards().isEmpty());
        assertEquals(2, snapshot.seats().get(1).holeCardCount());
        assertEquals(snapshot.seats(), gameplay.snapshot(table.tableId(), first).seats());
        assertThrows(GameApiException.class, () -> gameplay.snapshot(table.tableId(), UUID.randomUUID()));
    }

    @Test
    void realStompConnectionReceivesPrivateSnapshotActionErrorsAndResync() throws Exception {
        var table = dev.create(2, 0);
        try (StompSocket socket = connect(table.humanIds().getFirst())) {
            socket.send("SUBSCRIBE\nid:table\ndestination:/user/queue/tables/" + table.tableId() + "\n\n");
            socket.send("SUBSCRIBE\nid:errors\ndestination:/user/queue/game-errors\n\n");
            socket.send("SEND\ndestination:/app/tables/" + table.tableId() + "/sync\n\n");
            JsonNode first = socket.body();
            assertEquals(table.tableId().toString(), first.get("tableId").asString());
            assertEquals(2, first.get("seats").get(0).get("cards").size());
            assertEquals(0, first.get("seats").get(1).get("cards").size());
            long sequence = first.get("actionSequence").asLong();
            socket.send("SEND\ndestination:/app/tables/" + table.tableId() + "/action\ncontent-type:application/json\n\n"
                    + "{\"type\":\"CHECK\",\"actionSequence\":" + sequence + "}");
            assertEquals("GAME_INVALID_ACTION", socket.body().get("code").asString());
            socket.send("SEND\ndestination:/app/tables/" + table.tableId() + "/action\ncontent-type:application/json\n\n"
                    + "{\"type\":\"CALL\",\"actionSequence\":" + sequence + "}");
            JsonNode next = socket.body();
            assertEquals(sequence + 1, next.get("actionSequence").asLong());
            socket.send("SEND\ndestination:/app/tables/" + table.tableId() + "/sync\n\n");
            assertEquals(sequence + 1, socket.body().get("actionSequence").asLong());
        }
    }

    @Test
    void outsiderSubscriptionIsRejectedBeforeAnySnapshotIsSent() throws Exception {
        var table = dev.create(2, 0);
        try (StompSocket socket = connect(UUID.randomUUID())) {
            socket.send("SUBSCRIBE\nid:stolen\ndestination:/user/queue/tables/" + table.tableId() + "\n\n");
            assertTrue(socket.frame().startsWith("ERROR"));
        }
    }

    @Test
    void dueTimerChecksOrFoldsAndStaleClientActionIsRejected() {
        var table = dev.create(2, 0);
        var managed = registry.require(table.tableId());
        long sequence = managed.getEngine().getSequence();
        managed.setDeadline(Instant.now().minusSeconds(1));
        gameplay.tick();
        assertEquals(sequence + 1, managed.getEngine().getSequence());
        assertThrows(IllegalStateException.class, () -> gameplay.action(table.tableId(), table.humanIds().getFirst(),
                new ActionRequest(ActionType.FOLD, null, sequence)));
    }

    private StompSocket connect(UUID id) throws Exception {
        StompSocket socket = new StompSocket();
        socket.connection = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws/game"), socket).get(5, TimeUnit.SECONDS);
        socket.send("CONNECT\naccept-version:1.2\nhost:localhost\ndevUser:" + id + "\nheart-beat:0,0\n\n");
        assertTrue(socket.frame().startsWith("CONNECTED"));
        return socket;
    }

    private class StompSocket implements WebSocket.Listener, AutoCloseable {
        private final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        private final StringBuilder buffer = new StringBuilder();
        private WebSocket connection;

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            int end;
            while ((end = buffer.indexOf("\0")) >= 0) {
                frames.add(buffer.substring(0, end).stripLeading());
                buffer.delete(0, end + 1);
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            return onText(webSocket, StandardCharsets.UTF_8.decode(data), last);
        }

        void send(String frame) {
            connection.sendText(frame + "\0", true).join();
        }

        String frame() throws InterruptedException {
            String frame = frames.poll(5, TimeUnit.SECONDS);
            assertNotNull(frame, "No STOMP frame received");
            return frame;
        }

        JsonNode body() throws InterruptedException {
            String frame = frame();
            assertTrue(frame.startsWith("MESSAGE"), frame);
            return json.readTree(frame.substring(frame.indexOf("\n\n") + 2));
        }

        @Override
        public void close() {
            connection.abort();
        }
    }
}
