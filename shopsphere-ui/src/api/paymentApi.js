
import apiClient from './client';

export async function simulatePayment(orderId, outcome, idempotencyKey) {
  if (!orderId) throw new Error('Order ID is required.');

  if (!['SUCCESS', 'FAILURE'].includes(outcome)) {
    throw new Error('Payment outcome must be SUCCESS or FAILURE.');
  }

  if (!idempotencyKey) {
    throw new Error('Idempotency key is required.');
  }

  const response = await apiClient.post(
      `/orders/${encodeURIComponent(orderId)}/payment/simulate`,
      { outcome, idempotencyKey }
  );

  return response.data;
}

export async function getPayment(orderId) {
  if (!orderId) throw new Error('Order ID is required.');

  const response = await apiClient.get(
      `/orders/${encodeURIComponent(orderId)}/payment`
  );

  return response.data;
}

export async function createManualPayment(
    orderId,
    paymentMethod,
    upiTransactionId,
    idempotencyKey
) {
  if (!orderId) throw new Error('Order ID is required.');

  if (!['UPI', 'COD'].includes(paymentMethod)) {
    throw new Error('Payment method must be UPI or COD.');
  }

  if (!idempotencyKey) {
    throw new Error('Idempotency key is required.');
  }

  const response = await apiClient.post(
      `/orders/${encodeURIComponent(orderId)}/payment/manual`,
      {
        paymentMethod,
        upiTransactionId:
            paymentMethod === 'UPI' ? upiTransactionId?.trim() : null,
        idempotencyKey,
      }
  );

  return response.data;
}

export async function getPendingPayments() {
  const response = await apiClient.get('/admin/payments/pending');

  return response.data;
}

export async function verifyPayment(paymentId, decision, note) {
  if (!paymentId) throw new Error('Payment ID is required.');

  if (!['SUCCESS', 'FAILURE'].includes(decision)) {
    throw new Error('Decision must be SUCCESS or FAILURE.');
  }

  const response = await apiClient.post(
      `/admin/payments/${encodeURIComponent(paymentId)}/verify`,
      {
        decision,
        note: note?.trim() || '',
      }
  );

  return response.data;
}
