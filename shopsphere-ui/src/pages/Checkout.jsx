
import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { clearCart, getCart } from '../api/cartApi';
import { createOrder } from '../api/orderApi';
import '../orderPages.css';

const money = (value) =>
  new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 2,
  }).format(Number(value ?? 0));

// Match the cart formats supported by App.jsx.
function itemsFrom(cart) {
  if (Array.isArray(cart)) return cart;
  if (Array.isArray(cart?.items)) return cart.items;
  if (Array.isArray(cart?.cartItems)) return cart.cartItems;
  if (Array.isArray(cart?.cart?.items)) return cart.cart.items;
  return [];
}

function productId(item) {
  return (
    item.productId ??
    item.product?.id ??
    item.product?.productId ??
    item.id
  );
}

export default function Checkout() {
  const navigate = useNavigate();

  const [cart, setCart] = useState(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [createdOrder, setCreatedOrder] = useState(null);

  const items = itemsFrom(cart);

  const total = useMemo(
    () =>
      Number(
        cart?.totalAmount ??
          items.reduce(
            (sum, item) =>
              sum +
              Number(
                item.subtotal ??
                  Number(item.price ?? item.product?.price ?? 0) *
                    Number(item.quantity ?? 0)
              ),
            0
          )
      ),
    [cart, items]
  );

  useEffect(() => {
    let active = true;

    async function load() {
      if (!sessionStorage.getItem('shopsphere_token')) {
        navigate('/login', {
          replace: true,
          state: { from: '/checkout' },
        });
        return;
      }

      try {
        const data = await getCart();

        if (active) {
          setCart(data);
          setError('');
        }
      } catch (e) {
        if (active) {
          setError(
            e.response?.data?.message ||
              'Could not load your bag. Please sign in again if your session expired.'
          );
        }
      } finally {
        if (active) setLoading(false);
      }
    }

    load();

    return () => {
      active = false;
    };
  }, [navigate]);

  async function placeOrder() {
    setError('');

    if (!items.length) {
      setError('Your bag is empty. Add a product before checking out.');
      return;
    }

    const payloadItems = items.map((item) => ({
      productId: Number(productId(item)),
      quantity: Number(item.quantity),
    }));

    if (
      payloadItems.some(
        (item) =>
          !Number.isInteger(item.productId) ||
          item.productId <= 0 ||
          !Number.isInteger(item.quantity) ||
          item.quantity < 1
      )
    ) {
      setError(
        'The bag contains an item with invalid product or quantity information. Refresh the bag and try again.'
      );
      return;
    }

    setSubmitting(true);

    try {
      // The backend validates prices and stock and creates the order.
      const order = await createOrder(payloadItems);

      setCreatedOrder(order);

      // Clear the cart only after order creation succeeds.
      try {
        await clearCart();
      } catch (clearError) {
        console.error(
          'Order created, but cart cleanup failed:',
          clearError
        );
      }
    } catch (e) {
      setError(
        e.response?.data?.message ||
          e.response?.data?.detail ||
          'Order could not be placed. Your bag has not been cleared; please review it and retry.'
      );
    } finally {
      setSubmitting(false);
    }
  }

  if (createdOrder) {
    return (
      <main className="order-page">
        <section className="order-panel order-success">
          <p className="order-eyebrow">ORDER CONFIRMED</p>
          <h1>Thank you for your order!</h1>
          <p>Your order has been accepted by ShopSphere.</p>

          <dl className="order-details">
            <div>
              <dt>Order number</dt>
              <dd>#{createdOrder.orderId}</dd>
            </div>
            <div>
              <dt>Total</dt>
              <dd>{money(createdOrder.totalAmount)}</dd>
            </div>
            <div>
              <dt>Status</dt>
              <dd>{createdOrder.status || 'CREATED'}</dd>
            </div>
          </dl>

          <p className="order-note">
            This checkout version does not collect a delivery address
            or process a payment yet.
          </p>

          <div className="order-actions">
            <Link className="order-primary" to="/orders">
              View my orders
            </Link>
            <Link className="order-secondary" to="/">
              Continue shopping
            </Link>
          </div>
        </section>
      </main>
    );
  }

  return (
    <main className="order-page">
      <section className="order-panel">
        <div className="order-page-top">
          <Link to="/" className="order-back">
            Back to shopping
          </Link>
          <Link to="/orders" className="order-back">
            My orders
          </Link>
        </div>

        <p className="order-eyebrow">FINAL STEP</p>
        <h1>Review your order</h1>
        <p className="order-subtitle">
          Check your items before placing your order.
        </p>

        {error && (
          <p className="order-error" role="alert">
            {error}
          </p>
        )}

        {loading ? (
          <p role="status">Loading your bag...</p>
        ) : items.length === 0 ? (
          <div className="order-empty">
            <h2>Your bag is empty</h2>
            <p>Add products before checking out.</p>
            <Link className="order-primary" to="/">
              Browse products
            </Link>
          </div>
        ) : (
          <>
            <div className="order-line-items">
              {items.map((item, index) => {
                const id = productId(item);
                const quantity = Number(item.quantity ?? 0);
                const price = Number(
                  item.price ?? item.product?.price ?? 0
                );

                return (
                  <article
                    className="order-line"
                    key={id ?? index}
                  >
                    <div>
                      <strong>
                        {item.productName ??
                          item.product?.name ??
                          item.name ??
                          'Product #' + id}
                      </strong>
                      <span>Quantity: {quantity}</span>
                    </div>

                    <strong>
                      {money(
                        item.subtotal ?? price * quantity
                      )}
                    </strong>
                  </article>
                );
              })}
            </div>

            <div className="order-total">
              <span>Subtotal</span>
              <strong>{money(total)}</strong>
            </div>

            <div className="order-note">
              <strong>Before you continue</strong>
              <p>
                This version creates the order using the existing
                backend API. It does not collect a delivery address
                or take payment. The backend validates current prices
                and stock.
              </p>
            </div>

            <button
              className="order-primary order-submit"
              type="button"
              onClick={placeOrder}
              disabled={submitting || loading}
            >
              {submitting ? 'Placing order...' : 'Place order'}
            </button>
          </>
        )}
      </section>
    </main>
  );
}
