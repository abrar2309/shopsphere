
import { useCallback, useEffect, useState } from 'react';

import {
    getSellerAnalyticsSummary,
    getSellerSales,
} from '../api/sellerAnalyticsApi';

import './SellerAnalyticsPanel.css';

const money = (value) =>
    new Intl.NumberFormat('en-IN', {
        style: 'currency',
        currency: 'INR',
        maximumFractionDigits: 0,
    }).format(Number(value ?? 0));

const number = (value) =>
    new Intl.NumberFormat('en-IN').format(Number(value ?? 0));

function SalesChart({ data }) {
    const width = 760;
    const height = 260;
    const padding = 36;

    const values = data.map((item) => Number(item.grossSales ?? 0));
    const max = Math.max(...values, 1);

    const points = values.map((value, index) => {
        const x = data.length <= 1
            ? width / 2
            : padding + (index / (data.length - 1)) * (width - padding * 2);

        const y = height - padding -
            (value / max) * (height - padding * 2);

        return { x, y, value };
    });

    const line = points
        .map((point, index) =>
            `${index === 0 ? 'M' : 'L'} ${point.x} ${point.y}`
        )
        .join(' ');

    const area = points.length
        ? `${line} L ${points[points.length - 1].x} ${height - padding} L ${points[0].x} ${height - padding} Z`
        : '';

    if (!data.length) {
        return (
            <div className="seller-analytics-empty">
                No paid sales are available for this period yet.
            </div>
        );
    }

    return (
        <div className="seller-sales-chart">
            <svg
                viewBox={`0 0 ${width} ${height}`}
                role="img"
                aria-label="Sales revenue trend chart"
                preserveAspectRatio="none"
            >
                {[0, 1, 2, 3].map((lineIndex) => {
                    const y = padding +
                        (lineIndex / 3) * (height - padding * 2);

                    return (
                        <line
                            key={lineIndex}
                            x1={padding}
                            y1={y}
                            x2={width - padding}
                            y2={y}
                            className="seller-chart-gridline"
                        />
                    );
                })}

                <path d={area} className="seller-chart-area" />
                <path d={line} className="seller-chart-line" />

                {points.map((point, index) => (
                    <circle
                        key={index}
                        cx={point.x}
                        cy={point.y}
                        r="4"
                        className="seller-chart-point"
                    >
                        <title>
                            {data[index].periodStart}: {money(point.value)}
                        </title>
                    </circle>
                ))}
            </svg>

            <div className="seller-chart-labels">
                {data.map((item, index) => {
                    const showLabel =
                        data.length <= 10 ||
                        index === 0 ||
                        index === data.length - 1 ||
                        index % Math.ceil(data.length / 6) === 0;

                    return (
                        <span key={`${item.periodStart}-${index}`}>
                            {showLabel
                                ? new Date(`${item.periodStart}T00:00:00`)
                                    .toLocaleDateString('en-IN', {
                                        day: 'numeric',
                                        month: 'short',
                                    })
                                : ''}
                        </span>
                    );
                })}
            </div>
        </div>
    );
}

export default function SellerAnalyticsPanel() {
    const [period, setPeriod] = useState('DAILY');
    const [days, setDays] = useState(30);
    const [summary, setSummary] = useState(null);
    const [sales, setSales] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    const refresh = useCallback(async () => {
        setLoading(true);
        setError('');

        try {
            const [summaryData, salesData] = await Promise.all([
                getSellerAnalyticsSummary(days),
                getSellerSales(period, days),
            ]);

            setSummary(summaryData || {});
            setSales(Array.isArray(salesData) ? salesData : []);
        } catch (err) {
            setError(
                err?.response?.data?.message ||
                err?.response?.data?.error ||
                err?.message ||
                'Unable to load seller analytics.'
            );
        } finally {
            setLoading(false);
        }
    }, [days, period]);

    useEffect(() => {
        refresh();
    }, [refresh]);

    return (
        <section className="seller-analytics-panel">
            <div className="seller-analytics-heading">
                <div>
                    <span>BUSINESS PERFORMANCE</span>
                    <h2>Sales analytics</h2>
                    <p>Track paid orders, revenue and inventory value.</p>
                </div>

                <div className="seller-analytics-filters">
                    <select
                        aria-label="Sales grouping"
                        value={period}
                        onChange={(event) => setPeriod(event.target.value)}
                    >
                        <option value="DAILY">Daily</option>
                        <option value="WEEKLY">Weekly</option>
                        <option value="MONTHLY">Monthly</option>
                    </select>

                    <select
                        aria-label="Analytics date range"
                        value={days}
                        onChange={(event) => setDays(Number(event.target.value))}
                    >
                        <option value={7}>Last 7 days</option>
                        <option value={30}>Last 30 days</option>
                        <option value={90}>Last 90 days</option>
                        <option value={365}>Last 365 days</option>
                    </select>
                </div>
            </div>

            {error && (
                <div className="seller-analytics-error" role="alert">
                    {error}
                    <button type="button" onClick={refresh}>Retry</button>
                </div>
            )}

            <div className="seller-analytics-stats">
                <article>
                    <span>Paid revenue</span>
                    <strong>{money(summary?.grossSales)}</strong>
                </article>

                <article>
                    <span>Paid orders</span>
                    <strong>{number(summary?.paidOrders)}</strong>
                </article>

                <article>
                    <span>Units sold</span>
                    <strong>{number(summary?.unitsSold)}</strong>
                </article>

                <article>
                    <span>Inventory value</span>
                    <strong>{money(summary?.inventoryValue)}</strong>
                </article>
            </div>

            <div className="seller-analytics-chart-card">
                <div className="seller-analytics-chart-heading">
                    <div>
                        <h3>Revenue trend</h3>
                        <p>
                            {period.toLowerCase()} revenue · Last {days} days
                        </p>
                    </div>
                    {loading && <span>Loading…</span>}
                </div>

                <SalesChart data={sales} />
            </div>

            <div className="seller-analytics-table-card">
                <h3>Sales breakdown</h3>

                {sales.length === 0 ? (
                    <div className="seller-analytics-empty">
                        No sales records for the selected period.
                    </div>
                ) : (
                    <div className="seller-analytics-table-scroll">
                        <table>
                            <thead>
                            <tr>
                                <th>Period start</th>
                                <th>Paid orders</th>
                                <th>Units sold</th>
                                <th>Revenue</th>
                            </tr>
                            </thead>
                            <tbody>
                            {sales.map((item) => (
                                <tr key={item.periodStart}>
                                    <td>{item.periodStart}</td>
                                    <td>{number(item.paidOrders)}</td>
                                    <td>{number(item.unitsSold)}</td>
                                    <td>{money(item.grossSales)}</td>
                                </tr>
                            ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </div>
        </section>
    );
}
