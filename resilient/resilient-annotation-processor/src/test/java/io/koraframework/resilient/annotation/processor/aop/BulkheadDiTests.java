package io.koraframework.resilient.annotation.processor.aop;

import static org.junit.jupiter.api.Assertions.*;

import io.koraframework.resilient.bulkhead.Bulkhead;
import org.junit.jupiter.api.Test;

class BulkheadDiTests extends ResilientAopTestSupport {

    @Test
    void typedSpecsShareOneBudgetAcrossConsumersAndIsolateOtherSpecs() throws Throwable {
        var service = compileApp("""
                orders { maxConcurrentCalls = 1 }
                payments { maxConcurrentCalls = 2 }
                """, """
                @io.koraframework.resilient.bulkhead.annotation.BulkheadSpec("orders")
                public interface OrdersBulkhead extends io.koraframework.resilient.bulkhead.Bulkhead {}
                """, """
                @Component
                @Root
                public class TestTarget {
                    private final OrdersBulkhead orders;
                    private final OrdersBulkhead ordersAgain;
                    private final PaymentsBulkhead payments;
                    public TestTarget(OrdersBulkhead orders, OrdersBulkhead ordersAgain, PaymentsBulkhead payments) {
                        this.orders = orders;
                        this.ordersAgain = ordersAgain;
                        this.payments = payments;
                    }
                    public OrdersBulkhead orders() { return orders; }
                    public OrdersBulkhead ordersAgain() { return ordersAgain; }
                    public PaymentsBulkhead payments() { return payments; }
                    @io.koraframework.resilient.bulkhead.annotation.BulkheadSpec("payments")
                    public interface PaymentsBulkhead extends io.koraframework.resilient.bulkhead.Bulkhead {}
                }
                """);
        var orders = (Bulkhead) invokeTarget(service, "orders");
        var ordersAgain = (Bulkhead) invokeTarget(service, "ordersAgain");
        var payments = (Bulkhead) invokeTarget(service, "payments");
        assertSame(orders, ordersAgain);
        assertNotSame(orders, payments);
        assertEquals(1, orders.maxConcurrentCalls());
        assertEquals(2, payments.maxConcurrentCalls());
        try (var orderPermit = orders.acquire(); var firstPayment = payments.acquire(); var secondPayment = payments.acquire()) {
            assertNull(ordersAgain.tryAcquire());
            assertNull(payments.tryAcquire());
        }
        assertEquals(0, orders.inFlight());
        assertEquals(0, payments.inFlight());
    }
}
