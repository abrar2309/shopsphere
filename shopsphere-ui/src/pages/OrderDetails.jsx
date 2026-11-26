import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';

import { getOrderById } from '../api/orderApi';
import { getPayment, simulatePayment } from '../api/paymentApi';
import '../orderPages.css';

const money = (value) => new Intl.NumberFormat('en-IN', {
  style: 'currency', currency: 'INR', maximumFractionDigits: 2,
}).format(Number(value ?? 0));

function newIdempotencyKey() {
  return globalThis.crypto?.randomUUID?.()
    || `shopsphere-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

export default function OrderDetails() {
  const { orderId } = useParams();
  const navigate = useNavigate();
  const [order, setOrder] = useState(null);
  const [payment, setPayment] = useState(null);
  const [loading, setLoading] = useState(true);
  const [paymentLoading, setPaymentLoading] = useState(false);
  const [error, setError] = useState('');
  const [paymentError, setPaymentError] = useState('');
  const [notice, setNotice] = useState('');

  const loadPayment = useCallback(async () => {
    try {
      const data = await getPayment(orderId);
      setPayment(data);
    } catch (e) {
      if (e.response?.status === 404) {
        setPayment(null);
      } else {
        throw e;
      }
    }
  }, [orderId]);

  useEffect(() => {
    let active = true;
    async function load() {
      if (!sessionStorage.getItem('shopsphere_token')) {
        navigate('/login', { replace: true, state: { from: `/orders/${orderId}` } });
        return;
      }
      try {
        const data = await getOrderById(orderId);
        if (active) setOrder(data);
        try {
          const paymentData = await getPayment(orderId);
          if (active) setPayment(paymentData);
        } catch (paymentFetchError) {
          if (paymentFetchError.response?.status !== 404) throw paymentFetchError;
          if (active) setPayment(null);
        }
        if (active) setError('');
      } catch (e) {
        if (active) setError(e.response?.data?.message || e.response?.data?.detail || 'Unable to load this order.');
      } finally {
        if (active) setLoading(false);
      }
    }
    load();
    return () => { active = false; };
  }, [orderId, navigate]);

  async function handleSimulatedPayment(outcome) {
    setPaymentLoading(true);
    setPaymentError('');
    setNotice('');
    try {
      const result = await simulatePayment(orderId, outcome, newIdempotencyKey());
      setPayment(result);
      setNotice(outcome === 'SUCCESS'
        ? 'Demo payment successful. Your order is marked as PAID.'
        : 'Demo payment failed. The reserved inventory has been released.');
      const refreshedOrder = await getOrderById(orderId);
      setOrder(refreshedOrder);
    } catch (e) {
      setPaymentError(e.response?.data?.message || e.response?.data?.detail || e.message || 'Payment could not be processed.');
      try { await loadPayment(); } catch { /* retain the original error */ }
      try { setOrder(await getOrderById(orderId)); } catch { /* retain current order */ }
    } finally {
      setPaymentLoading(false);
    }
  }

  if (loading) return <main className="order-page"><section className="order-panel"><p role="status">Loading order details...</p></section></main>;

  if (error || !order) {
    return <main className="order-page"><section className="order-panel">
      <h1>Order unavailable</h1><p className="order-error">{error || 'Order not found.'}</p>
      <Link className="order-primary" to="/orders">Back to my orders</Link>
    </section></main>;
  }

  const address = order.deliveryAddress;
  const canPay = !payment && !['PAID', 'PAYMENT_FAILED', 'CANCELLED'].includes(String(order.status || '').toUpperCase());

  return (
    <main className="order-page">
      <section className="order-panel">
        <div className="order-page-top">
          <Link to="/orders" className="order-back">← My orders</Link>
          <Link to="/" className="order-back">Continue shopping</Link>
        </div>

        <p className="order-eyebrow">ORDER DETAILS</p>
        <h1>Order #{order.orderId}</h1>
        <p className="order-subtitle">Your ShopSphere purchase summary.</p>

        <dl className="order-details">
          <div><dt>Order number</dt><dd>#{order.orderId}</dd></div>
          <div><dt>Order status</dt><dd>{order.status || 'UNKNOWN'}</dd></div>
          <div><dt>Total amount</dt><dd>{money(order.totalAmount)}</dd></div>
        </dl>

        <section className="order-detail-section">
          <h2>Payment</h2>
          <p><strong>Payment status:</strong> {payment?.status || 'NOT INITIATED'}</p>
          {payment && <>
            <p><strong>Payment reference:</strong> {payment.paymentReference}</p>
            <p><strong>Payment method:</strong> {payment.paymentMethod}</p>
            <p><strong>Amount:</strong> {money(payment.amount)}</p>
            {payment.failureReason && <p className="order-error">{payment.failureReason}</p>}
          </>}
          {canPay && <>
            <p>This is a simulated payment for development and testing. No real money will be charged.</p>
            <div className="order-actions">
              <button type="button" className="order-primary" disabled={paymentLoading}
                onClick={() => handleSimulatedPayment('SUCCESS')}>
                {paymentLoading ? 'Processing...' : 'Simulate successful payment'}
              </button>
              <button type="button" className="order-secondary" disabled={paymentLoading}
                onClick={() => handleSimulatedPayment('FAILURE')}>
                Simulate payment failure
              </button>
            </div>
          </>}
          {notice && <p role="status">{notice}</p>}
          {paymentError && <p className="order-error" role="alert">{paymentError}</p>}
          {payment?.status === 'SUCCESS' && <p role="status">Payment completed successfully.</p>}
          {payment?.status === 'FAILURE' && <p role="status">Payment failed. This order cannot be paid again.</p>}
        </section>

        <section className="order-detail-section">
          <h2>Items ordered</h2>
          {order.items?.length ? <div className="order-line-items">
            {order.items.map((item, index) => <div className="order-line" key={`${item.productId}-${index}`}>
              <div><strong>Product #{item.productId}</strong>
                <span>Quantity: {item.quantity}</span>
                <span>Unit price: {money(item.price)}</span></div>
              <strong>{money(Number(item.price ?? 0) * Number(item.quantity ?? 0))}</strong>
            </div>)}
          </div> : <p>No item information is available.</p>}
        </section>

        {address && <section className="order-note">
          <h2>Delivery address</h2>
          <p><strong>{address.recipientName}</strong><br />
            {address.addressLine1}<br />
            {address.addressLine2 && <>{address.addressLine2}<br /></>}
            {address.city}, {address.state} - {address.postalCode}<br />
            {address.country}<br />Phone: {address.phoneNumber}
          </p>
        </section>}

        <div className="order-actions">
          <Link className="order-primary" to="/">Continue shopping</Link>
          <Link className="order-secondary" to="/orders">View all orders</Link>
        </div>
      </section>
    </main>
  );
}
