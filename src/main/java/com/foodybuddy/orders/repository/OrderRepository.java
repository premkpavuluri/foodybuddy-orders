package com.foodybuddy.orders.repository;

import com.foodybuddy.orders.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByOrderId(String orderId);
    List<Order> findByStatus(com.foodybuddy.orders.entity.OrderStatus status);
}
