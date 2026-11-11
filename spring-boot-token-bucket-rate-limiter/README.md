# Spring Boot token bucket rate limiter

Java port of the ideas in [fastapi-redis-rate-limiter](https://github.com/iunary/fastapi-redis-rate-limiter). The Python middleware counts requests in Redis with a fixed window (`INCR` + `EXPIRE`) and returns **429** when a client exceeds `limit` inside `window`. This API keeps that contract — per-client limit, HTTP 429, same default of 40 requests per 60 seconds — and replaces Redis with an in-memory **token bucket** stored in a `ConcurrentHashMap`.

No Redis, database, or other external server is required.

## Layout

| Layer | Class | Role |
| --- | --- | --- |
| Filter | `RateLimitFilter` | Same place in the pipeline as `RedisRateLimiterMiddleware`. Keys the client by remote address and spends one token. |
| Service | `TokenBucketRateLimitService` | Holds `ConcurrentHashMap<String, TokenBucket>` and applies the algorithm. |
| Model | `TokenBucket` | Capacity, smooth refill, thread-safe `tryConsume`. |
| Controller | `DemoController` | Protected REST endpoints. |
| DTO | `MessageResponse`, `EchoRequest`, `EchoResponse`, `ItemResponse`, `ErrorResponse` | Request and response bodies. |
| Errors | `GlobalExceptionHandler` | Maps `RateLimitExceededException` to **429 Too Many Requests**, plus 400 and 404. |

## Algorithm

Each client starts with `capacity` tokens. A request spends one token. Tokens refill continuously:

`tokensAdded = elapsed / refillPeriod * refillTokens`

The bucket never grows past `capacity`. With the defaults (`capacity=40`, `refill-tokens=40`, `refill-period-seconds=60`) a client can burst 40 requests, then recovers at 40 tokens per minute. That lines up with `limit=40` and `window=60` in the Python sample.

## Run

Java 17 or newer and Maven are required. The JDK on a default `java` install may be 8; point `JAVA_HOME` at 17+.

```shell
cd spring-boot-token-bucket-rate-limiter
mvn spring-boot:run
```

```shell
curl -i http://localhost:8080/api/limited
curl -i http://localhost:8080/api/hello
curl -i http://localhost:8080/api/items
curl -i http://localhost:8080/api/items/1
curl -i -H "Content-Type: application/json" -d "{\"text\":\"ping\"}" http://localhost:8080/api/echo
curl -i http://localhost:8080/api/health
```

Successful responses include `X-RateLimit-Limit` and `X-RateLimit-Remaining`. The 41st call inside a drained bucket returns:

```json
{
  "status": 429,
  "error": "Too Many Requests",
  "message": "Rate limit exceeded. Try again later.",
  "retryAfterSeconds": 2
}
```

`Retry-After` is set on that response. `/api/health` is excluded and does not spend tokens.

## Configuration

`src/main/resources/application.yml`:

```yaml
rate-limit:
  capacity: 40
  refill-tokens: 40
  refill-period-seconds: 60
  include-path-patterns:
    - /api/**
  exclude-path-patterns:
    - /api/health
```

## Tests

```shell
mvn test
```

Unit tests cover refill math, per-client isolation, and a concurrent burst that must not exceed capacity. The MockMvc test checks 200, 429, the health exclusion, validation (400), and a missing item (404).
