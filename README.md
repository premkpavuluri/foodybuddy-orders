# FoodyBuddy Orders Service

Order management service for the FoodyBuddy microservices application built with Spring Boot.

## Features

- Create new orders
- Retrieve order details
- Update order status
- Order history
- H2 in-memory database for development

## Prerequisites

- Java 17+
- Gradle 8.5+

## Getting Started

### Development

1. Run the application:
   ```bash
   ./gradlew bootRun
   ```

2. The service will be available at http://localhost:8081

3. H2 Console will be available at http://localhost:8081/h2-console
   - JDBC URL: jdbc:h2:mem:ordersdb
   - Username: sa
   - Password: password

### Building

1. Build the application:
   ```bash
   ./gradlew build
   ```

2. Run the JAR file:
   ```bash
   java -jar build/libs/foodybuddy-orders-0.0.1-SNAPSHOT.jar
   ```

## API Endpoints

### Orders
- `POST /api/orders` - Create a new order
- `GET /api/orders/{orderId}` - Get order by ID
- `GET /api/orders` - Get all orders
- `PUT /api/orders/{orderId}/status?status={status}` - Update order status

### Health
- `GET /api/orders/health` - Service health check
- `GET /actuator/health` - Application health

## Order Status Values

- PENDING
- CONFIRMED
- PREPARING
- READY
- DELIVERED
- CANCELLED

## Example API Usage

### Create Order
```bash
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "items": [
      {
        "itemId": "1",
        "itemName": "Margherita Pizza",
        "quantity": 2,
        "price": 12.99
      }
    ]
  }'
```

### Get Order
```bash
curl http://localhost:8081/api/orders/{orderId}
```

### Update Order Status
```bash
curl -X PUT "http://localhost:8081/api/orders/{orderId}/status?status=CONFIRMED"
```

## Docker

### Build the Docker image:
```bash
docker build -t foodybuddy-orders .
```

### Run the container:
```bash
docker run -p 8081:8081 foodybuddy-orders
```

## Database

This service uses H2 in-memory database for development. In production, you would typically use PostgreSQL or MySQL.

## Technologies Used

- Spring Boot 3.2.0
- Spring Data JPA
- H2 Database
- Gradle 8.5
- Java 17
