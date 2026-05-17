# Azar Cafetero - Parqués Game Engine

Welcome to the **Parqués Game Service** repository for Azar Cafetero. This Spring Boot microservice is the authoritative, server-side engine for the classic Colombian board game *Parqués*. It handles all the complex board topology, movement validations, and interaction rules required to ensure a fair and synchronous multiplayer experience.

## 🚀 Technology Stack

- **[Java & Spring Boot](https://spring.io/projects/spring-boot)**: The foundation for executing the state-heavy logic required by a complex board game.
- **[Maven](https://maven.apache.org/)**: Build lifecycle management.
- **[Docker](https://www.docker.com/)**: Containerized deployment for scalable, isolated instances.
- **SonarQube**: Ensures high code quality and test coverage.

## 🛠️ Architecture & Responsibilities

The frontend UI is strictly a presentation layer; this service dictates all actual game reality.

### 1. Board Topology & Position Management
- **Coordinate System**: Maps the complex Parqués board, tracking both "absolute positions" (global coordinates on the 96-square track) and "relative positions" (player-specific paths into the final stretch/heaven).
- **Safe Zones (Seguros)**: Identifies specific squares that protect pieces from capture.

### 2. Core Game Rules & Mechanics
- **Secure Dice Rolling**: Generates random dice rolls server-side to prevent client manipulation or cheating.
- **Movement Validation**: Calculates valid moves based on the dice rolled, ensuring players do not move more squares than allowed or jump over blockades illegally.
- **Capturing (Eating) & Jail**: Enforces the rules of capturing opponent pieces when landing on the same square (outside of safe zones), sending the captured piece back to jail, and awarding the captor an extra turn or bonus movement.
- **Jail Exit Logic**: Manages the strict rules for escaping jail, including automated release mechanics when players roll doubles while all pieces are jailed, and providing choices between moving active pieces or exiting jail.

### 3. Event Publishing
- As the game state mutates (e.g., after a valid move), this engine constructs a comprehensive state payload and publishes it back to the WebSocket Gateway to be broadcasted to the connected players.

## 🏃‍♂️ Getting Started

### Prerequisites
- Java 17+ (JDK)
- Maven 3.8+

### Running Locally

```bash
./mvnw spring-boot:run
```

### Docker Deployment

```bash
docker build -t azarcafetero-parques .
docker run -p 8084:8084 azarcafetero-parques
```

## 🧪 Testing & Debugging

The service contains critical logic for piece movement, absolute-to-relative coordinate translation, and complex interactions that must be heavily tested. 

Run the test suite:
```bash
./mvnw test
```

**Debug Mode**: The service supports a local development "Debug Mode" (often triggered via frontend shortcuts like `Shift + D`) which bypasses certain validations or forces specific dice rolls to easily test edge-case board states (e.g., testing the final stretch).
