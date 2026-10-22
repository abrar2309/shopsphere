
package com.abrar.shopsphere;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;

@SpringBootTest
@ActiveProfiles("test")
@Import(ShopSphereTestSchedulingConfig.class)
class ShopifyApplicationTests {

    @Test
    void contextLoads() {
    }

}
