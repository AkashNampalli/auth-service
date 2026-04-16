# ShopTalk — Session 03 Notes
**Date:** 16 April 2026
**Duration:** Full day session
**Topic:** Auth Service — JWT, Redis, Spring Security, Token Management

---

## What we covered today

### 1. Why session-based auth breaks in microservices

Sessions are stateful — the server stores session data in memory. In microservices:

```
User logs in → Auth Service stores session in its own memory
User places order → Order Service checks session → not in ITS memory → rejected
```

To share sessions across services you need a shared session store — adding network latency
on every request and creating a single point of failure.

**JWT solves this** — all user info lives inside the token itself. Any service verifies it
independently using a shared secret. No network call needed. Stateless.

---

### 2. JWT deep dive

A JWT has three parts separated by dots:

```
header.payload.signature
```

**Header** — algorithm used:
```json
{"alg": "HS256", "typ": "JWT"}
```

**Payload (claims)** — user data:
```json
{
    "sub": "9ce9efa5-...",
    "email": "akash@shoptalk.com",
    "role": "BUYER",
    "exp": 1713012800,
    "iat": 1713011900
}
```

**Signature** — cryptographic proof:
```
HMACSHA256(base64(header) + "." + base64(payload), secretKey)
```

**Why JWT cannot be forged:**
The signature is generated with a secret key only your server knows. A hacker can read
the payload (it's Base64) and even modify it — but regenerating the signature requires
the secret key they don't have. Server recalculates signature on every request and
rejects any mismatch.

**One-line answer:** "Without the secret key, a modified payload produces a different
signature that the server immediately rejects."

---

### 3. Access token vs refresh token

```
Access Token
├── Contains: userId, email, role
├── Expiry: 15 minutes (900,000ms)
├── Used: sent with every API request
└── Short-lived — limits damage if stolen to 15 minutes

Refresh Token
├── Contains: userId only
├── Expiry: 7 days (604,800,000ms)
├── Used: only to get a new access token
└── Stored in Redis — can be invalidated on logout
```

**Why short access token expiry:**
JWTs cannot be invalidated before expiry. If stolen, attacker has access until expiry.
15 minutes limits damage window vs 7 days.

**Why refresh token in Redis:**
Allows true logout — deleting from Redis means the refresh token can never be used
again, even if someone stole it.

---

### 4. Project setup

```
Artifact    : auth-service
Package     : com.shoptalk.authservice
Port        : 8082
Database    : authservice_db (PostgreSQL)
Cache       : Redis (port 6379)
```

**Dependencies:**
- spring-boot-starter-web
- spring-boot-starter-data-jpa
- postgresql
- spring-boot-starter-security
- spring-boot-starter-validation
- spring-boot-devtools
- lombok (1.18.36)
- spring-boot-starter-data-redis
- jjwt-api, jjwt-impl, jjwt-jackson (0.12.6)

**Docker commands:**
```bash
# Start Redis
docker run --name shoptalk-redis -p 6379:6379 -d redis:7

# Create auth database
docker exec -it shoptalk-postgres psql -U shoptalk -d userservice_db \
  -c "CREATE DATABASE authservice_db;"

# Verify both running
docker ps
```

---

### 5. application.yml

```yaml
spring:
  application:
    name: auth-service
  datasource:
    url: jdbc:postgresql://localhost:5432/authservice_db
    username: shoptalk
    password: shoptalk123
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    hibernate:
      ddl-auto: create-drop
    show-sql: true
    properties:
      hibernate:
        format_sql: true
  data:
    redis:
      host: localhost
      port: 6379

server:
  port: 8082

jwt:
  secret: 404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970
  access-token-expiry: 900000
  refresh-token-expiry: 604800000
```

**Why `database-platform` needed:**
When both Spring Data JPA and Spring Data Redis are on the classpath, Spring gets
confused about which datasource is for JPA. Explicitly setting `database-platform`
tells Hibernate which dialect to use without needing a live connection to figure it out.

---

### 6. Package structure

```
com.shoptalk.authservice/
├── config/
│   └── SecurityConfig.java
├── controller/
│   └── AuthController.java
├── dto/
│   ├── AuthResponse.java
│   ├── ErrorResponse.java
│   ├── LoginRequest.java
│   ├── RefreshTokenRequest.java
│   └── RegisterRequest.java
├── entity/
│   ├── User.java
│   └── UserRole.java
├── exception/
│   ├── DuplicateEmailException.java
│   ├── GlobalExceptionHandler.java
│   ├── InvalidCredentialException.java
│   ├── InvalidTokenException.java
│   └── UserNotFoundException.java
├── repository/
│   └── UserRepository.java
├── security/
│   └── JwtService.java
└── service/
    └── AuthService.java
```

---

### 7. JwtService.java

```java
package com.shoptalk.authservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiry}")
    private long accessTokenExpiry;

    @Value("${jwt.refresh-token-expiry}")
    private long refreshTokenExpiry;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(String userId, String role, String email) {
        return Jwts.builder()
                .subject(userId)
                .claim("role", role)
                .claim("email", email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiry))
                .signWith(secretKey)
                .compact();
    }

    public String generateRefreshToken(String userId) {
        return Jwts.builder()
                .subject(userId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiry))
                .signWith(secretKey)
                .compact();
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractClaims(token);
            return claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }
}
```

**Key concepts:**

`@PostConstruct` — runs after all `@Value` injections are complete. Safe to use
injected values here. Constructor runs before injection — `secret` would be null there.

`isTokenValid` returns `false` on any exception — tampered, expired, malformed.
Never rethrows. Callers get a clean boolean answer.

All methods `public` — `AuthService` needs to call them. Private methods cannot be
injected or called from other beans.

---

### 8. AuthService.java

```java
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RedisTemplate<String, String> redisTemplate;

    // constructor injection...

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new DuplicateEmailException(request.getEmail());
        }
        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .build();
        User savedUser = userRepository.save(user);
        String userId = savedUser.getId().toString();
        String accessToken = jwtService.generateAccessToken(
                userId, savedUser.getRole().name(), savedUser.getEmail());
        String refreshToken = jwtService.generateRefreshToken(userId);
        redisTemplate.opsForValue().set("refresh:" + userId, refreshToken, 7, TimeUnit.DAYS);
        return AuthResponse.builder()
                .accessToken(accessToken).refreshToken(refreshToken)
                .userId(userId).role(savedUser.getRole().name()).build();
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() ->
                    new UserNotFoundException("No account found: " + request.getEmail()));
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialException("Invalid email or password");
        }
        String userId = user.getId().toString();
        String accessToken = jwtService.generateAccessToken(
                userId, user.getRole().name(), user.getEmail());
        String refreshToken = jwtService.generateRefreshToken(userId);
        redisTemplate.opsForValue().set("refresh:" + userId, refreshToken, 7, TimeUnit.DAYS);
        return AuthResponse.builder()
                .accessToken(accessToken).refreshToken(refreshToken)
                .userId(userId).role(user.getRole().name()).build();
    }

    public AuthResponse refresh(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();
        if (!jwtService.isTokenValid(refreshToken)) {
            throw new InvalidTokenException("Refresh token is invalid or expired");
        }
        String userId = jwtService.extractClaims(refreshToken).getSubject();
        String storedToken = redisTemplate.opsForValue().get("refresh:" + userId);
        if (storedToken == null || !storedToken.equals(refreshToken)) {
            throw new InvalidTokenException("Refresh token not recognised");
        }
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        String newAccessToken = jwtService.generateAccessToken(
                userId, user.getRole().name(), user.getEmail());
        return AuthResponse.builder()
                .accessToken(newAccessToken).refreshToken(refreshToken)
                .userId(userId).role(user.getRole().name()).build();
    }

    public void logout(String token) {
        String userId = jwtService.extractClaims(token).getSubject();
        redisTemplate.delete("refresh:" + userId);
    }
}
```

---

### 9. AuthController.java

```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7); // removes "Bearer "
        authService.logout(token);
        return ResponseEntity.noContent().build();
    }
}
```

**Logout design:**
- Receives token from `Authorization` header not request body
- `authHeader.substring(7)` strips `"Bearer "` prefix
- Returns `204 No Content` — success with no body

---

### 10. Redis operations used

```java
// Store with expiry
redisTemplate.opsForValue().set("refresh:" + userId, refreshToken, 7, TimeUnit.DAYS);

// Retrieve
String stored = redisTemplate.opsForValue().get("refresh:" + userId);

// Delete (logout)
redisTemplate.delete("refresh:" + userId);
```

**Key naming pattern:** `"refresh:" + userId`
Prefix prevents collisions when multiple key types exist in Redis.
Later: `"blacklist:" + tokenId` for revoked access tokens.

**Why Redis for refresh tokens:**
- O(1) lookup — constant time regardless of how many tokens stored
- Built-in TTL — Redis auto-deletes expired tokens, no cleanup job needed
- Distributed — all service instances share the same Redis, no sync issues

---

### 11. Bugs found and fixed

| Bug | Cause | Fix |
|---|---|---|
| Wrong password returned 200 | `new InvalidCredentialException()` without `throw` | Added `throw` keyword |
| Refresh token always invalid | Redis key had extra space `"refresh: "` vs `"refresh:"` | Removed space from login method |
| URL 400 error | Postman copied URL with newline `%0A` at end | Retyped URL manually |
| JSON parse error | Postman body set to Text not JSON | Changed dropdown to JSON in Postman |
| `JwtService bean not found` | DevTools mid-restart, resolved on full restart | Waited for clean restart |
| Hibernate dialect error | JPA + Redis both on classpath confused Spring | Added `database-platform` to yml |

---

### 12. Live test results

| Endpoint | Status | Notes |
|---|---|---|
| POST /api/auth/register | 201 Created | Returns accessToken + refreshToken |
| POST /api/auth/login | 200 OK | Validates BCrypt password |
| POST /api/auth/login (wrong pwd) | 400 Bad Request | InvalidCredentialException caught |
| POST /api/auth/refresh | 200 OK | Returns new accessToken |
| POST /api/auth/logout | 204 No Content | Deletes refresh token from Redis |

---

### 13. Key concepts to remember

| Concept | One-line summary |
|---|---|
| JWT stateless | All user info inside token — no server storage needed |
| Signature | HMAC(header+payload, secretKey) — cannot forge without secret |
| Access token | Short-lived (15 min) — limits damage if stolen |
| Refresh token | Long-lived (7 days) — stored in Redis, deleted on logout |
| `@PostConstruct` | Runs after `@Value` injection — safe to use injected values |
| `passwordEncoder.matches()` | Hashes input and compares — never decrypts BCrypt |
| Redis TTL | Auto-expires keys — no manual cleanup needed |
| `substring(7)` | Strips `"Bearer "` from Authorization header |
| `throw` keyword | Exception must be thrown — just `new` creates but does nothing |

---

## What's coming in Session 04

```
├── React project setup — shoptalk-ui
├── Login page connected to Auth Service
├── Register page connected to Auth Service
├── JWT token storage in React (memory, not localStorage)
├── Protected routes — redirect to login if no token
└── Visual confirmation of full auth flow in browser
```

---

*ShopTalk Learning Journal — Session 03 complete*
