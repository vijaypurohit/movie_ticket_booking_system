package com.vijaypurohit.movietickets.support;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.vijaypurohit.movietickets.payment.LocalPaymentGateway;
import com.vijaypurohit.movietickets.payment.PaymentGateway;

/**
 * Test seam over the production local adapter. It adds the two controls the deterministic adapter
 * deliberately does not expose: a gate that holds a charge in flight so a race can be driven from
 * the test thread, and a queue of scripted refund outcomes so retryable and terminal provider
 * failures can be injected. Anything not scripted is delegated to the real adapter, so tests that
 * do not opt in keep the production behaviour.
 */
public class ScriptedPaymentGateway implements PaymentGateway {
    private final LocalPaymentGateway delegate;
    private final Queue<RefundResult> scriptedRefunds = new ConcurrentLinkedQueue<>();
    private final AtomicInteger refundCalls = new AtomicInteger();
    private final AtomicInteger chargeCalls = new AtomicInteger();

    /** Consumed by the first charge that arrives, so only that one parks. */
    private volatile CountDownLatch pendingGate;
    /** Kept for the test thread to release; never cleared by the charging thread. */
    private volatile CountDownLatch releaseGate;
    private volatile CountDownLatch chargeEntered;

    public ScriptedPaymentGateway(LocalPaymentGateway delegate) { this.delegate = delegate; }

    /** Holds the next charge inside the gateway until {@link #releaseCharge()} is called. */
    public void gateNextCharge() {
        chargeEntered = new CountDownLatch(1);
        pendingGate = new CountDownLatch(1);
        releaseGate = pendingGate;
    }

    /** Blocks until a charge has actually entered the gateway and is parked. */
    public void awaitChargeEntered() throws InterruptedException {
        if (!chargeEntered.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("charge never started");
    }

    public void releaseCharge() { releaseGate.countDown(); }

    /** Queues refund outcomes, consumed in order; once exhausted the real adapter answers. */
    public void scriptRefunds(RefundResult... results) { scriptedRefunds.addAll(java.util.List.of(results)); }

    public static RefundResult retryableFailure() { return new RefundResult(false, true, "refund_retryable"); }
    public static RefundResult terminalFailure() { return new RefundResult(false, false, "refund_terminal"); }

    public int refundCalls() { return refundCalls.get(); }
    public int chargeCalls() { return chargeCalls.get(); }

    public void reset() {
        scriptedRefunds.clear(); refundCalls.set(0); chargeCalls.set(0);
        pendingGate = null; releaseGate = null; chargeEntered = null;
    }

    @Override
    public ChargeResult charge(String idempotencyKey, BigDecimal amount, String paymentToken) {
        chargeCalls.incrementAndGet();
        CountDownLatch gate = pendingGate;
        if (gate != null) {
            pendingGate = null;
            chargeEntered.countDown();
            try {
                if (!gate.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("charge gate never released");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        }
        return delegate.charge(idempotencyKey, amount, paymentToken);
    }

    @Override
    public Optional<ChargeResult> findChargeResult(String idempotencyKey) {
        return delegate.findChargeResult(idempotencyKey);
    }

    @Override
    public RefundResult refund(String idempotencyKey, BigDecimal amount) {
        refundCalls.incrementAndGet();
        RefundResult scripted = scriptedRefunds.poll();
        return scripted != null ? scripted : delegate.refund(idempotencyKey, amount);
    }
}
