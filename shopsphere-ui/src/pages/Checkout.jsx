
import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import { clearCart, getCart } from '../api/cartApi';
import { createOrder } from '../api/orderApi';
import { getAddresses, createAddress } from '../api/addressApi';
import { createManualPayment } from '../api/paymentApi';

import { QRCodeSVG } from 'qrcode.react';
import '../orderPages.css';

const UPI_ID = import.meta.env.VITE_SHOPSPHERE_UPI_ID || '';
const UPI_NAME = import.meta.env.VITE_SHOPSPHERE_UPI_NAME || 'ShopSphere';

const money = (value) =>
    new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 2,
    }).format(Number(value ?? 0));

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

const emptyAddress = {
  label: 'Home',
  recipientName: '',
  phoneNumber: '',
  addressLine1: '',
  addressLine2: '',
  city: '',
  state: '',
  postalCode: '',
  country: 'India',
  isDefault: false,
};

function isDefaultAddress(address) {
  return Boolean(address.isDefault ?? address.default);
}

export default function Checkout() {
  const navigate = useNavigate();

  const [cart, setCart] = useState(null);
  const [addresses, setAddresses] = useState([]);
  const [selectedAddressId, setSelectedAddressId] = useState('');
  const [addressForm, setAddressForm] = useState(emptyAddress);

  const [paymentMethod, setPaymentMethod] = useState('UPI');
  const [upiTransactionId, setUpiTransactionId] = useState('');

  const [showAddressForm, setShowAddressForm] = useState(false);
  const [loading, setLoading] = useState(true);
  const [savingAddress, setSavingAddress] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const [error, setError] = useState('');
  const [createdOrder, setCreatedOrder] = useState(null);
  const [createdPayment, setCreatedPayment] = useState(null);

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

    async function loadCheckout() {
      if (!sessionStorage.getItem('shopsphere_token')) {
        navigate('/login', {
          replace: true,
          state: { from: '/checkout' },
        });
        return;
      }

      try {
        const [cartData, addressData] = await Promise.all([
          getCart(),
          getAddresses(),
        ]);

        if (!active) return;

        const savedAddresses = Array.isArray(addressData)
            ? addressData
            : [];

        setCart(cartData);
        setAddresses(savedAddresses);

        const defaultAddress = savedAddresses.find(isDefaultAddress);

        if (defaultAddress) {
          setSelectedAddressId(String(defaultAddress.id));
        } else if (savedAddresses.length > 0) {
          setSelectedAddressId(String(savedAddresses[0].id));
        }

        setError('');
      } catch (e) {
        if (active) {
          setError(
              e.response?.data?.message ||
              'Could not load checkout information. Please sign in again if your session expired.'
          );
        }
      } finally {
        if (active) setLoading(false);
      }
    }

    loadCheckout();

    return () => {
      active = false;
    };
  }, [navigate]);

  function updateAddressField(event) {
    const { name, value, type, checked } = event.target;

    setAddressForm((previous) => ({
      ...previous,
      [name]: type === 'checkbox' ? checked : value,
    }));
  }

  async function saveAddress(event) {
    event.preventDefault();
    setError('');
    setSavingAddress(true);

    try {
      const savedAddress = await createAddress({
        ...addressForm,
        isDefault: addressForm.isDefault || addresses.length === 0,
      });

      setAddresses((previous) => [
        ...previous.map((address) => ({
          ...address,
          isDefault: false,
          default: false,
        })),
        savedAddress,
      ]);

      setSelectedAddressId(String(savedAddress.id));
      setAddressForm(emptyAddress);
      setShowAddressForm(false);
    } catch (e) {
      setError(
          e.response?.data?.message ||
          e.response?.data?.detail ||
          'Could not save your delivery address.'
      );
    } finally {
      setSavingAddress(false);
    }
  }

  async function submitPayment(order) {
    setError('');
    setSubmitting(true);

    try {
      const payment = await createManualPayment(
          order.orderId,
          paymentMethod,
          upiTransactionId,
          `shopsphere-${order.orderId}-${paymentMethod}`
      );

      setCreatedPayment(payment);

      try {
        await clearCart();
      } catch (clearError) {
        console.error(
            'Payment created, but cart cleanup failed:',
            clearError
        );
      }
    } catch (e) {
      setError(
          e.response?.data?.message ||
          e.response?.data?.detail ||
          'Payment submission failed. Your order was created. Please retry payment submission.'
      );
    } finally {
      setSubmitting(false);
    }
  }

  async function placeOrder() {
    setError('');

    if (!items.length) {
      setError('Your bag is empty. Add a product before checking out.');
      return;
    }

    if (!selectedAddressId) {
      setError('Please select or add a delivery address.');
      return;
    }

    if (paymentMethod === 'UPI' && !UPI_ID) {
      setError(
          'UPI is not configured yet. Configure your UPI ID in .env.local or select Cash on Delivery.'
      );
      return;
    }

    if (paymentMethod === 'UPI' && !upiTransactionId.trim()) {
      setError('Enter your UPI transaction ID after making the payment.');
      return;
    }

    const payloadItems = items.map((item) => ({
      productId: Number(productId(item)),
      quantity: Number(item.quantity),
      ...(item.variantId != null || item.variant?.id != null
        ? { variantId: Number(item.variantId ?? item.variant.id) }
        : {}),
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
          'The bag contains invalid product or quantity information. Refresh the bag and try again.'
      );
      return;
    }

    setSubmitting(true);

    try {
      const order = await createOrder(
          payloadItems,
          Number(selectedAddressId)
      );

      setCreatedOrder(order);

      // Submit payment after the order is successfully created.
      await submitPayment(order);
    } catch (e) {
      setError(
          e.response?.data?.message ||
          e.response?.data?.detail ||
          'Order could not be placed. Your bag has not been cleared.'
      );
      setSubmitting(false);
    }
  }

  async function retryPayment() {
    if (!createdOrder || createdPayment) return;
    await submitPayment(createdOrder);
  }

  if (createdPayment && createdOrder) {
    const delivery = createdOrder.deliveryAddress;

    return (
        <main className="order-page">
          <section className="order-panel order-success">
            <p className="order-eyebrow">ORDER CONFIRMED</p>

            <h1>Thank you for your order!</h1>

            <p>Your order has been created successfully.</p>

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
                <dt>Payment method</dt>
                <dd>{createdPayment.paymentMethod}</dd>
              </div>

              <div>
                <dt>Payment status</dt>
                <dd>
                  {createdPayment.status === 'PENDING'
                      ? 'Awaiting verification'
                      : createdPayment.status}
                </dd>
              </div>

              {createdPayment.upiTransactionId && (
                  <div>
                    <dt>UPI transaction ID</dt>
                    <dd>{createdPayment.upiTransactionId}</dd>
                  </div>
              )}
            </dl>

            {delivery && (
                <div className="order-note">
                  <strong>Delivering to</strong>
                  <p>
                    {delivery.recipientName}
                    <br />
                    {delivery.addressLine1}
                    <br />
                    {delivery.addressLine2 && (
                        <>
                          {delivery.addressLine2}
                          <br />
                        </>
                    )}
                    {delivery.city}, {delivery.state} - {delivery.postalCode}
                    <br />
                    {delivery.country}
                    <br />
                    Phone: {delivery.phoneNumber}
                  </p>
                </div>
            )}

            <div className="order-note">
              {createdPayment.paymentMethod === 'UPI'
                  ? 'Your UPI payment details have been submitted. ShopSphere will verify the transaction before marking the order as paid.'
                  : 'Your Cash on Delivery order is confirmed. Payment will be collected when the order is delivered.'}
            </div>

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
            Confirm your delivery address and payment method.
          </p>

          {error && (
              <p className="order-error" role="alert">
                {error}
              </p>
          )}

          {loading ? (
              <p role="status">Loading your checkout...</p>
          ) : createdOrder ? (
              <section className="order-note">
                <h2>Order #{createdOrder.orderId} created</h2>
                <p>
                  Your order exists, but payment submission needs to be retried.
                  Do not place another order.
                </p>

                <button
                    className="order-primary"
                    type="button"
                    onClick={retryPayment}
                    disabled={submitting}
                >
                  {submitting ? 'Submitting...' : 'Retry payment submission'}
                </button>
              </section>
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
                <section>
                  <h2>Delivery address</h2>

                  {addresses.length > 0 && (
                      <div className="order-line-items">
                        {addresses.map((address) => (
                            <label
                                className="order-line"
                                key={address.id}
                                style={{
                                  cursor: 'pointer',
                                  alignItems: 'flex-start',
                                }}
                            >
                              <input
                                  type="radio"
                                  name="deliveryAddress"
                                  value={address.id}
                                  checked={
                                      selectedAddressId === String(address.id)
                                  }
                                  onChange={() =>
                                      setSelectedAddressId(String(address.id))
                                  }
                              />

                              <div>
                                <strong>
                                  {address.label}
                                  {isDefaultAddress(address) && ' (Default)'}
                                </strong>

                                <span>{address.recipientName}</span>
                                <span>{address.addressLine1}</span>

                                {address.addressLine2 && (
                                    <span>{address.addressLine2}</span>
                                )}

                                <span>
                          {address.city}, {address.state} -{' '}
                                  {address.postalCode}
                        </span>

                                <span>{address.country}</span>
                                <span>{address.phoneNumber}</span>
                              </div>
                            </label>
                        ))}
                      </div>
                  )}

                  <button
                      type="button"
                      className="order-secondary"
                      onClick={() =>
                          setShowAddressForm((value) => !value)
                      }
                  >
                    {showAddressForm
                        ? 'Cancel adding address'
                        : '+ Add new address'}
                  </button>

                  {showAddressForm && (
                      <form
                          onSubmit={saveAddress}
                          className="order-line-items"
                          style={{ marginTop: '16px' }}
                      >
                        <h3>New delivery address</h3>

                        {[
                          ['label', 'Address label (Home, Work, etc.)'],
                          ['recipientName', 'Recipient name'],
                          ['phoneNumber', 'Phone number'],
                          ['addressLine1', 'Address line 1'],
                          ['addressLine2', 'Address line 2 (optional)'],
                          ['city', 'City'],
                          ['state', 'State'],
                          ['postalCode', 'Postal code'],
                          ['country', 'Country'],
                        ].map(([name, placeholder]) => (
                            <input
                                key={name}
                                name={name}
                                value={addressForm[name]}
                                onChange={updateAddressField}
                                placeholder={placeholder}
                                required={name !== 'addressLine2'}
                                maxLength={
                                  name === 'label'
                                      ? 80
                                      : name === 'recipientName'
                                          ? 120
                                          : name === 'phoneNumber'
                                              ? 20
                                              : name.startsWith('addressLine')
                                                  ? 255
                                                  : name === 'postalCode'
                                                      ? 20
                                                      : 100
                                }
                                className="order-address-input"
                            />
                        ))}

                        <label>
                          <input
                              type="checkbox"
                              name="isDefault"
                              checked={addressForm.isDefault}
                              onChange={updateAddressField}
                          />
                          Set as my default address
                        </label>

                        <button
                            type="submit"
                            className="order-primary"
                            disabled={savingAddress}
                        >
                          {savingAddress ? 'Saving...' : 'Save address'}
                        </button>
                      </form>
                  )}
                </section>

                <section className="order-line-items">
                  <h2>Order items</h2>

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
                            {money(item.subtotal ?? price * quantity)}
                          </strong>
                        </article>
                    );
                  })}
                </section>

                <div className="order-total">
                  <span>Subtotal</span>
                  <strong>{money(total)}</strong>
                </div>

                <section className="order-line-items">
                  <h2>Choose payment method</h2>

                  <label className="order-line">
                    <input
                        type="radio"
                        name="paymentMethod"
                        value="UPI"
                        checked={paymentMethod === 'UPI'}
                        onChange={() => setPaymentMethod('UPI')}
                    />

                    <div>
                      <strong>UPI Payment</strong>
                      <span>
                    Pay using Google Pay, PhonePe or another UPI app.
                  </span>
                    </div>
                  </label>

                  {paymentMethod === 'UPI' && (
                      <div className="order-note">
                        {UPI_ID ? (
                            <>
                              <strong>Pay to: {UPI_NAME}</strong>
                              <p>
                                UPI ID: <strong>{UPI_ID}</strong>
                              </p>
                              <p>
                                Amount: <strong>{money(total)}</strong>
                              </p>

                              <div style={{
                                  margin: '20px auto',
                                  padding: '18px',
                                  background: '#ffffff',
                                  borderRadius: '14px',
                                  textAlign: 'center',
                                  maxWidth: '280px',
                                  border: '1px solid #e5e5e5'
                              }}>
                                <strong>Scan with any UPI app</strong>

                                <div style={{
                                    display: 'flex',
                                    justifyContent: 'center',
                                    margin: '14px 0'
                                }}>
                                  <QRCodeSVG
                                    value={`upi://pay?pa=${encodeURIComponent(UPI_ID)}&pn=${encodeURIComponent(UPI_NAME)}&am=${Number(total).toFixed(2)}&cu=INR&tn=${encodeURIComponent('ShopSphere order payment')}`}
                                    size={220}
                                    level="H"
                                    includeMargin
                                  />
                                </div>

                                <p style={{ margin: 0, fontSize: '13px' }}>
                                  Verify the payee and exact amount in your UPI app.
                                </p>

                                <a
                                  href={`upi://pay?pa=${encodeURIComponent(UPI_ID)}&pn=${encodeURIComponent(UPI_NAME)}&am=${Number(total).toFixed(2)}&cu=INR&tn=${encodeURIComponent('ShopSphere order payment')}`}
                                  style={{
                                      display: 'inline-block',
                                      marginTop: '12px',
                                      fontWeight: 600
                                  }}
                                >
                                  Open UPI app
                                </a>
                              </div>

                              <div style={{
                                  margin: '20px auto',
                                  padding: '18px',
                                  background: '#ffffff',
                                  borderRadius: '14px',
                                  textAlign: 'center',
                                  maxWidth: '280px',
                                  border: '1px solid #e5e5e5'
                              }}>
                                <strong>Scan with any UPI app</strong>

                                <div style={{
                                    display: 'flex',
                                    justifyContent: 'center',
                                    margin: '14px 0'
                                }}>
                                  <QRCodeSVG
                                    value={`upi://pay?pa=${encodeURIComponent(UPI_ID)}&pn=${encodeURIComponent(UPI_NAME)}&am=${Number(total).toFixed(2)}&cu=INR&tn=${encodeURIComponent('ShopSphere order payment')}`}
                                    size={220}
                                    level="H"
                                    includeMargin
                                  />
                                </div>

                                <p style={{ margin: 0, fontSize: '13px' }}>
                                  Verify the payee and exact amount in your UPI app.
                                </p>

                                <a
                                  href={`upi://pay?pa=${encodeURIComponent(UPI_ID)}&pn=${encodeURIComponent(UPI_NAME)}&am=${Number(total).toFixed(2)}&cu=INR&tn=${encodeURIComponent('ShopSphere order payment')}`}
                                  style={{
                                      display: 'inline-block',
                                      marginTop: '12px',
                                      fontWeight: 600
                                  }}
                                >
                                  Open UPI app
                                </a>
                              </div>
                              <p>
                                Pay this exact amount using your UPI app.
                                Then enter the transaction reference below.
                              </p>

                              <input
                                  className="order-address-input"
                                  value={upiTransactionId}
                                  onChange={(event) =>
                                      setUpiTransactionId(event.target.value)
                                  }
                                  placeholder="Enter UPI transaction ID / UTR"
                                  maxLength={100}
                              />
                            </>
                        ) : (
                            <p>
                              UPI is not configured. Add your receiving UPI ID
                              to the frontend .env.local file.
                            </p>
                        )}
                      </div>
                  )}

                  <label className="order-line">
                    <input
                        type="radio"
                        name="paymentMethod"
                        value="COD"
                        checked={paymentMethod === 'COD'}
                        onChange={() => setPaymentMethod('COD')}
                    />

                    <div>
                      <strong>Cash on Delivery</strong>
                      <span>Pay when your order is delivered.</span>
                    </div>
                  </label>
                </section>

                <button
                    className="order-primary order-submit"
                    type="button"
                    onClick={placeOrder}
                    disabled={
                        submitting ||
                        loading ||
                        savingAddress ||
                        addresses.length === 0 ||
                        !selectedAddressId
                    }
                >
                  {submitting ? 'Processing...' : 'Place order'}
                </button>
              </>
          )}
        </section>
      </main>
  );
}
