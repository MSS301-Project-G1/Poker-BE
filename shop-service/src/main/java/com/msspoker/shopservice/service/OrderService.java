package com.msspoker.shopservice.service;

import com.msspoker.shopservice.dto.request.CreateOrderRequest;
import com.msspoker.shopservice.dto.response.OrderResponse;
import vn.payos.model.webhooks.Webhook;

import java.util.List;
import java.util.UUID;

public interface OrderService {

    OrderResponse createOrder(UUID accountId, CreateOrderRequest request);

    OrderResponse createBundleOrder(UUID accountId, UUID bundleId);

    OrderResponse getOrderDetail(UUID accountId, UUID orderId);

    List<OrderResponse> getMyOrders(UUID accountId);

    OrderResponse cancelOrder(UUID accountId, UUID orderId);

    OrderResponse syncOrder(UUID accountId, UUID orderId);

    void handlePayosWebhook(Webhook webhook);

    void reconcileOrder(UUID orderId);
}
