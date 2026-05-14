# Backend Problems — Billing & Payment Issues

> These issues were discovered while integrating the frontend with the backend API.
> They are all **backend-side** problems that need fixes in the Spring Boot application.

---

## 🔴 PROBLEM 1: Duplicate Bill Prevention Blocks Re-billing After Payment

### Symptom
After a tenant **pays** a bill (status = `PAID`) and new IoT usage is recorded, clicking
"Generate Bills Now" for the same month **does not create a new bill**. The backend's
duplicate prevention sees an existing bill for `tenant + billing_period` and skips it —
regardless of whether the old bill is PAID or UNPAID.

### Expected Behavior
- If a bill for `tenant + period` exists and is **UNPAID** → skip (don't duplicate)
- If a bill for `tenant + period` exists and is **PAID** → allow generating a new bill
  for any additional usage consumed **after** the last bill's cutoff

### Current Behavior
The bill generation service checks something like:
```java
if (billAlreadyExistsForTenantAndPeriod(tenant, start, end)) {
    skip; // ← skips even if the existing bill is PAID
}
```

### Suggested Fix
Change the duplicate check to only skip UNPAID bills:
```java
if (billRepo.existsByTenantAndBillingPeriodAndPaymentStatus(tenant, start, end, PaymentStatus.UNPAID)) {
    continue; // only skip if unpaid
}
```

You may also need a new repo method:
```java
boolean existsByUserIdAndBillingPeriodStartAndBillingPeriodEndAndPaymentStatus(
    Long userId, LocalDate start, LocalDate end, PaymentStatus status
);
```

---

## 🔴 PROBLEM 2: No Incremental Billing — New Usage After Payment is Ignored

### Symptom
Even when Problem 1 is fixed, the bill amount will be wrong because the backend
calculates `units_consumed = last_energy_reading - first_energy_reading` for the
entire month. It doesn't know where the **previous bill left off**.

### Example
1. Month starts. IoT reads energy: 0 → 100 kWh
2. Bill generated: 100 kWh × ₹8 = ₹800. Paid. ✅
3. More usage: 100 → 250 kWh (150 kWh new)
4. Generate bill again → calculates 250 - 0 = 250 kWh × ₹8 = ₹2,000 ❌
   - Should be: 250 - 100 = **150 kWh** × ₹8 = ₹1,200

### Suggested Fix
Track the energy reading at the time of billing. When generating a new bill:
```java
// Find the last PAID bill for this tenant+device
Optional<Bill> lastPaid = billRepo.findTopByUserAndRoomOrderByBillingPeriodEndDesc(tenant, room);

BigDecimal startEnergy;
if (lastPaid.isPresent()) {
    // Start from where the last bill ended
    startEnergy = lastPaid.get().getLastEnergyReading(); // NEW FIELD needed
} else {
    // First bill ever — use first reading of the period
    startEnergy = firstReadingOfPeriod.getUnitsConsumedTotal();
}

BigDecimal endEnergy = latestReading.getUnitsConsumedTotal();
BigDecimal unitsConsumed = endEnergy.subtract(startEnergy);
```

This requires adding a `last_energy_reading` column to the `bills` table to track
the meter reading at the time the bill was generated.

---

## 🟡 PROBLEM 3: Default Billing Month is Previous Month

### Symptom
`POST /api/v1/bills/generate` without a `month` parameter defaults to the **previous month**.
During testing (May 2026), all simulated IoT readings have May timestamps, but the backend
generates bills for April — resulting in tiny ₹0.64 bills from old April data.

### Current Behavior
No `month` param → bills for previous month (April when current is May)

### Impact
The frontend now sends `?month=YYYY-MM-01` explicitly (fixed on frontend side), but the
backend default should probably be the **current month** to be more intuitive.

### Suggested Fix
Change the default in the bill generation controller/service:
```java
// Instead of: default to previous month
// Default to: current month
if (month == null) {
    month = LocalDate.now().withDayOfMonth(1);
}
```

---

## 🟡 PROBLEM 4: Webhook Not Reachable in Local Development

### Symptom
After a successful Cashfree sandbox payment, the bill remains UNPAID because
Cashfree's servers cannot reach `localhost:8080` to deliver the webhook.

### Root Cause
`CashfreeService.java` has the `notify_url` commented out:
```java
// orderMeta.put("notify_url", "http://YOUR_SERVER_IP/api/v1/payment/webhook");
```

### Fix
For local development, use ngrok:
```bash
ngrok http 8080
```
Then uncomment and set:
```java
orderMeta.put("notify_url", "https://YOUR-NGROK-ID.ngrok-free.app/api/v1/payment/webhook");
```

Also fix the `return_url` — it currently points to port 3000, but Vite runs on 5173:
```java
orderMeta.put("return_url", "http://localhost:5173/transactions?order_id={order_id}");
```

---

## 🟢 PROBLEM 5: CashfreeService return_url Port Mismatch

### Symptom
After payment, Cashfree redirects to `http://localhost:3000/payment-success` which
doesn't exist. The frontend runs on port **5173** (Vite dev server).

### Fix
In `CashfreeService.java` line ~55:
```java
// Change from:
orderMeta.put("return_url", "http://localhost:3000/payment-success?order_id={order_id}");

// Change to:
orderMeta.put("return_url", "http://localhost:5173/transactions?order_id={order_id}");
```

---

## Frontend API Expectations

For reference, here's what the frontend sends and expects:

### Bill Generation
```
POST /api/v1/bills/generate?month=2026-05-01
→ Expects: { billsGenerated: N, billingMonth: "MAY 2026", bills: [...] }
```

### Create Payment Order
```
POST /api/v1/payment/create-order
Body: { "billId": 123 }
→ Expects: { "paymentSessionId": "session_xxx", "orderId": "order_xxx" }
```

### Webhook (from Cashfree)
```
POST /api/v1/payment/webhook
Body: { "data": { "order": { "order_id": "..." }, "payment": { "payment_status": "SUCCESS" } } }
→ Should: mark bill as PAID, turn on IoT device
```

### Fetch Bills
```
GET /api/v1/bills/unpaid        → landlord sees all unpaid bills
GET /api/v1/bills/my-bills      → tenant sees own bills (paid + unpaid)
GET /api/v1/bills/room/{roomId} → bills for a specific room
```
