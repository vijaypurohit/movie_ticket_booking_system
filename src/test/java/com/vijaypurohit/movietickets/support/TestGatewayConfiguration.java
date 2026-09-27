package com.vijaypurohit.movietickets.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.vijaypurohit.movietickets.payment.LocalPaymentGateway;

/**
 * Imported only by tests that need to drive a charge or inject refund provider failures; every
 * other suite keeps the production adapter.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestGatewayConfiguration {
    @Bean
    @Primary
    ScriptedPaymentGateway scriptedPaymentGateway(LocalPaymentGateway delegate) {
        return new ScriptedPaymentGateway(delegate);
    }
}
