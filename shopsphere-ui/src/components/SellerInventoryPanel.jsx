
import { useCallback, useEffect, useState } from 'react';

import {
    getInventorySummary,
    getInventoryAlerts,
    getInventoryMovements,
    adjustInventory,
    updateInventoryThreshold,
} from '../api/sellerInventoryApi';

import './SellerInventoryPanel.css';

const money = (value) =>
    new Intl.NumberFormat('en-IN', {
        style: 'currency',
        currency: 'INR',
        maximumFractionDigits: 2,
    }).format(Number(value ?? 0));

const dateTime = (value) => {
    if (!value) return '—';

    const date = new Date(value);
    return Number.isNaN(date.getTime())
        ? '—'
        : date.toLocaleString('en-IN');
};

function errorMessage(error) {
    return (
        error?.response?.data?.message ||
        error?.response?.data?.error ||
        error?.message ||
        'Something went wrong. Please try again.'
    );
}

export default function SellerInventoryPanel({ products = [] }) {
    const [summary, setSummary] = useState(null);
    const [alerts, setAlerts] = useState([]);
    const [movements, setMovements] = useState([]);

    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');

    const [adjustment, setAdjustment] = useState({
        productId: '',
        quantityDelta: '',
        reason: 'RESTOCK',
        note: '',
    });

    const [thresholds, setThresholds] = useState({});
    const [thresholdSavingId, setThresholdSavingId] = useState(null);

    const refresh = useCallback(async () => {
        setLoading(true);
        setError('');

        try {
            const [summaryData, alertData, movementData] =
                await Promise.all([
                    getInventorySummary(),
                    getInventoryAlerts(),
                    getInventoryMovements(50),
                ]);

            setSummary(summaryData || {});
            setAlerts(Array.isArray(alertData) ? alertData : []);
            setMovements(Array.isArray(movementData) ? movementData : []);
        } catch (err) {
            setError(errorMessage(err));
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        refresh();
    }, [refresh]);

    const submitAdjustment = async (event) => {
        event.preventDefault();

        const productId = Number(adjustment.productId);
        const quantityDelta = Number(adjustment.quantityDelta);

        if (!productId || !Number.isInteger(quantityDelta) || quantityDelta === 0) {
            setError('Choose a product and enter a non-zero whole-number stock change.');
            return;
        }

        if (!adjustment.reason.trim()) {
            setError('Please select a stock adjustment reason.');
            return;
        }

        setSaving(true);
        setError('');
        setSuccess('');

        try {
            await adjustInventory(productId, {
                quantityDelta,
                reason: adjustment.reason,
                referenceType: 'MANUAL_ADJUSTMENT',
                note: adjustment.note.trim() || null,
            });

            setAdjustment({
                productId: '',
                quantityDelta: '',
                reason: 'RESTOCK',
                note: '',
            });

            setSuccess('Inventory updated successfully.');
            await refresh();
        } catch (err) {
            setError(errorMessage(err));
        } finally {
            setSaving(false);
        }
    };

    const saveThreshold = async (productId) => {
        const threshold = Number(thresholds[productId]);

        if (!Number.isInteger(threshold) || threshold < 0) {
            setError('Low-stock threshold must be a whole number greater than or equal to zero.');
            return;
        }

        setThresholdSavingId(productId);
        setError('');
        setSuccess('');

        try {
            await updateInventoryThreshold(productId, threshold);
            setSuccess(`Low-stock threshold updated for product #${productId}.`);
            await refresh();
        } catch (err) {
            setError(errorMessage(err));
        } finally {
            setThresholdSavingId(null);
        }
    };

    return (
        <section className="seller-inventory-panel">
            <div className="inventory-heading">
                <div>
                    <span className="inventory-eyebrow">SELLER OPERATIONS</span>
                    <h2>Inventory overview</h2>
                    <p>Monitor stock, make adjustments and review inventory activity.</p>
                </div>

                <button
                    type="button"
                    className="inventory-refresh"
                    onClick={refresh}
                    disabled={loading}
                >
                    {loading ? 'Refreshing…' : 'Refresh'}
                </button>
            </div>

            {error && (
                <div className="inventory-message inventory-error" role="alert">
                    {error}
                </div>
            )}

            {success && (
                <div className="inventory-message inventory-success" role="status">
                    {success}
                </div>
            )}

            <div className="inventory-summary-grid">
                <article className="inventory-stat">
                    <span>Total products</span>
                    <strong>{summary?.productCount ?? 0}</strong>
                </article>

                <article className="inventory-stat">
                    <span>Total units in stock</span>
                    <strong>{summary?.totalUnits ?? 0}</strong>
                </article>

                <article className="inventory-stat">
                    <span>Inventory valuation</span>
                    <strong>{money(summary?.inventoryValue)}</strong>
                </article>

                <article className="inventory-stat inventory-stat-warning">
                    <span>Low-stock products</span>
                    <strong>{summary?.lowStockProducts ?? 0}</strong>
                </article>

                <article className="inventory-stat inventory-stat-danger">
                    <span>Out of stock</span>
                    <strong>{summary?.outOfStockProducts ?? 0}</strong>
                </article>
            </div>

            <div className="inventory-content-grid">
                <section className="inventory-card">
                    <div className="inventory-card-heading">
                        <div>
                            <h3>Stock adjustment</h3>
                            <p>Increase or decrease a product's available stock.</p>
                        </div>
                    </div>

                    <form className="inventory-adjustment-form" onSubmit={submitAdjustment}>
                        <label>
                            Product
                            <select
                                value={adjustment.productId}
                                onChange={(event) =>
                                    setAdjustment((current) => ({
                                        ...current,
                                        productId: event.target.value,
                                    }))
                                }
                                required
                            >
                                <option value="">Select a product</option>
                                {products.map((product) => (
                                    <option key={product.id} value={product.id}>
                                        {product.name} — Current stock: {product.stock ?? 0}
                                    </option>
                                ))}
                            </select>
                        </label>

                        <label>
                            Quantity change
                            <input
                                type="number"
                                step="1"
                                required
                                value={adjustment.quantityDelta}
                                onChange={(event) =>
                                    setAdjustment((current) => ({
                                        ...current,
                                        quantityDelta: event.target.value,
                                    }))
                                }
                                placeholder="Example: 20 or -3"
                            />
                            <small>Use a positive number to add stock and a negative number to remove stock.</small>
                        </label>

                        <label>
                            Reason
                            <select
                                value={adjustment.reason}
                                onChange={(event) =>
                                    setAdjustment((current) => ({
                                        ...current,
                                        reason: event.target.value,
                                    }))
                                }
                            >
                                <option value="RESTOCK">Restock</option>
                                <option value="DAMAGED">Damaged goods</option>
                                <option value="LOST">Lost inventory</option>
                                <option value="RETURN">Customer return</option>
                                <option value="CORRECTION">Stock correction</option>
                                <option value="OTHER">Other</option>
                            </select>
                        </label>

                        <label>
                            Note
                            <textarea
                                rows="3"
                                maxLength="500"
                                value={adjustment.note}
                                onChange={(event) =>
                                    setAdjustment((current) => ({
                                        ...current,
                                        note: event.target.value,
                                    }))
                                }
                                placeholder="Optional explanation for this adjustment"
                            />
                        </label>

                        <button type="submit" disabled={saving || loading}>
                            {saving ? 'Updating stock…' : 'Save stock adjustment'}
                        </button>
                    </form>
                </section>

                <section className="inventory-card">
                    <div className="inventory-card-heading">
                        <div>
                            <h3>Stock alerts</h3>
                            <p>Products that need your attention.</p>
                        </div>
                        <span className="inventory-count">{alerts.length}</span>
                    </div>

                    {alerts.length === 0 ? (
                        <div className="inventory-empty">
                            No low-stock or out-of-stock alerts.
                        </div>
                    ) : (
                        <div className="inventory-alert-list">
                            {alerts.map((item) => (
                                <article
                                    className="inventory-alert"
                                    key={item.productId}
                                >
                                    <div className="inventory-alert-info">
                                        <strong>{item.productName}</strong>
                                        <span>
                                            Stock: {item.currentStock} · Threshold: {item.threshold}
                                        </span>
                                    </div>

                                    <span
                                        className={
                                            item.outOfStock
                                                ? 'inventory-status inventory-status-danger'
                                                : 'inventory-status inventory-status-warning'
                                        }
                                    >
                                        {item.outOfStock ? 'Out of stock' : 'Low stock'}
                                    </span>
                                </article>
                            ))}
                        </div>
                    )}
                </section>
            </div>

            <section className="inventory-card inventory-threshold-card">
                <div className="inventory-card-heading">
                    <div>
                        <h3>Low-stock thresholds</h3>
                        <p>Set the stock level at which each product should trigger an alert.</p>
                    </div>
                </div>

                {products.length === 0 ? (
                    <div className="inventory-empty">No products available.</div>
                ) : (
                    <div className="inventory-threshold-list">
                        {products.map((product) => (
                            <div className="inventory-threshold-row" key={product.id}>
                                <div>
                                    <strong>{product.name}</strong>
                                    <span>Current stock: {product.stock ?? 0}</span>
                                </div>

                                <input
                                    type="number"
                                    min="0"
                                    step="1"
                                    aria-label={`Low-stock threshold for ${product.name}`}
                                    value={
                                        thresholds[product.id] ??
                                        product.lowStockThreshold ??
                                        5
                                    }
                                    onChange={(event) =>
                                        setThresholds((current) => ({
                                            ...current,
                                            [product.id]: event.target.value,
                                        }))
                                    }
                                />

                                <button
                                    type="button"
                                    onClick={() => saveThreshold(product.id)}
                                    disabled={thresholdSavingId === product.id}
                                >
                                    {thresholdSavingId === product.id ? 'Saving…' : 'Save'}
                                </button>
                            </div>
                        ))}
                    </div>
                )}
            </section>

            <section className="inventory-card inventory-history-card">
                <div className="inventory-card-heading">
                    <div>
                        <h3>Inventory movement history</h3>
                        <p>Recent stock changes recorded by the database.</p>
                    </div>
                    <span className="inventory-count">{movements.length}</span>
                </div>

                {movements.length === 0 ? (
                    <div className="inventory-empty">
                        No inventory movements recorded yet.
                    </div>
                ) : (
                    <div className="inventory-table-wrapper">
                        <table className="inventory-table">
                            <thead>
                            <tr>
                                <th>Date</th>
                                <th>Product</th>
                                <th>Before</th>
                                <th>Change</th>
                                <th>After</th>
                                <th>Reason</th>
                                <th>Note</th>
                            </tr>
                            </thead>

                            <tbody>
                            {movements.map((movement) => (
                                <tr key={movement.id}>
                                    <td>{dateTime(movement.createdAt)}</td>
                                    <td>{movement.productName}</td>
                                    <td>{movement.quantityBefore}</td>
                                    <td
                                        className={
                                            Number(movement.quantityDelta) >= 0
                                                ? 'inventory-positive'
                                                : 'inventory-negative'
                                        }
                                    >
                                        {Number(movement.quantityDelta) > 0 ? '+' : ''}
                                        {movement.quantityDelta}
                                    </td>
                                    <td>{movement.quantityAfter}</td>
                                    <td>{movement.reason}</td>
                                    <td>{movement.note || '—'}</td>
                                </tr>
                            ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </section>
        </section>
    );
}
