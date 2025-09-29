package com.foodybuddy.orders.controller;

import com.foodybuddy.orders.dto.CreateOrderRequest;
import com.foodybuddy.orders.dto.OrderResponse;
import com.foodybuddy.orders.entity.Order;
import com.foodybuddy.orders.entity.OrderStatus;
import com.foodybuddy.orders.service.OrderService;
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
    
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }
    
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@RequestBody CreateOrderRequest request) {
        try {
            OrderResponse order = orderService.createOrder(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(order);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
    
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable String orderId) {
        try {
            OrderResponse order = orderService.getOrder(orderId);
            return ResponseEntity.ok(order);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    @GetMapping
    public ResponseEntity<List<OrderResponse>> getAllOrders() {
        List<OrderResponse> orders = orderService.getAllOrders();
        return ResponseEntity.ok(orders);
    }
    
    @PutMapping("/{orderId}/status")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable String orderId, 
            @RequestParam OrderStatus status) {
        try {
            OrderResponse order = orderService.updateOrderStatus(orderId, status);
            return ResponseEntity.ok(order);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    @GetMapping("/health")
    public ResponseEntity<String> health() {
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
        try {
            Map<String, Object> result = orderService.processAllStatusProgressions();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
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
        try {
            OrderResponse order = orderService.getOrder(orderId);
            
            // Simulate status progression
            switch (order.getStatus()) {
                case PENDING:
                    order = orderService.updateOrderStatus(orderId, OrderStatus.CONFIRMED);
                    break;
                case CONFIRMED:
                    order = orderService.updateOrderStatus(orderId, OrderStatus.PREPARING);
                    break;
                case PREPARING:
                    order = orderService.updateOrderStatus(orderId, OrderStatus.READY);
                    break;
                case READY:
                    order = orderService.updateOrderStatus(orderId, OrderStatus.OUT_FOR_DELIVERY);
                    break;
                case OUT_FOR_DELIVERY:
                    order = orderService.updateOrderStatus(orderId, OrderStatus.DELIVERED);
                    break;
                default:
                    return ResponseEntity.badRequest().build();
            }
            
            return ResponseEntity.ok(order);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
