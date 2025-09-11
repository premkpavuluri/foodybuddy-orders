package com.foodybuddy.orders.entity;

/**
 * Order status enumeration for the Orders service
 * Represents the lifecycle of an order in the system
 */
public enum OrderStatus {
    PENDING("Order has been created and is waiting for confirmation"),
    CONFIRMED("Order has been confirmed and payment processed"),
    PREPARING("Order is being prepared in the kitchen"),
    READY("Order is ready for pickup/delivery"),
    OUT_FOR_DELIVERY("Order is out for delivery"),
    DELIVERED("Order has been delivered successfully"),
    CANCELLED("Order has been cancelled");

    private final String description;

    OrderStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Check if the order can transition to the given status
     */
    public boolean canTransitionTo(OrderStatus newStatus) {
        return switch (this) {
            case PENDING -> newStatus == CONFIRMED || newStatus == CANCELLED;
            case CONFIRMED -> newStatus == PREPARING || newStatus == CANCELLED;
            case PREPARING -> newStatus == READY || newStatus == CANCELLED;
            case READY -> newStatus == OUT_FOR_DELIVERY || newStatus == CANCELLED;
            case OUT_FOR_DELIVERY -> newStatus == DELIVERED || newStatus == CANCELLED;
            case DELIVERED, CANCELLED -> false;
        };
    }
}
