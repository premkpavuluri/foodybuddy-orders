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
     * Bulk update order status for orders with specific status
     * This method finds all orders with the given status and updates them to the new status
     */
    public Map<String, Object> bulkUpdateOrderStatus(OrderStatus fromStatus, OrderStatus toStatus) {
        // Validate status transition
        if (!fromStatus.canTransitionTo(toStatus)) {
            throw new IllegalArgumentException("Invalid status transition from " + fromStatus + " to " + toStatus);
        }
        
        // Get all orders with the current status
        List<Order> ordersToUpdate = orderRepository.findByStatus(fromStatus);
        
        if (ordersToUpdate.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "No orders found with status " + fromStatus);
            result.put("updatedCount", 0);
            result.put("fromStatus", fromStatus.name());
            result.put("toStatus", toStatus.name());
            return result;
        }
        
        // Update all orders
        int updatedCount = 0;
        for (Order order : ordersToUpdate) {
            try {
                OrderStatus oldStatus = order.getStatus();
                order.setStatus(toStatus);
                orderRepository.save(order);
                
                // Notify gateway about status change
                notifyGatewayStatusUpdate(order.getOrderId(), toStatus.name(), 
                    "Bulk status update from " + oldStatus + " to " + toStatus);
                
                updatedCount++;
            } catch (Exception e) {
                // Log error but continue with other orders
                System.err.println("Failed to update order " + order.getOrderId() + ": " + e.getMessage());
            }
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Successfully updated " + updatedCount + " orders from " + fromStatus + " to " + toStatus);
        result.put("updatedCount", updatedCount);
        result.put("totalFound", ordersToUpdate.size());
        result.put("fromStatus", fromStatus.name());
        result.put("toStatus", toStatus.name());
        
        return result;
    }
    
    /**
     * Process all order status progressions automatically
     * 
     * This method:
     * 1. Gets all orders with target statuses (CONFIRMED, PREPARING, READY, OUT_FOR_DELIVERY)
     * 2. Creates a map of orders by their current status
     * 3. Updates each set of orders to their desired destination status
     */
    public Map<String, Object> processAllStatusProgressions() {
        Map<String, Object> overallResult = new HashMap<>();
        overallResult.put("success", true);
        overallResult.put("message", "Order status progression processing completed");
        overallResult.put("timestamp", java.time.Instant.now().toString());
        
        // Define target statuses and their destination statuses
        OrderStatus[] targetStatuses = {
            OrderStatus.CONFIRMED,
            OrderStatus.PREPARING, 
            OrderStatus.READY,
            OrderStatus.OUT_FOR_DELIVERY
        };
        
        OrderStatus[] destinationStatuses = {
            OrderStatus.PREPARING,
            OrderStatus.READY,
            OrderStatus.OUT_FOR_DELIVERY,
            OrderStatus.DELIVERED
        };
        
        String[] stepNames = {
            "confirmed_to_preparing",
            "preparing_to_ready", 
            "ready_to_out_for_delivery",
            "out_for_delivery_to_delivered"
        };
        
        // Create a map of orders by their current status
        Map<OrderStatus, List<Order>> ordersByStatus = new HashMap<>();
        
        // Get all orders with target statuses and group them by status
        for (OrderStatus status : targetStatuses) {
            List<Order> orders = orderRepository.findByStatus(status);
            ordersByStatus.put(status, orders);
        }
        
        // Track results for each step
        Map<String, Object> stepResults = new HashMap<>();
        int totalUpdated = 0;
        
        // Process each status transition
        for (int i = 0; i < targetStatuses.length; i++) {
            OrderStatus fromStatus = targetStatuses[i];
            OrderStatus toStatus = destinationStatuses[i];
            String stepName = stepNames[i];
            
            try {
                List<Order> ordersToUpdate = ordersByStatus.get(fromStatus);
                
                if (ordersToUpdate == null || ordersToUpdate.isEmpty()) {
                    Map<String, Object> stepResult = new HashMap<>();
                    stepResult.put("success", true);
                    stepResult.put("fromStatus", fromStatus.toString());
                    stepResult.put("toStatus", toStatus.toString());
                    stepResult.put("updatedCount", 0);
                    stepResult.put("totalFound", 0);
                    stepResult.put("message", "No orders found with status " + fromStatus);
                    stepResults.put(stepName, stepResult);
                    continue;
                }
                
                // Update all found orders to the destination status
                int updatedCount = 0;
                for (Order order : ordersToUpdate) {
                    if (order.getStatus().canTransitionTo(toStatus)) {
                        order.setStatus(toStatus);
                        order.setUpdatedAt(java.time.LocalDateTime.now());
                        orderRepository.save(order);
                        
                        // Notify gateway about the status change
                        notifyGatewayStatusUpdate(
                            order.getOrderId(), 
                            toStatus.toString(), 
                            "Order status updated from " + fromStatus + " to " + toStatus
                        );
                        
                        updatedCount++;
                    }
                }
                
                Map<String, Object> stepResult = new HashMap<>();
                stepResult.put("success", true);
                stepResult.put("fromStatus", fromStatus.toString());
                stepResult.put("toStatus", toStatus.toString());
                stepResult.put("updatedCount", updatedCount);
                stepResult.put("totalFound", ordersToUpdate.size());
                stepResult.put("message", "Successfully updated " + updatedCount + " orders from " + fromStatus + " to " + toStatus);
                stepResults.put(stepName, stepResult);
                
                totalUpdated += updatedCount;
                
            } catch (Exception e) {
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("success", false);
                errorResult.put("message", fromStatus + " -> " + toStatus + " failed: " + e.getMessage());
                stepResults.put(stepName, errorResult);
            }
        }
        
        overallResult.put("totalOrdersUpdated", totalUpdated);
        overallResult.put("stepResults", stepResults);
        
        return overallResult;
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
