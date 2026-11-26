
import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import apiClient from '../api/client';

import {
    getAdminDashboardSummary,
    getAdminPaymentHistory,
    getAdminUsers,
    getAdminCategories,
    getAdminRecentOrders,
    deleteAdminUser,
} from '../api/adminApi';

import { getProducts } from '../api/productApi';

import './AdminDashboard.css';

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

function extractList(data) {
    if (Array.isArray(data)) return data;
    if (Array.isArray(data?.content)) return data.content;
    if (Array.isArray(data?.items)) return data.items;
    if (Array.isArray(data?.data)) return data.data;
    return [];
}

function Metric({ label, value, detail }) {
    return (
        <article className="admin-metric">
            <span>{label}</span>
            <strong>{value ?? 0}</strong>
            {detail && <small>{detail}</small>}
        </article>
    );
}

export default function AdminDashboard() {
    const [orders, setOrders] = useState([]);
    const [summary, setSummary] = useState(null);
    const [history, setHistory] = useState([]);
    const [users, setUsers] = useState([]);
    const [categories, setCategories] = useState([]);
    const [adminProducts, setAdminProducts] = useState([]);

    const [categoryName, setCategoryName] = useState('');
    const [editingCategoryId, setEditingCategoryId] = useState(null);

    const [loading, setLoading] = useState(true);
    const [savingCategory, setSavingCategory] = useState(false);
    const [deletingId, setDeletingId] = useState(null);
    const [deletingCategoryId, setDeletingCategoryId] = useState(null);

    const [error, setError] = useState('');
    const [message, setMessage] = useState('');

    const refresh = useCallback(async () => {
        setLoading(true);
        setError('');

        try {
            const [
                summaryData,
                historyData,
                userData,
                categoryData,
                orderData,
                productData,
            ] = await Promise.all([
                getAdminDashboardSummary(),
                getAdminPaymentHistory(50),
                getAdminUsers(),
                getAdminCategories(),
                getAdminRecentOrders(50),
                getProducts({
                    page: 0,
                    size: 50,
                    sortBy: 'id',
                    direction: 'desc',
                }),
            ]);

            setSummary(summaryData);
            setHistory(extractList(historyData));
            setUsers(extractList(userData));
            setCategories(extractList(categoryData));
            setOrders(extractList(orderData));
            setAdminProducts(extractList(productData));
        } catch (e) {
            setError(
                e.response?.data?.message ||
                e.response?.data?.detail ||
                e.message ||
                'Unable to load the administrator dashboard.'
            );
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        refresh();
    }, [refresh]);

    async function handleDeleteUser(user) {
        if (!user?.id) return;

        const confirmed = window.confirm(
            `Delete user ${user.email || user.id}? This action cannot be undone.`
        );

        if (!confirmed) return;

        setDeletingId(user.id);
        setError('');
        setMessage('');

        try {
            await deleteAdminUser(user.id);

            setMessage(`User ${user.email || user.id} deleted successfully.`);

            await refresh();
        } catch (e) {
            setError(
                e.response?.data?.message ||
                'Could not delete this user.'
            );
        } finally {
            setDeletingId(null);
        }
    }

    function handleEditCategory(category) {
        setEditingCategoryId(category.id);
        setCategoryName(category.name || '');
        setError('');
        setMessage('');
    }

    function cancelCategoryEdit() {
        setEditingCategoryId(null);
        setCategoryName('');
    }

    async function handleSaveCategory(event) {
        event.preventDefault();

        const name = categoryName.trim();

        if (name.length < 3 || name.length > 50) {
            setError('Category name must contain 3–50 characters.');
            return;
        }

        setSavingCategory(true);
        setError('');
        setMessage('');

        try {
            if (editingCategoryId !== null) {
                await apiClient.put(
                    `/categories/${editingCategoryId}`,
                    { name }
                );

                setMessage('Category updated successfully.');
            } else {
                await apiClient.post('/categories', { name });

                setMessage('Category created successfully.');
            }

            cancelCategoryEdit();
            await refresh();
        } catch (e) {
            setError(
                e.response?.data?.message ||
                e.response?.data?.detail ||
                'Unable to save category.'
            );
        } finally {
            setSavingCategory(false);
        }
    }

    async function handleDeleteCategory(category) {
        if (!category?.id) return;

        const confirmed = window.confirm(
            `Delete category "${category.name}"?`
        );

        if (!confirmed) return;

        setDeletingCategoryId(category.id);
        setError('');
        setMessage('');

        try {
            await apiClient.delete(`/categories/${category.id}`);

            if (editingCategoryId === category.id) {
                cancelCategoryEdit();
            }

            setMessage(`Category "${category.name}" deleted.`);
            await refresh();
        } catch (e) {
            setError(
                e.response?.data?.message ||
                'Unable to delete this category. Products may still be using it.'
            );
        } finally {
            setDeletingCategoryId(null);
        }
    }

    return (
        <main className="admin-dashboard">

            <header className="admin-dashboard-header">
                <div>
                    <p className="admin-eyebrow">
                        SHOPSPHERE CONTROL CENTER
                    </p>

                    <h1>Admin dashboard</h1>

                    <p>
                        Monitor platform activity, users, payments,
                        products, categories and orders.
                    </p>
                </div>

                <div className="admin-header-actions">
                    <Link
                        to="/admin/payments"
                        className="admin-primary-link"
                    >
                        Review payments
                    </Link>

                    <button
                        type="button"
                        onClick={refresh}
                        disabled={loading}
                    >
                        {loading ? 'Refreshing...' : 'Refresh'}
                    </button>
                </div>
            </header>

            {error && (
                <div className="admin-error" role="alert">
                    {error}
                </div>
            )}

            {message && (
                <div className="admin-message" role="status">
                    {message}
                </div>
            )}

            {loading && !summary ? (
                <p className="admin-loading">
                    Loading platform overview...
                </p>
            ) : (
                <>

                    {/* PLATFORM OVERVIEW */}

                    <section className="admin-metrics-grid">

                        <Metric
                            label="Total users"
                            value={summary?.totalUsers}
                        />

                        <Metric
                            label="Customers"
                            value={summary?.totalCustomers}
                        />

                        <Metric
                            label="Sellers"
                            value={summary?.totalSellers}
                        />

                        <Metric
                            label="Products"
                            value={summary?.totalProducts}
                        />

                        <Metric
                            label="Categories"
                            value={summary?.totalCategories}
                        />

                        <Metric
                            label="Orders"
                            value={summary?.totalOrders}
                        />

                        <Metric
                            label="Pending payments"
                            value={summary?.pendingPayments}
                            detail="Awaiting administrator review"
                        />

                        <Metric
                            label="Successful revenue"
                            value={money(summary?.successfulRevenue)}
                            detail="Payments marked SUCCESS"
                        />

                    </section>

                    {/* PAYMENT VERIFICATION HISTORY */}

                    <section className="admin-section">

                        <div className="admin-section-heading">
                            <div>
                                <h2>Payment verification history</h2>
                                <p>
                                    Latest recorded payment status changes.
                                </p>
                            </div>

                            <Link to="/admin/payments">
                                Open payment queue
                            </Link>
                        </div>

                        {history.length === 0 ? (
                            <p className="admin-empty">
                                No payment history found.
                            </p>
                        ) : (
                            <div className="admin-table-wrap">
                                <table className="admin-table">
                                    <thead>
                                    <tr>
                                        <th>Payment</th>
                                        <th>Order</th>
                                        <th>Method</th>
                                        <th>Previous</th>
                                        <th>New status</th>
                                        <th>Verified by</th>
                                        <th>Changed</th>
                                        <th>Admin note</th>
                                    </tr>
                                    </thead>

                                    <tbody>
                                    {history.map((item) => (
                                        <tr key={item.id}>
                                            <td>#{item.paymentId}</td>
                                            <td>#{item.orderId ?? '—'}</td>
                                            <td>{item.paymentMethod || '—'}</td>
                                            <td>{item.oldStatus || '—'}</td>

                                            <td>
                                                    <span
                                                        className={`admin-status ${String(
                                                            item.newStatus || ''
                                                        ).toLowerCase()}`}
                                                    >
                                                        {item.newStatus || '—'}
                                                    </span>
                                            </td>

                                            <td>{item.verifiedBy || '—'}</td>
                                            <td>{dateTime(item.changedAt)}</td>
                                            <td>{item.adminNote || '—'}</td>
                                        </tr>
                                    ))}
                                    </tbody>
                                </table>
                            </div>
                        )}

                    </section>

                    {/* USER MANAGEMENT */}

                    <section className="admin-section">

                        <div className="admin-section-heading">
                            <div>
                                <h2>User management</h2>
                                <p>
                                    Review registered accounts and remove
                                    accounts when necessary.
                                </p>
                            </div>

                            <span>{users.length} accounts loaded</span>
                        </div>

                        {users.length === 0 ? (
                            <p className="admin-empty">
                                No users found.
                            </p>
                        ) : (
                            <div className="admin-table-wrap">
                                <table className="admin-table">
                                    <thead>
                                    <tr>
                                        <th>User ID</th>
                                        <th>Name</th>
                                        <th>Email</th>
                                        <th>Phone</th>
                                        <th>Action</th>
                                    </tr>
                                    </thead>

                                    <tbody>
                                    {users.map((user) => (
                                        <tr key={user.id}>
                                            <td>{user.id}</td>

                                            <td>
                                                {[
                                                    user.firstName,
                                                    user.lastName,
                                                ]
                                                    .filter(Boolean)
                                                    .join(' ') || '—'}
                                            </td>

                                            <td>{user.email || '—'}</td>
                                            <td>{user.phoneNumber || '—'}</td>

                                            <td>
                                                <button
                                                    type="button"
                                                    className="admin-delete-button"
                                                    disabled={deletingId === user.id}
                                                    onClick={() =>
                                                        handleDeleteUser(user)
                                                    }
                                                >
                                                    {deletingId === user.id
                                                        ? 'Deleting...'
                                                        : 'Delete'}
                                                </button>
                                            </td>
                                        </tr>
                                    ))}
                                    </tbody>
                                </table>
                            </div>
                        )}

                    </section>

                    {/* CATEGORY MANAGEMENT */}

                    <section className="admin-section">

                        <div className="admin-section-heading">
                            <div>
                                <h2>Category management</h2>
                                <p>
                                    Create, rename and remove product categories.
                                </p>
                            </div>

                            <span>{categories.length} categories</span>
                        </div>

                        <form
                            className="admin-category-form"
                            onSubmit={handleSaveCategory}
                        >
                            <input
                                type="text"
                                value={categoryName}
                                onChange={(event) =>
                                    setCategoryName(event.target.value)
                                }
                                placeholder="Enter category name"
                                minLength={3}
                                maxLength={50}
                                required
                            />

                            <button
                                type="submit"
                                disabled={savingCategory}
                            >
                                {savingCategory
                                    ? 'Saving...'
                                    : editingCategoryId !== null
                                        ? 'Update category'
                                        : 'Add category'}
                            </button>

                            {editingCategoryId !== null && (
                                <button
                                    type="button"
                                    onClick={cancelCategoryEdit}
                                    disabled={savingCategory}
                                >
                                    Cancel
                                </button>
                            )}
                        </form>

                        {categories.length === 0 ? (
                            <p className="admin-empty">
                                No categories found.
                            </p>
                        ) : (
                            <div className="admin-table-wrap">
                                <table className="admin-table">
                                    <thead>
                                    <tr>
                                        <th>ID</th>
                                        <th>Category name</th>
                                        <th>Actions</th>
                                    </tr>
                                    </thead>

                                    <tbody>
                                    {categories.map((category) => (
                                        <tr key={category.id}>
                                            <td>{category.id}</td>
                                            <td>{category.name}</td>

                                            <td>
                                                <button
                                                    type="button"
                                                    onClick={() =>
                                                        handleEditCategory(category)
                                                    }
                                                    disabled={
                                                        deletingCategoryId === category.id
                                                    }
                                                >
                                                    Edit
                                                </button>

                                                <button
                                                    type="button"
                                                    className="admin-delete-button"
                                                    disabled={
                                                        deletingCategoryId === category.id
                                                    }
                                                    onClick={() =>
                                                        handleDeleteCategory(category)
                                                    }
                                                >
                                                    {deletingCategoryId === category.id
                                                        ? 'Deleting...'
                                                        : 'Delete'}
                                                </button>
                                            </td>
                                        </tr>
                                    ))}
                                    </tbody>
                                </table>
                            </div>
                        )}

                    </section>

                    {/* PRODUCT CATALOG */}

                    <section className="admin-section">

                        <div className="admin-section-heading">
                            <div>
                                <h2>Product catalog</h2>
                                <p>
                                    Review products currently listed on ShopSphere.
                                </p>
                            </div>

                            <span>
                                {adminProducts.length} products loaded
                            </span>
                        </div>

                        {adminProducts.length === 0 ? (
                            <p className="admin-empty">
                                No products found.
                            </p>
                        ) : (
                            <div className="admin-table-wrap">
                                <table className="admin-table">
                                    <thead>
                                    <tr>
                                        <th>Product ID</th>
                                        <th>Product</th>
                                        <th>Price</th>
                                        <th>Stock</th>
                                        <th>Category</th>
                                    </tr>
                                    </thead>

                                    <tbody>
                                    {adminProducts.map((product) => (
                                        <tr key={product.id}>
                                            <td>{product.id}</td>
                                            <td>{product.name || '—'}</td>
                                            <td>{money(product.price)}</td>
                                            <td>{product.stock ?? 0}</td>
                                            <td>
                                                {product.categoryName ||
                                                    product.category?.name ||
                                                    product.category ||
                                                    '—'}
                                            </td>
                                        </tr>
                                    ))}
                                    </tbody>
                                </table>
                            </div>
                        )}

                    </section>

                    {/* RECENT ORDERS */}

                    <section className="admin-section">

                        <div className="admin-section-heading">
                            <div>
                                <h2>Recent orders</h2>
                                <p>
                                    Latest customer orders and associated payment statuses.
                                </p>
                            </div>

                            <span>{orders.length} orders loaded</span>
                        </div>

                        {orders.length === 0 ? (
                            <p className="admin-empty">
                                No orders found.
                            </p>
                        ) : (
                            <div className="admin-table-wrap">
                                <table className="admin-table">
                                    <thead>
                                    <tr>
                                        <th>Order</th>
                                        <th>Customer</th>
                                        <th>Email</th>
                                        <th>Items</th>
                                        <th>Total</th>
                                        <th>Order status</th>
                                        <th>Payment</th>
                                        <th>Method</th>
                                    </tr>
                                    </thead>

                                    <tbody>
                                    {orders.map((order) => (
                                        <tr key={order.orderId}>
                                            <td>#{order.orderId}</td>
                                            <td>{order.customerName || '—'}</td>
                                            <td>{order.customerEmail || '—'}</td>
                                            <td>{order.itemCount ?? 0}</td>
                                            <td>{money(order.totalAmount)}</td>
                                            <td>{order.orderStatus || '—'}</td>
                                            <td>{order.paymentStatus || 'NOT CREATED'}</td>
                                            <td>{order.paymentMethod || '—'}</td>
                                        </tr>
                                    ))}
                                    </tbody>
                                </table>
                            </div>
                        )}

                    </section>

                </>
            )}

        </main>
    );
}
