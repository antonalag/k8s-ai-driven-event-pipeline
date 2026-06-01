package com.k8s.pipeline.collector;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "platform.messaging.type=kafka",
    "spring.kafka.bootstrap-servers=localhost:9092"
})
class K8sCollectorApplicationTests {

    @Test
    void contextLoads() {
        // Validates that the Spring application context starts without errors.
    }
}
