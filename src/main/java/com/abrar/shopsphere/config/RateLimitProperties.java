package com.abrar.shopsphere.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "shopsphere.rate-limit")
public class RateLimitProperties {

    private Policy login = new Policy();
    private Policy productCreate = new Policy();
    private Policy orderCreate = new Policy();

    public Policy getLogin() {
        return login;
    }

    public void setLogin(Policy login) {
        this.login = login;
    }

    public Policy getProductCreate() {
        return productCreate;
    }

    public void setProductCreate(Policy productCreate) {
        this.productCreate = productCreate;
    }

    public Policy getOrderCreate() {
        return orderCreate;
    }

    public void setOrderCreate(Policy orderCreate) {
        this.orderCreate = orderCreate;
    }

    public static class Policy {

        private int limit;
        private long windowSeconds;

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }

        public long getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(long windowSeconds) {
            this.windowSeconds = windowSeconds;
        }
    }
}