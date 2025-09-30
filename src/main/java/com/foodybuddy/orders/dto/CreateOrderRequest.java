package com.foodybuddy.orders.dto;

import java.util.List;

public class CreateOrderRequest {
    private String userId;
    private List<OrderItemRequest> items;
    private Double totalAmount;
    
    public CreateOrderRequest() {}
    
    public CreateOrderRequest(String userId, List<OrderItemRequest> items, Double totalAmount) {
        this.userId = userId;
        this.items = items;
        this.totalAmount = totalAmount;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public List<OrderItemRequest> getItems() {
        return items;
    }
    
    public void setItems(List<OrderItemRequest> items) {
        this.items = items;
    }
    
    public Double getTotalAmount() {
        return totalAmount;
    }
    
    public void setTotalAmount(Double totalAmount) {
        this.totalAmount = totalAmount;
    }
    
    public static class OrderItemRequest {
        private String itemId;
        private String itemName;
        private Integer quantity;
        private Double price;
        
        public OrderItemRequest() {}
        
        public OrderItemRequest(String itemId, String itemName, Integer quantity, Double price) {
            this.itemId = itemId;
            this.itemName = itemName;
            this.quantity = quantity;
            this.price = price;
        }
        
        public String getItemId() {
            return itemId;
        }
        
        public void setItemId(String itemId) {
            this.itemId = itemId;
        }
        
        public String getItemName() {
            return itemName;
        }
        
        public void setItemName(String itemName) {
            this.itemName = itemName;
        }
        
        public Integer getQuantity() {
            return quantity;
        }
        
        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }
        
        public Double getPrice() {
            return price;
        }
        
        public void setPrice(Double price) {
            this.price = price;
        }
    }
}
