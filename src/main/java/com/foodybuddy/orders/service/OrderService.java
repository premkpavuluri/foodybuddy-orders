package com.foodybuddy.orders.service;

import com.foodybuddy.orders.dto.CreateOrderRequest;
import com.foodybuddy.orders.dto.OrderResponse;
import com.foodybuddy.orders.entity.Order;
import com.foodybuddy.orders.entity.OrderItem;
import com.foodybuddy.orders.entity.OrderStatus;
import com.foodybuddy.orders.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    
    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);
    private final OrderRepository orderRepository;
    private final RestTemplate restTemplate;
    private final String gatewayStatusUpdateUrl;

    public OrderService(OrderRepository orderRepository, 
                       RestTemplate restTemplate,
                       @Value("${gateway.url:http://localhost:8080}") String gatewayUrl) {
        this.orderRepository = orderRepository;
        this.restTemplate = restTemplate;
        this.gatewayStatusUpdateUrl = gatewayUrl + "/api/gateway/orders/status";
        logger.info("OrderService initialized with gateway URL: {}", this.gatewayStatusUpdateUrl);
    }
    
    public OrderResponse createOrder(CreateOrderRequest request) {
        String orderId = UUID.randomUUID().toString();
        logger.info("Creating new order - OrderId: {}, UserId: {}, Items: {}", 
            orderId, request.getUserId(), request.getItems().size());
        
        // Create order items
        List<OrderItem> orderItems = request.getItems().stream()
                .map(item -> new OrderItem(
                        item.getItemId(),
                        item.getItemName(),
                        item.getQuantity(),
                        item.getPrice()
                ))
                .collect(Collectors.toList());
        
        logger.debug("Created {} order items", orderItems.size());
        
        // Calculate total
        Double total = orderItems.stream()
                .mapToDouble(item -> item.getPrice() * item.getQuantity())
                .sum();
        
        logger.debug("Order total calculated: {}", total);
        
        // Create order
        Order order = new Order(orderId, orderItems, total, OrderStatus.PENDING);
        
        // Set order reference in items
        orderItems.forEach(item -> item.setOrder(order));
        
        // Save order
        Order savedOrder = orderRepository.save(order);
        logger.info("Order created and saved successfully - OrderId: {}, Status: {}, Total: {}", 
            orderId, OrderStatus.PENDING, total);
        
        return convertToResponse(savedOrder);
    }
    
    public OrderResponse getOrder(String orderId) {
        logger.debug("Retrieving order - OrderId: {}", orderId);
        
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> {
                    logger.error("Order not found: {}", orderId);
                    return new RuntimeException("Order not found: " + orderId);
                });
        
        logger.debug("Order retrieved successfully - OrderId: {}, Status: {}", 
            orderId, order.getStatus());
        return convertToResponse(order);
    }
    
    public List<OrderResponse> getAllOrders() {
        logger.debug("Retrieving all orders");
        
        List<Order> orders = orderRepository.findAll();
        logger.debug("Found {} orders in database", orders.size());
        
        return orders.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }
    
    public OrderResponse updateOrderStatus(String orderId, OrderStatus status) {
        logger.info("Updating order status - OrderId: {}, New Status: {}", orderId, status);
        
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> {
                    logger.error("Order not found for status update: {}", orderId);
                    return new RuntimeException("Order not found: " + orderId);
                });
        
        OrderStatus oldStatus = order.getStatus();
        logger.debug("Status transition: {} -> {}", oldStatus, status);
        
        order.setStatus(status);
        Order updatedOrder = orderRepository.save(order);
        
        logger.info("Order status updated successfully - OrderId: {}, {} -> {}", 
            orderId, oldStatus, status);
        
        // Notify gateway about status change
        notifyGatewayStatusUpdate(orderId, status.name(), "Order status updated from " + oldStatus + " to " + status);
        
        return convertToResponse(updatedOrder);
    }
    
    /**
     * Bulk update order status for orders with specific status
     * This method finds all orders with the given status and updates them to the new status
     */
    public Map<String, Object> bulkUpdateOrderStatus(OrderStatus fromStatus, OrderStatus toStatus) {
        logger.info("Starting bulk status update - From: {}, To: {}", fromStatus, toStatus);
        
        // Validate status transition
        if (!fromStatus.canTransitionTo(toStatus)) {
            logger.error("Invalid status transition from {} to {}", fromStatus, toStatus);
            throw new IllegalArgumentException("Invalid status transition from " + fromStatus + " to " + toStatus);
        }
        
        // Get all orders with the current status
        List<Order> ordersToUpdate = orderRepository.findByStatus(fromStatus);
        logger.debug("Found {} orders with status {}", ordersToUpdate.size(), fromStatus);
        
        if (ordersToUpdate.isEmpty()) {
            logger.info("No orders found with status {}", fromStatus);
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
                
                logger.debug("Updated order {} from {} to {}", order.getOrderId(), oldStatus, toStatus);
                
                // Notify gateway about status change
                notifyGatewayStatusUpdate(order.getOrderId(), toStatus.name(), 
                    "Bulk status update from " + oldStatus + " to " + toStatus);
                
                updatedCount++;
            } catch (Exception e) {
                // Log error but continue with other orders
                logger.error("Failed to update order {} from {} to {}", 
                    order.getOrderId(), fromStatus, toStatus, e);
            }
        }
        
        logger.info("Bulk status update completed - Updated: {}/{} orders from {} to {}", 
            updatedCount, ordersToUpdate.size(), fromStatus, toStatus);
        
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
        logger.info("Starting order status progression processing");
        
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
            
            logger.debug("Processing step: {} ({} -> {})", stepName, fromStatus, toStatus);
            
            try {
                List<Order> ordersToUpdate = ordersByStatus.get(fromStatus);
                
                if (ordersToUpdate == null || ordersToUpdate.isEmpty()) {
                    logger.debug("No orders found for step {} with status {}", stepName, fromStatus);
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
                logger.debug("Found {} orders to update from {} to {}", ordersToUpdate.size(), fromStatus, toStatus);
                
                for (Order order : ordersToUpdate) {
                    if (order.getStatus().canTransitionTo(toStatus)) {
                        order.setStatus(toStatus);
                        order.setUpdatedAt(java.time.LocalDateTime.now());
                        orderRepository.save(order);
                        
                        logger.debug("Updated order {} from {} to {}", order.getOrderId(), fromStatus, toStatus);
                        
                        // Notify gateway about the status change
                        notifyGatewayStatusUpdate(
                            order.getOrderId(), 
                            toStatus.toString(), 
                            "Order status updated from " + fromStatus + " to " + toStatus
                        );
                        
                        updatedCount++;
                    } else {
                        logger.debug("Skipping order {} - invalid transition from {} to {}", 
                            order.getOrderId(), order.getStatus(), toStatus);
                    }
                }
                
                logger.info("Step {} completed - Updated: {}/{} orders from {} to {}", 
                    stepName, updatedCount, ordersToUpdate.size(), fromStatus, toStatus);
                
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
                logger.error("Step {} failed - {} -> {}", stepName, fromStatus, toStatus, e);
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("success", false);
                errorResult.put("message", fromStatus + " -> " + toStatus + " failed: " + e.getMessage());
                stepResults.put(stepName, errorResult);
            }
        }
        
        logger.info("Order status progression processing completed - Total orders updated: {}", totalUpdated);
        
        overallResult.put("totalOrdersUpdated", totalUpdated);
        overallResult.put("stepResults", stepResults);
        
        return overallResult;
    }
    
    /**
     * Notify gateway about order status changes
     */
    private void notifyGatewayStatusUpdate(String orderId, String status, String message) {
        logger.debug("Notifying gateway about status update - OrderId: {}, Status: {}", orderId, status);
        
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
            logger.debug("Gateway notification sent successfully for orderId: {}", orderId);
        } catch (Exception e) {
            // Log the error but don't fail the order update
            logger.error("Failed to notify gateway about status update for orderId: {}", orderId, e);
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
