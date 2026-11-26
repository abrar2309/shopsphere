import { useCallback, useEffect, useState } from 'react';

import {
    getInventorySummary,
    getInventoryAlerts,
    getInventoryMovements,
    adjustInventory,
    updateInventoryThreshold,
    getSellerSales,
} from '../api/sellerOperationsApi';

import './SellerOperationsPanel.css';

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

export default function SellerOperationsPanel() {
    const [summary, setSummary] = useState(null);
    const [alerts, setAlerts] = useState([]);
    const [movements, setMovements] = useState([]);
    const [sales, setSales] = useState([]);
    const [period, setPeriod] = useState('DAILY');

    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [message, setMessage] = useState('');
    const [saving, setSaving] = useState(false);

    const [productId, setProductId] = useState('');
    const [quantityChange, setQuantityChange] = useState('');
    const [reason, setReason] = useState('RESTOCK');
    const [note, setNote] = useState('');

    const [thresholdProductId, setThresholdProductId] = useState('');
    const [threshold, setThreshold] = useState('5');

    const loadData = useCallback(async () => {
        setLoading(true);
        setError('');

        try {
            const [
                summaryData,
                alertData,
                movementData,
                salesData,
            ] = await Promise.all([
                getInventorySummary(),
                getInventoryAlerts(),
                getInventoryMovements(50),
                getSellerSales(period),
            ]);

            setSummary(summaryData);
            setAlerts(Array.isArray(alertData) ? alertData : []);
            setMovements(Array.isArray(movementData) ? movementData : []);
            setSales(Array.isArray(salesData?.data) ? salesData.data : []);
        } catch (requestError) {
            setError(
                requestError.response?.data?.message ||
                requestError.response?.data?.detail ||
                requestError.message ||
                'Could not load seller operations.'
            );
        } finally {
            setLoading(false);
        }
    }, [period]);

    useEffect(() => {
        loadData();
    }, [loadData]);

    async function handleAdjustment(event) {
        event.preventDefault();

        const delta = Number(quantityChange);
        const id = Number(productId);

        if (!Number.isInteger(id) || id <= 0) {
            setError('Enter a valid product ID.');
            return;
        }

        if (!Number.isInteger(delta) || delta === 0) {
            setError('Quantity change must be a non-zero whole number.');
            return;
        }

        setSaving(true);
        setError('');
        setMessage('');

        try {
            const result = await adjustInventory(
                id,
                delta,
                reason,
                note
            );

            setMessage(result.message || 'Inventory updated.');
            setQuantityChange('');
            setNote('');
            await loadData();
        } catch (requestError) {
            setError(
                requestError.response?.data?.message ||
                requestError.response?.data?.detail ||
                'Inventory adjustment failed.'
            );
        } finally {
            setSaving(false);
        }
    }

    async function handleThreshold(event) {
        event.preventDefault();

        const id = Number(thresholdProductId);
        const value = Number(threshold);

        if (!Number.isInteger(id) || id <= 0 ||
            !Number.isInteger(value) || value < 0) {
            setError('Enter a valid product ID and non-negative threshold.');
            return;
        }

        setSaving(true);
        setError('');
        setMessage('');

        try {
            await updateInventoryThreshold(id, value);
            setMessage('Low-stock threshold updated.');
            await loadData();
        } catch (requestError) {
            setError(
                requestError.response?.data?.message ||
                'Could not update the threshold.'
            );
        } finally {
            setSaving(false);
        }
    }

    if (loading && !summary) {
        return <section className="seller-operations">
            <p>Loading seller operations...</p>
        </section>;
    }

    return (
        <section className="seller-operations">
            <div className="operations-heading">
                <div>
                    <span className="seller-operations-eyebrow">
                        BUSINESS OPERATIONS
                    </span>
                    <h2>Inventory & sales analytics</h2>
                    <p>Monitor stock, movements and sales performance.</p>
                </div>

                <button type="button" onClick={loadData}>
                    Refresh
                </button>
            </div>

            {error && <p className="operations-error" role="alert">{error}</p>}
            {message && <p className="operations-success" role="status">{message}</p>}

            <div className="operations-stats">
                <article>
                    <span>Products</span>
                    <strong>{summary?.productCount ?? 0}</strong>
                </article>

                <article>
                    <span>Units in stock</span>
                    <strong>{summary?.totalUnits ?? 0}</strong>
                </article>

                <article>
                    <span>Inventory value</span>
                    <strong>{money(summary?.inventoryValue)}</strong>
                </article>

                <article>
                    <span>Out of stock</span>
                    <strong>{summary?.outOfStockProducts ?? 0}</strong>
                </article>

                <article>
                    <span>Low stock</span>
                    <strong>{summary?.lowStockProducts ?? 0}</strong>
                </article>
            </div>

            <div className="operations-grid">
                <section className="operations-card">
                    <h3>Adjust inventory</h3>
                    <p>Use a positive quantity to add stock and a negative quantity to remove it.</p>

                    <form onSubmit={handleAdjustment}>
                        <label>
                            Product ID
                            <input
                                type="number"
                                min="1"
                                value={productId}
                                onChange={(e) => setProductId(e.target.value)}
                                required
                            />
                        </label>

                        <label>
                            Quantity change
                            <input
                                type="number"
                                value={quantityChange}
                                onChange={(e) => setQuantityChange(e.target.value)}
                                required
                            />
                        </label>

                        <label>
                            Reason
                            <select
                                value={reason}
                                onChange={(e) => setReason(e.target.value)}
                            >
                                <option value="RESTOCK">Restock</option>
                                <option value="DAMAGED">Damaged</option>
                                <option value="RETURN">Return</option>
                                <option value="CORRECTION">Correction</option>
                                <option value="MANUAL_ADJUSTMENT">Manual adjustment</option>
                                <option value="OTHER">Other</option>
                            </select>
                        </label>

                        <label>
                            Note
                            <textarea
                                maxLength="500"
                                value={note}
                                onChange={(e) => setNote(e.target.value)}
                                placeholder="Optional explanation"
                            />
                        </label>

                        <button disabled={saving} type="submit">
                            {saving ? 'Saving...' : 'Update stock'}
                        </button>
                    </form>
                </section>

                <section className="operations-card">
                    <h3>Low-stock threshold</h3>
                    <p>Set the stock level at which a product should generate an alert.</p>

                    <form onSubmit={handleThreshold}>
                        <label>
                            Product ID
                            <input
                                type="number"
                                min="1"
                                value={thresholdProductId}
                                onChange={(e) => setThresholdProductId(e.target.value)}
                                required
                            />
                        </label>

                        <label>
                            Alert threshold
                            <input
                                type="number"
                                min="0"
                                value={threshold}
                                onChange={(e) => setThreshold(e.target.value)}
                                required
                            />
                        </label>

                        <button disabled={saving} type="submit">
                            Save threshold
                        </button>
                    </form>
                </section>
            </div>

            <section className="operations-card">
                <div className="operations-section-heading">
                    <div>
                        <h3>Inventory alerts</h3>
                        <p>Products that have reached their stock threshold.</p>
                    </div>
                    <span>{alerts.length} alert(s)</span>
                </div>

                {alerts.length === 0 ? (
                    <p className="operations-empty">No low-stock alerts.</p>
                ) : (
                    <div className="operations-table-wrap">
                        <table className="operations-table">
                            <thead>
                            <tr>
                                <th>Product</th>
                                <th>Stock</th>
                                <th>Threshold</th>
                                <th>Status</th>
                            </tr>
                            </thead>
                            <tbody>
                            {alerts.map((item) => (
                                <tr key={item.productId}>
                                    <td>{item.productName}</td>
                                    <td>{item.currentStock ?? 0}</td>
                                    <td>{item.threshold ?? 0}</td>
                                    <td>
                                            <span className={
                                                      item.outOfStock
                                                          ? 'operations-status danger'
                                                          : 'operations-status warning'
                                                  }>
                                                {item.outOfStock ? 'OUT OF STOCK' : 'LOW STOCK'}
                                            </span>
                                    </td>
                                </tr>
                            ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </section>

            <section className="operations-card">
                <div className="operations-section-heading">
                    <div>
                        <h3>Sales analytics</h3>
                        <p>Sales aggregated from successful payments.</p>
                    </div>

                    <select
                        value={period}
                        onChange={(e) => setPeriod(e.target.value)}
                        aria-label="Sales analytics period"
                    >
                        <option value="DAILY">Daily · 30 days</option>
                        <option value="WEEKLY">Weekly · 12 weeks</option>
                        <option value="MONTHLY">Monthly · 12 months</option>
                    </select>
                </div>

                {sales.length === 0 ? (
                    <p className="operations-empty">
                        No sales data is available for this period yet.
                    </p>
                ) : (
                    <>
                        <div className="sales-chart">
                            {sales.map((item) => {
                                const maximum = Math.max(
                                    ...sales.map((point) => Number(point.grossSales ?? 0)),
                                    1
                                );

                                const amount = Number(item.grossSales ?? 0);
                                const width = Math.max(2, amount / maximum * 100);

                                return (
                                    <div
                                        className="sales-chart-row"
                                        key={item.periodStart}
                                    >
                                        <span>{item.periodStart}</span>
                                        <div className="sales-chart-track">
                                            <div
                                                className="sales-chart-bar"
                                                style={{ width: `${width}%` }}
                                            />
                                        </div>
                                        <strong>{money(amount)}</strong>
                                    </div>
                                );
                            })}
                        </div>

                        <div className="operations-table-wrap">
                            <table className="operations-table">
                                <thead>
                                <tr>
                                    <th>Period start</th>
                                    <th>Paid orders</th>
                                    <th>Units sold</th>
                                    <th>Gross sales</th>
                                </tr>
                                </thead>
                                <tbody>
                                {sales.map((item) => (
                                    <tr key={item.periodStart}>
                                        <td>{item.periodStart}</td>
                                        <td>{item.paidOrders}</td>
                                        <td>{item.unitsSold}</td>
                                        <td>{money(item.grossSales)}</td>
                                    </tr>
                                ))}
                                </tbody>
                            </table>
                        </div>
                    </>
                )}
            </section>

            <section className="operations-card">
                <h3>Inventory movement history</h3>
                <p>Recent stock changes recorded by the database.</p>

                {movements.length === 0 ? (
                    <p className="operations-empty">No inventory movements recorded yet.</p>
                ) : (
                    <div className="operations-table-wrap">
                        <table className="operations-table">
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
                            {movements.map((item) => (
                                <tr key={item.id}>
                                    <td>{dateTime(item.createdAt)}</td>
                                    <td>{item.productName}</td>
                                    <td>{item.quantityBefore}</td>
                                    <td>{item.quantityDelta > 0 ? '+' : ''}{item.quantityDelta}</td>
                                    <td>{item.quantityAfter}</td>
                                    <td>{item.reason}</td>
                                    <td>{item.note || '—'}</td>
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