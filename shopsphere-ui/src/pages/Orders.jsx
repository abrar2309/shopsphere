import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { getMyOrders } from '../api/orderApi';
import '../orderPages.css';

const money = (value) => new Intl.NumberFormat('en-IN', {
  style: 'currency', currency: 'INR', maximumFractionDigits: 2,
}).format(Number(value ?? 0));

export default function Orders() {
  const navigate = useNavigate();
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    async function load() {
      if (!sessionStorage.getItem('shopsphere_token')) {
        navigate('/login', { replace: true, state: { from: '/orders' } });
        return;
      }
      try {
        const data = await getMyOrders();
        if (active) setOrders(Array.isArray(data) ? data : []);
      } catch (e) {
        if (active) setError(e.response?.data?.message || 'Could not load order history. Please check your session and try again.');
      } finally {
        if (active) setLoading(false);
      }
    }
    load();
    return () => { active = false; };
  }, [navigate]);

  return <main className="order-page"><section className="order-panel order-history-panel">
    <div className="order-page-top"><Link to="/" className="order-back">← Back to ShopSphere</Link><Link to="/checkout" className="order-back">Checkout</Link></div>
    <p className="order-eyebrow">YOUR ACCOUNT</p><h1>My orders</h1><p className="order-subtitle">Orders placed by your signed-in account.</p>
    {error && <p className="order-error" role="alert">{error}</p>}
    {loading ? <p role="status">Loading your orders…</p> : orders.length === 0 ? <div className="order-empty"><h2>No orders yet</h2><p>When you place an order, it will appear here.</p><Link className="order-primary" to="/">Explore products</Link></div> : <div className="orders-list">{orders.map((order) => <article className="history-order" key={order.orderId}><div className="history-order-heading"><div><span className="history-label">ORDER</span><h2>#{order.orderId}</h2></div><span className="order-status">{order.status || 'UNKNOWN'}</span></div><div className="history-order-meta"><span>Total <strong>{money(order.totalAmount)}</strong></span><span>{order.items?.length ?? 0} line item(s)</span></div>{Array.isArray(order.items) && order.items.length > 0 && <ul className="history-items">{order.items.map((item, index) => <li key={`${item.productId}-${index}`}>Product #{item.productId} · Qty {item.quantity} · {money(Number(item.price ?? 0) * Number(item.quantity ?? 0))}</li>)}</ul>}</article>)}</div>}
  </section></main>;
}
