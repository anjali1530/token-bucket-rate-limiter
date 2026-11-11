package com.ratelimiter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "rate-limit.capacity=3",
        "rate-limit.refill-tokens=1",
        "rate-limit.refill-period-seconds=3600"
})
class RateLimitApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returns429AfterCapacityIsSpent() throws Exception {
        String client = "203.0.113.10";
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/limited").with(remoteAddr(client)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("This is a protected endpoint."))
                    .andExpect(jsonPath("$.limit").value(3))
                    .andExpect(jsonPath("$.remaining").value(2 - i))
                    .andExpect(header().string("X-RateLimit-Limit", "3"))
                    .andExpect(header().string("X-RateLimit-Remaining", String.valueOf(2 - i)));
        }

        mockMvc.perform(get("/api/hello").with(remoteAddr(client)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.message").value("Rate limit exceeded. Try again later."))
                .andExpect(jsonPath("$.path").value("/api/hello"))
                .andExpect(jsonPath("$.limit").value(3))
                .andExpect(jsonPath("$.remaining").value(0))
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"));
    }

    @Test
    void healthIsNotRateLimitedAndClientsAreIndependent() throws Exception {
        String exhausted = "203.0.113.20";
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/items").with(remoteAddr(exhausted)))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/items").with(remoteAddr(exhausted)))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(get("/api/health").with(remoteAddr(exhausted)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/api/hello").with(remoteAddr("203.0.113.21")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Hello, world!"))
                .andExpect(jsonPath("$.remaining").value(2));
    }

    @Test
    void validatesEchoAndLooksUpItems() throws Exception {
        String client = "203.0.113.30";

        mockMvc.perform(post("/api/echo")
                        .with(remoteAddr(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"ping\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("ping"));

        mockMvc.perform(post("/api/echo")
                        .with(remoteAddr(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("text: must not be blank"));

        String lookupClient = "203.0.113.31";
        mockMvc.perform(get("/api/items/1").with(remoteAddr(lookupClient)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alpha"));

        mockMvc.perform(get("/api/items/99").with(remoteAddr(lookupClient)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Item 99 was not found"));
    }

    private static RequestPostProcessor remoteAddr(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
