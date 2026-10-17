package com.msspoker.shopservice.controller;

import com.msspoker.shopservice.dto.request.CreateOrderRequest;
import com.msspoker.shopservice.dto.response.ApiResponse;
import com.msspoker.shopservice.dto.response.OrderResponse;
import com.msspoker.shopservice.enums.OrderStatus;
import com.msspoker.shopservice.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private PaymentController controller;

    private UUID accountId;
    private UUID orderId;
    private OrderResponse orderResponse;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        orderResponse = new OrderResponse(
                orderId,
                accountId,
                UUID.randomUUID(),
                "Royal Card Back",
                null,
                null,
                null,
                50000L,
                100001L,
                "https://payos.vn/checkout/100001",
                "vietqr-code",
                "https://img.vietqr.io/image/MB-0348732999-compact2.png?amount=50000&addInfo=MSS%20100001",
                OrderStatus.PENDING,
                null,
                Instant.now().plusSeconds(900),
                Instant.now()
        );
    }

    @Test
    @DisplayName("createOrder - Trả về HTTP 201 CREATED kèm thông tin đơn hàng")
    void createOrder() {
        CreateOrderRequest request = new CreateOrderRequest(orderResponse.productId());
        when(orderService.createOrder(accountId, request)).thenReturn(orderResponse);

        ResponseEntity<ApiResponse<OrderResponse>> result = controller.createOrder(accountId, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getData()).isEqualTo(orderResponse);
        verify(orderService).createOrder(accountId, request);
    }

    @Test
    @DisplayName("createBundleOrder - Trả về HTTP 201 CREATED kèm thông tin đơn hàng nạp VIP Coin")
    void createBundleOrder() {
        UUID bundleId = UUID.randomUUID();
        OrderResponse bundleOrderResp = new OrderResponse(
                orderId,
                accountId,
                null,
                null,
                bundleId,
                "Gói 100K",
                115000L,
                100000L,
                200002L,
                "https://payos.vn/checkout/200002",
                "vietqr-bundle",
                "https://img.vietqr.io/image/MB-0348732999-compact2.png?amount=100000&addInfo=MSS%20200002",
                OrderStatus.PENDING,
                null,
                Instant.now().plusSeconds(900),
                Instant.now()
        );
        when(orderService.createBundleOrder(accountId, bundleId)).thenReturn(bundleOrderResp);

        ResponseEntity<ApiResponse<OrderResponse>> result = controller.createBundleOrder(accountId, bundleId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getData()).isEqualTo(bundleOrderResp);
        verify(orderService).createBundleOrder(accountId, bundleId);
    }

    @Test
    @DisplayName("getMyOrders - Trả về danh sách đơn hàng của người dùng")
    void getMyOrders() {
        List<OrderResponse> orders = List.of(orderResponse);
        when(orderService.getMyOrders(accountId)).thenReturn(orders);

        ResponseEntity<ApiResponse<List<OrderResponse>>> result = controller.getMyOrders(accountId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getData()).isEqualTo(orders);
        verify(orderService).getMyOrders(accountId);
    }

    @Test
    @DisplayName("getOrderDetail - Trả về chi tiết đơn hàng")
    void getOrderDetail() {
        when(orderService.getOrderDetail(accountId, orderId)).thenReturn(orderResponse);

        ResponseEntity<ApiResponse<OrderResponse>> result = controller.getOrderDetail(accountId, orderId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getData()).isEqualTo(orderResponse);
        verify(orderService).getOrderDetail(accountId, orderId);
    }

    @Test
    @DisplayName("cancelOrder - Trả về đơn hàng sau khi hủy")
    void cancelOrder() {
        OrderResponse cancelledResponse = new OrderResponse(
                orderId,
                accountId,
                orderResponse.productId(),
                "Royal Card Back",
                null,
                null,
                null,
                50000L,
                100001L,
                null,
                null,
                null,
                OrderStatus.CANCELLED,
                null,
                null,
                Instant.now()
        );
        when(orderService.cancelOrder(accountId, orderId)).thenReturn(cancelledResponse);

        ResponseEntity<ApiResponse<OrderResponse>> result = controller.cancelOrder(accountId, orderId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getData().status()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderService).cancelOrder(accountId, orderId);
    }
}
