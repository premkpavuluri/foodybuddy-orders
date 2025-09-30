package com.foodybuddy.orders.controller;

import com.foodybuddy.orders.dto.CreateOrderRequest;
import com.foodybuddy.orders.dto.OrderResponse;
import com.foodybuddy.orders.entity.Order;
import com.foodybuddy.orders.entity.OrderStatus;
import com.foodybuddy.orders.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "http://localhost:3000")
public class OrderController {
    
    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
        logger.info("OrderController initialized with order service");
    }
    
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@RequestBody CreateOrderRequest request) {
        logger.info("Creating new order for user: {}, items count: {}, total: {}", 
            request.getUserId(), 
            request.getItems() != null ? request.getItems().size() : 0,
            request.getTotalAmount());
        
        try {
            OrderResponse order = orderService.createOrder(request);
            logger.info("Order created successfully - OrderId: {}, Status: {}, Total: {}", 
                order.getOrderId(), order.getStatus(), order.getTotal());
            return ResponseEntity.status(HttpStatus.CREATED).body(order);
        } catch (Exception e) {
            logger.error("Failed to create order for user: {}", request.getUserId(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
    
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable String orderId) {
        logger.info("Fetching order details for orderId: {}", orderId);
        
        try {
            OrderResponse order = orderService.getOrder(orderId);
            logger.info("Order retrieved successfully - OrderId: {}, Status: {}", 
                order.getOrderId(), order.getStatus());
            return ResponseEntity.ok(order);
        } catch (RuntimeException e) {
            logger.error("Order not found: {}", orderId, e);
            return ResponseEntity.notFound().build();
        }
    }
    
    @GetMapping
    public ResponseEntity<List<OrderResponse>> getAllOrders() {
        logger.info("Fetching all orders");
        
        List<OrderResponse> orders = orderService.getAllOrders();
        logger.info("Retrieved {} orders successfully", orders.size());
        return ResponseEntity.ok(orders);
    }
    
    @PutMapping("/{orderId}/status")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable String orderId, 
            @RequestParam OrderStatus status) {
        logger.info("Updating order status - OrderId: {}, New Status: {}", orderId, status);
        
        try {
            OrderResponse order = orderService.updateOrderStatus(orderId, status);
            logger.info("Order status updated successfully - OrderId: {}, Status: {}", 
                order.getOrderId(), order.getStatus());
            return ResponseEntity.ok(order);
        } catch (RuntimeException e) {
            logger.error("Failed to update order status - OrderId: {}", orderId, e);
            return ResponseEntity.notFound().build();
        }
    }
    
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        logger.debug("Health check endpoint called");
        return ResponseEntity.ok("Orders service is healthy");
    }
    
    /**
     * Bulk update order status - processes all status progressions automatically
     * 
     * This endpoint:
     * 1. Gets all orders with target statuses (CONFIRMED, PREPARING, READY, OUT_FOR_DELIVERY)
     * 2. Creates a map of orders by their current status
     * 3. Updates each set of orders to their desired destination status
     */
    @PostMapping("/bulk-status-update")
    public ResponseEntity<Map<String, Object>> bulkUpdateOrderStatus() {
        logger.info("Processing bulk order status update");
        
        try {
            Map<String, Object> result = orderService.processAllStatusProgressions();
            logger.info("Bulk status update completed successfully - Total orders updated: {}", 
                result.get("totalOrdersUpdated"));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Bulk status update failed", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Bulk status update failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    

    /**
     * Simulate order status progression for testing
     * This endpoint simulates the order lifecycle
     */
    @PostMapping("/{orderId}/simulate-progression")
    public ResponseEntity<OrderResponse> simulateOrderProgression(@PathVariable String orderId) {
        logger.info("Simulating order progression for orderId: {}", orderId);
        
        try {
            OrderResponse order = orderService.getOrder(orderId);
            logger.debug("Current order status: {}", order.getStatus());
            
            // Simulate status progression
            switch (order.getStatus()) {
                case PENDING:
                    logger.debug("Progression: PENDING -> CONFIRMED");
                    order = orderService.updateOrderStatus(orderId, OrderStatus.CONFIRMED);
                    break;
                case CONFIRMED:
                    logger.debug("Progression: CONFIRMED -> PREPARING");
                    order = orderService.updateOrderStatus(orderId, OrderStatus.PREPARING);
                    break;
                case PREPARING:
                    logger.debug("Progression: PREPARING -> READY");
                    order = orderService.updateOrderStatus(orderId, OrderStatus.READY);
                    break;
                case READY:
                    logger.debug("Progression: READY -> OUT_FOR_DELIVERY");
                    order = orderService.updateOrderStatus(orderId, OrderStatus.OUT_FOR_DELIVERY);
                    break;
                case OUT_FOR_DELIVERY:
                    logger.debug("Progression: OUT_FOR_DELIVERY -> DELIVERED");
                    order = orderService.updateOrderStatus(orderId, OrderStatus.DELIVERED);
                    break;
                default:
                    logger.warn("Cannot progress order from status: {}", order.getStatus());
                    return ResponseEntity.badRequest().build();
            }
            
            logger.info("Order progression completed - OrderId: {}, New Status: {}", 
                order.getOrderId(), order.getStatus());
            return ResponseEntity.ok(order);
        } catch (RuntimeException e) {
            logger.error("Failed to simulate order progression - OrderId: {}", orderId, e);
            return ResponseEntity.notFound().build();
        }
    }
}
