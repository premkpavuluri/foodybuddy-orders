package com.foodybuddy.orders.service;

import com.foodybuddy.orders.dto.CreateOrderRequest;
import com.foodybuddy.orders.dto.OrderResponse;
import com.foodybuddy.orders.entity.Order;
import com.foodybuddy.orders.entity.OrderItem;
import com.foodybuddy.orders.entity.OrderStatus;
import com.foodybuddy.orders.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Order Service
 * 
 * This service handles all order-related operations in the FoodyBuddy application.
 * It manages the complete order lifecycle from creation to delivery.
 * 
 * Key responsibilities:
 * - Create new orders with items and customer details
 * - Track order status throughout the lifecycle
 * - Update order status and notify the gateway
 * - Provide order history and details
 * - Handle order status transitions with validation
 */
@Service
@Transactional
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final RestTemplate restTemplate;
    private final String gatewayStatusUpdateUrl;

    public OrderService(OrderRepository orderRepository, 
                       RestTemplate restTemplate,
                       @Value("${gateway.url:http://localhost:8080}") String gatewayUrl) {
        this.orderRepository = orderRepository;
        this.restTemplate = restTemplate;
        this.gatewayStatusUpdateUrl = gatewayUrl + "/api/gateway/orders/status";
    }
    
    public OrderResponse createOrder(CreateOrderRequest request) {
        String orderId = UUID.randomUUID().toString();
        
        // Create order items
        List<OrderItem> orderItems = request.getItems().stream()
                .map(item -> new OrderItem(
                        item.getItemId(),
                        item.getItemName(),
                        item.getQuantity(),
                        item.getPrice()
                ))
                .collect(Collectors.toList());
        
        // Calculate total
        Double total = orderItems.stream()
                .mapToDouble(item -> item.getPrice() * item.getQuantity())
                .sum();
        
        // Create order
        Order order = new Order(orderId, orderItems, total, OrderStatus.PENDING);
        
        // Set order reference in items
        orderItems.forEach(item -> item.setOrder(order));
        
        // Save order
        Order savedOrder = orderRepository.save(order);
        
        return convertToResponse(savedOrder);
    }
    
    public OrderResponse getOrder(String orderId) {
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
        
        return convertToResponse(order);
    }
    
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }
    
    public OrderResponse updateOrderStatus(String orderId, OrderStatus status) {
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
        
        OrderStatus oldStatus = order.getStatus();
        order.setStatus(status);
        Order updatedOrder = orderRepository.save(order);
        
        // Notify gateway about status change
        notifyGatewayStatusUpdate(orderId, status.name(), "Order status updated from " + oldStatus + " to " + status);
        
        return convertToResponse(updatedOrder);
    }
    
    /**
     * Notify gateway about order status changes
     */
    private void notifyGatewayStatusUpdate(String orderId, String status, String message) {
        try {
            Map<String, Object> statusUpdateRequest = new HashMap<>();
            statusUpdateRequest.put("orderId", orderId);
            statusUpdateRequest.put("status", status);
            statusUpdateRequest.put("message", message);
            statusUpdateRequest.put("updatedBy", "order-service");
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(statusUpdateRequest, headers);
            
            restTemplate.postForObject(gatewayStatusUpdateUrl, entity, Map.class);
        } catch (Exception e) {
            // Log the error but don't fail the order update
            System.err.println("Failed to notify gateway about status update: " + e.getMessage());
        }
    }
    
    private OrderResponse convertToResponse(Order order) {
        List<OrderResponse.OrderItemResponse> itemResponses = order.getItems().stream()
                .map(item -> new OrderResponse.OrderItemResponse(
                        item.getId(),
                        item.getItemId(),
                        item.getItemName(),
                        item.getQuantity(),
                        item.getPrice()
                ))
                .collect(Collectors.toList());
        
        return new OrderResponse(
                order.getId(),
                order.getOrderId(),
                itemResponses,
                order.getTotal(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
