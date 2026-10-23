package com.msspoker.shopservice.validator;

import com.msspoker.shopservice.dto.request.CreateOrderRequest;
import com.msspoker.shopservice.entity.Product;

import java.util.UUID;

/**
 * Validator interface tập trung toàn bộ các quy tắc nghiệp vụ cho Order.
 */
public interface OrderValidator {

    void validateCreateOrder(UUID accountId, CreateOrderRequest request, Product product);
}
