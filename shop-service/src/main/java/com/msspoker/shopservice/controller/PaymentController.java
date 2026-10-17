package com.msspoker.shopservice.controller;

import com.msspoker.shopservice.dto.request.CreateOrderRequest;
import com.msspoker.shopservice.dto.response.ApiResponse;
import com.msspoker.shopservice.dto.response.OrderResponse;
import com.msspoker.shopservice.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payment APIs", description = "Đơn hàng và thanh toán PayOS")
public class PaymentController {

    private final OrderService orderService;

    @PostMapping("/orders")
    @Operation(summary = "Tạo đơn hàng thanh toán", description = "Tạo đơn hàng mua vật phẩm qua PayOS")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Parameter(name = "X-User-Id", description = "UUID tài khoản người dùng", in = ParameterIn.HEADER, required = true)
            @RequestHeader("X-User-Id") UUID accountId,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        OrderResponse response = orderService.createOrder(accountId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo đơn hàng thanh toán thành công", response));
    }

    @PostMapping("/bundles/{bundleId}/orders")
    @Operation(summary = "Tạo đơn hàng mua gói nạp", description = "Tạo đơn hàng mua gói VIP Coin qua PayOS")
    public ResponseEntity<ApiResponse<OrderResponse>> createBundleOrder(
            @Parameter(name = "X-User-Id", description = "UUID tài khoản người dùng", in = ParameterIn.HEADER, required = true)
            @RequestHeader("X-User-Id") UUID accountId,
            @Parameter(description = "UUID của gói nạp VIP Coin")
            @PathVariable UUID bundleId
    ) {
        OrderResponse response = orderService.createBundleOrder(accountId, bundleId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo đơn hàng mua gói nạp thành công", response));
    }

    @GetMapping("/orders/me")
    @Operation(summary = "Lịch sử đơn hàng của tôi", description = "Lấy danh sách các đơn hàng đã tạo")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getMyOrders(
            @Parameter(name = "X-User-Id", description = "UUID tài khoản người dùng", in = ParameterIn.HEADER, required = true)
            @RequestHeader("X-User-Id") UUID accountId
    ) {
        List<OrderResponse> orders = orderService.getMyOrders(accountId);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách đơn hàng thành công", orders));
    }

    @GetMapping("/orders/{orderId}")
    @Operation(summary = "Chi tiết đơn hàng", description = "Lấy thông tin chi tiết một đơn hàng theo ID")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderDetail(
            @Parameter(name = "X-User-Id", description = "UUID tài khoản người dùng", in = ParameterIn.HEADER, required = true)
            @RequestHeader("X-User-Id") UUID accountId,
            @Parameter(description = "UUID của đơn hàng")
            @PathVariable UUID orderId
    ) {
        OrderResponse order = orderService.getOrderDetail(accountId, orderId);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết đơn hàng thành công", order));
    }

    @PutMapping("/orders/{orderId}/cancel")
    @Operation(summary = "Hủy đơn hàng", description = "Hủy đơn hàng đang chờ thanh toán")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @Parameter(name = "X-User-Id", description = "UUID tài khoản người dùng", in = ParameterIn.HEADER, required = true)
            @RequestHeader("X-User-Id") UUID accountId,
            @Parameter(description = "UUID của đơn hàng")
            @PathVariable UUID orderId
    ) {
        OrderResponse order = orderService.cancelOrder(accountId, orderId);
        return ResponseEntity.ok(ApiResponse.success("Hủy đơn hàng thành công", order));
    }

    @PostMapping("/orders/{orderId}/sync")
    @Operation(summary = "Đồng bộ trạng thái thanh toán", description = "Tra cứu và cập nhật trạng thái đơn hàng từ PayOS")
    public ResponseEntity<ApiResponse<OrderResponse>> syncOrder(
            @Parameter(name = "X-User-Id", description = "UUID tài khoản người dùng", in = ParameterIn.HEADER, required = true)
            @RequestHeader("X-User-Id") UUID accountId,
            @Parameter(description = "UUID của đơn hàng cần đồng bộ")
            @PathVariable UUID orderId
    ) {
        OrderResponse order = orderService.syncOrder(accountId, orderId);
        return ResponseEntity.ok(ApiResponse.success("Đồng bộ trạng thái đơn hàng thành công", order));
    }
}
