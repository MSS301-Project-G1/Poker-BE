package com.msspoker.matchmakingservice.service.presence;

import java.util.UUID;

public interface AbandonedPlayerService {
    /**
     * Đưa người chơi đã đóng tab ra khỏi hàng chờ và party.
     * Trả về false khi trận của họ đang được tạo, để lần quét sau thử lại.
     */
    boolean release(UUID accountId);
}
