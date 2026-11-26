
import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import {
    getPendingPayments,
    verifyPayment,
} from '../api/paymentApi';

import '../orderPages.css';

const money = (value) =>
    new Intl.NumberFormat('en-IN', {
        style: 'currency',
        currency: 'INR',
        maximumFractionDigits: 2,
    }).format(Number(value ?? 0));

const dateTime = (value) => {
    if (!value) return 'Not available';

    const date = new Date(value);

    if (Number.isNaN(date.getTime())) return 'Not available';

    return date.toLocaleString('en-IN');
};

function extractPayments(data) {
    if (Array.isArray(data)) return data;
    if (Array.isArray(data?.content)) return data.content;
    if (Array.isArray(data?.payments)) return data.payments;
    if (Array.isArray(data?.data)) return data.data;

    return [];
}

export default function AdminPayments() {
    const [payments, setPayments] = useState([]);
    const [notes, setNotes] = useState({});
    const [loading, setLoading] = useState(true);
    const [processingId, setProcessingId] = useState(null);
    const [error, setError] = useState('');
    const [message, setMessage] = useState('');

    const loadPayments = useCallback(async () => {
        setLoading(true);
        setError('');

        try {
            const data = await getPendingPayments();
            setPayments(extractPayments(data));
        } catch (e) {
            setError(
                e.response?.data?.message ||
                e.response?.data?.detail ||
                'Could not load pending payments. Check your administrator access.'
            );
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        loadPayments();
    }, [loadPayments]);

    async function handleVerification(payment, decision) {
        const paymentId = payment.paymentId ?? payment.id;

        if (!paymentId) {
            setError('This payment does not contain a valid payment ID.');
            return;
        }

        setError('');
        setMessage('');
        setProcessingId(paymentId);

        try {
            await verifyPayment(
                paymentId,
                decision,
                notes[paymentId] || ''
            );

            setMessage(
                `Payment #${paymentId} was ${decision === 'SUCCESS' ? 'approved' : 'rejected'} successfully.`
            );

            setPayments((previous) =>
                previous.filter(
                    (item) => (item.paymentId ?? item.id) !== paymentId
                )
            );

            setNotes((previous) => {
                const updated = { ...previous };
                delete updated[paymentId];
                return updated;
            });
        } catch (e) {
            setError(
                e.response?.data?.message ||
                e.response?.data?.detail ||
                'Payment verification failed. Please try again.'
            );
        } finally {
            setProcessingId(null);
        }
    }

    return (
        <main className="order-page">
            <section className="order-panel">
                <div className="order-page-top">
                    <Link to="/" className="order-back">
                        Back to shop
                    </Link>

                    <button
                        type="button"
                        className="order-secondary"
                        onClick={loadPayments}
                        disabled={loading || processingId !== null}
                    >
                        {loading ? 'Refreshing...' : 'Refresh payments'}
                    </button>
                </div>

                <p className="order-eyebrow">SHOPSPHERE ADMIN</p>

                <h1>Payment verification</h1>

                <p className="order-subtitle">
                    Review customer payment submissions and verify UPI
                    transactions or COD orders.
                </p>

                {error && (
                    <p className="order-error" role="alert">
                        {error}
                    </p>
                )}

                {message && (
                    <p className="order-note" role="status">
                        {message}
                    </p>
                )}

                <div className="order-total">
                    <span>Pending payments</span>
                    <strong>{payments.length}</strong>
                </div>

                {loading ? (
                    <p role="status">Loading pending payments...</p>
                ) : payments.length === 0 ? (
                    <div className="order-empty">
                        <h2>No pending payments</h2>
                        <p>There are currently no payments waiting for verification.</p>
                    </div>
                ) : (
                    <div className="order-line-items">
                        {payments.map((payment) => {
                            const paymentId = payment.paymentId ?? payment.id;
                            const isProcessing = processingId === paymentId;

                            return (
                                <article
                                    className="order-line-items"
                                    key={paymentId}
                                    style={{
                                        padding: '18px',
                                        marginBottom: '16px',
                                    }}
                                >
                                    <div className="order-line">
                                        <div>
                                            <strong>Payment #{paymentId}</strong>
                                            <span>
                        Order #{payment.orderId}
                      </span>
                                        </div>

                                        <strong>{money(payment.amount)}</strong>
                                    </div>

                                    <div className="order-line">
                                        <span>Payment method</span>
                                        <strong>{payment.paymentMethod || 'Unknown'}</strong>
                                    </div>

                                    <div className="order-line">
                                        <span>Payment status</span>
                                        <strong>{payment.status || 'PENDING'}</strong>
                                    </div>

                                    <div className="order-line">
                                        <span>Payment reference</span>
                                        <strong>
                                            {payment.paymentReference || 'Not available'}
                                        </strong>
                                    </div>

                                    {payment.paymentMethod === 'UPI' && (
                                        <div className="order-note">
                                            <strong>UPI transaction reference / UTR</strong>
                                            <p>
                                                {payment.upiTransactionId || 'Not provided'}
                                            </p>
                                        </div>
                                    )}

                                    <div className="order-line">
                                        <span>Submitted</span>
                                        <span>{dateTime(payment.createdAt)}</span>
                                    </div>

                                    <label
                                        style={{
                                            display: 'block',
                                            marginTop: '12px',
                                        }}
                                    >
                                        <strong>Admin verification note</strong>

                                        <textarea
                                            className="order-address-input"
                                            rows={3}
                                            maxLength={500}
                                            placeholder="Optional: Add a verification note..."
                                            value={notes[paymentId] || ''}
                                            disabled={isProcessing}
                                            onChange={(event) =>
                                                setNotes((previous) => ({
                                                    ...previous,
                                                    [paymentId]: event.target.value,
                                                }))
                                            }
                                            style={{
                                                display: 'block',
                                                width: '100%',
                                                marginTop: '8px',
                                                resize: 'vertical',
                                            }}
                                        />
                                    </label>

                                    <div
                                        className="order-actions"
                                        style={{ marginTop: '14px' }}
                                    >
                                        <button
                                            type="button"
                                            className="order-primary"
                                            disabled={processingId !== null}
                                            onClick={() =>
                                                handleVerification(payment, 'SUCCESS')
                                            }
                                        >
                                            {isProcessing ? 'Processing...' : 'Approve payment'}
                                        </button>

                                        <button
                                            type="button"
                                            className="order-secondary"
                                            disabled={processingId !== null}
                                            onClick={() =>
                                                handleVerification(payment, 'FAILURE')
                                            }
                                        >
                                            Reject payment
                                        </button>
                                    </div>
                                </article>
                            );
                        })}
                    </div>
                )}
            </section>
        </main>
    );
}
