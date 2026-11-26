
import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import ProductImage from '../components/ProductImage';

import {
    getMyProducts,
    createSellerProduct,
    updateSellerProduct,
    deleteSellerProduct,
    uploadProductImage,
} from '../api/sellerApi';

import './SellerDashboard.css';
import SellerOperationsPanel from '../components/SellerOperationsPanel';
import SellerProductGalleryManager from '../components/SellerProductGalleryManager';
import SellerProductVariantManager from '../components/SellerProductVariantManager';
import SellerInventoryPanel from '../components/SellerInventoryPanel';
const emptyForm = {
    name: '',
    description: '',
    price: '',
    stock: '',
    color: '',
};
import SellerAnalyticsPanel from '../components/SellerAnalyticsPanel';
const money = (value) =>
    new Intl.NumberFormat('en-IN', {
        style: 'currency',
        currency: 'INR',
        maximumFractionDigits: 2,
    }).format(Number(value ?? 0));

function getErrorMessage(error) {
    const data = error?.response?.data;

    if (typeof data === 'string') return data;

    return (
        data?.message ||
        data?.error ||
        error?.message ||
        'Something went wrong. Please try again.'
    );
}

export default function SellerDashboard() {
    const navigate = useNavigate();

    const [products, setProducts] = useState([]);
    const [form, setForm] = useState(emptyForm);
    const [editingId, setEditingId] = useState(null);

    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [uploadingId, setUploadingId] = useState(null);
    const [deletingId, setDeletingId] = useState(null);

    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');

    const token = sessionStorage.getItem('shopsphere_token');
    const role = sessionStorage.getItem('shopsphere_role') || '';

    useEffect(() => {
        if (!token) {
            navigate('/login', { replace: true });
            return;
        }

        if (!['SELLER', 'ROLE_SELLER'].includes(role)) {
            navigate('/', { replace: true });
        }
    }, [token, role, navigate]);

    async function loadProducts() {
        setLoading(true);
        setError('');

        try {
            const data = await getMyProducts();

            setProducts(Array.isArray(data) ? data : []);
        } catch (requestError) {
            setError(getErrorMessage(requestError));
        } finally {
            setLoading(false);
        }
    }

    useEffect(() => {
        if (
            token &&
            ['SELLER', 'ROLE_SELLER'].includes(role)
        ) {
            loadProducts();
        }
    }, [token, role]);

    function handleChange(event) {
        const { name, value } = event.target;

        setForm((previous) => ({
            ...previous,
            [name]: value,
        }));
    }

    function resetForm() {
        setForm(emptyForm);
        setEditingId(null);
    }

    function startEditing(product) {
        setEditingId(product.id);

        setForm({
            name: product.name ?? '',
            description: product.description ?? '',
            price: product.price ?? '',
            stock: product.stock ?? '',
            color: product.color ?? '',
        });

        setError('');
        setSuccess('');

        window.scrollTo({
            top: 0,
            behavior: 'smooth',
        });
    }

    async function handleSubmit(event) {
        event.preventDefault();

        setError('');
        setSuccess('');

        const payload = {
            name: form.name.trim(),
            description: form.description.trim(),
            price: Number(form.price),
            stock: Number(form.stock),
            color: form.color.trim() || null,
        };

        if (
            payload.name.length < 3 ||
            payload.name.length > 100
        ) {
            setError('Product name must contain 3–100 characters.');
            return;
        }

        if (
            payload.description.length < 10 ||
            payload.description.length > 500
        ) {
            setError('Description must contain 10–500 characters.');
            return;
        }

        if (
            !Number.isFinite(payload.price) ||
            payload.price <= 0
        ) {
            setError('Enter a valid price greater than zero.');
            return;
        }

        if (
            !Number.isInteger(payload.stock) ||
            payload.stock < 0
        ) {
            setError('Stock must be a whole number and cannot be negative.');
            return;
        }

        setSaving(true);

        try {
            if (editingId !== null) {
                await updateSellerProduct(editingId, payload);
                setSuccess('Product updated successfully.');
            } else {
                await createSellerProduct(payload);
                setSuccess('Product created successfully.');
            }

            resetForm();
            await loadProducts();
        } catch (requestError) {
            setError(getErrorMessage(requestError));
        } finally {
            setSaving(false);
        }
    }

    async function handleDelete(product) {
        const confirmed = window.confirm(
            `Are you sure you want to delete "${product.name}"?`
        );

        if (!confirmed) return;

        setDeletingId(product.id);
        setError('');
        setSuccess('');

        try {
            await deleteSellerProduct(product.id);

            setProducts((previous) =>
                previous.filter((item) => item.id !== product.id)
            );

            if (editingId === product.id) {
                resetForm();
            }

            setSuccess('Product deleted successfully.');
        } catch (requestError) {
            setError(getErrorMessage(requestError));
        } finally {
            setDeletingId(null);
        }
    }

    async function handleImageUpload(productId, event) {
        const file = event.target.files?.[0];

        if (!file) return;

        setError('');
        setSuccess('');

        if (!['image/jpeg', 'image/png', 'image/webp', 'image/avif'].includes(file.type)) {
            setError('Please select a JPEG, PNG, WebP, or AVIF image.');
            event.target.value = '';
            return;
        }

        if (file.size > 5 * 1024 * 1024) {
            setError('Image size must not exceed 5 MB.');
            event.target.value = '';
            return;
        }

        setUploadingId(productId);

        try {
            // Backend storage accepts JPEG and PNG. Convert WebP/AVIF in-browser.
            let uploadFile = file;

            if (file.type === 'image/webp' || file.type === 'image/avif') {
                let bitmap;

                try {
                    if (typeof createImageBitmap !== 'function') {
                        throw new Error('This browser cannot convert WebP/AVIF images. Please use JPEG or PNG.');
                    }

                    bitmap = await createImageBitmap(file);

                    if (!bitmap.width || !bitmap.height || bitmap.width > 6000 || bitmap.height > 6000) {
                        throw new Error('Image dimensions must not exceed 6000 Ã— 6000 pixels.');
                    }

                    const canvas = document.createElement('canvas');
                    canvas.width = bitmap.width;
                    canvas.height = bitmap.height;

                    const context = canvas.getContext('2d');
                    if (!context) {
                        throw new Error('Could not prepare the selected image for upload.');
                    }

                    context.drawImage(bitmap, 0, 0);

                    const jpegBlob = await new Promise((resolve, reject) => {
                        canvas.toBlob(
                            (blob) => blob ? resolve(blob) : reject(new Error('Image conversion failed. Please try a JPEG or PNG image.')),
                            'image/jpeg',
                            0.92
                        );
                    });

                    uploadFile = new File(
                        [jpegBlob],
                        file.name.replace(/\.(avif|webp)$/i, '') + '.jpg',
                        { type: 'image/jpeg', lastModified: Date.now() }
                    );

                    if (uploadFile.size > 5 * 1024 * 1024) {
                        throw new Error('Converted image exceeds 5 MB. Please choose a smaller image.');
                    }
                } catch (conversionError) {
                    setError(conversionError?.message || 'Could not convert this image. Please use JPEG or PNG.');
                    return;
                } finally {
                    if (bitmap) bitmap.close();
                }
            }

            const updatedProduct = await uploadProductImage(
                productId,
                uploadFile
            );

            setProducts((previous) =>
                previous.map((product) =>
                    product.id === productId
                        ? { ...product, ...updatedProduct }
                        : product
                )
            );

            setSuccess('Product image uploaded successfully.');
        } catch (requestError) {
            setError(getErrorMessage(requestError));
        } finally {
            setUploadingId(null);
            event.target.value = '';
        }
    }

    if (
        !token ||
        !['SELLER', 'ROLE_SELLER'].includes(role)
    ) {
        return null;
    }

    return (
        <main className="seller-dashboard">
            <header className="seller-header">
                <Link to="/" className="seller-brand">
                    Shop<span>Sphere</span>
                </Link>

                <nav>
                    <Link to="/">Storefront</Link>
                    <Link to="/orders">My Orders</Link>

                    <button
                        type="button"
                        onClick={() => {
                            sessionStorage.removeItem('shopsphere_token');
                            sessionStorage.removeItem('shopsphere_email');
                            sessionStorage.removeItem('shopsphere_role');
                            navigate('/login', { replace: true });
                        }}
                    >
                        Log out
                    </button>
                </nav>
            </header>

            <section className="seller-welcome">
                <span className="seller-eyebrow">
                    SELLER WORKSPACE
                </span>

                <h1>Manage your products.</h1>

                <p>
                    Create products, update inventory and manage
                    your product images from one place.
                </p>

                <span className="seller-email">
                    {sessionStorage.getItem('shopsphere_email')}
                </span>
            </section>
            <SellerOperationsPanel />
            <section className="seller-stats">
                <article>
                    <span>Total products</span>
                    <strong>{products.length}</strong>
                </article>

                <article>
                    <span>Total inventory</span>
                    <strong>
                        {products.reduce(
                            (total, product) =>
                                total + Number(product.stock ?? 0),
                            0
                        )}
                    </strong>
                </article>

                <article>
                    <span>Out of stock</span>
                    <strong>
                        {products.filter(
                            (product) => Number(product.stock) === 0
                        ).length}
                    </strong>
                </article>
            </section>

            {error && (
                <div className="seller-alert seller-error" role="alert">
                    {error}
                </div>
            )}

            {success && (
                <div className="seller-alert seller-success" role="status">
                    {success}
                </div>
            )}
            <SellerInventoryPanel products={products} />
            <SellerInventoryPanel products={products} />

            <SellerAnalyticsPanel />
            <section className="seller-form-section">
                <div className="seller-section-heading">
                    <div>
                        <span className="seller-eyebrow">
                            PRODUCT MANAGEMENT
                        </span>

                        <h2>
                            {editingId !== null
                                ? 'Edit product'
                                : 'Add a new product'}
                        </h2>
                    </div>

                    {editingId !== null && (
                        <button
                            className="seller-secondary-button"
                            type="button"
                            onClick={resetForm}
                        >
                            Cancel editing
                        </button>
                    )}
                </div>

                <form
                    className="seller-product-form"
                    onSubmit={handleSubmit}
                >
                    <label>
                        Product name
                        <input
                            name="name"
                            value={form.name}
                            onChange={handleChange}
                            minLength={3}
                            maxLength={100}
                            placeholder="e.g. Everyday Backpack"
                            required
                        />
                    </label>

                    <label>
                        Description
                        <textarea
                            name="description"
                            value={form.description}
                            onChange={handleChange}
                            minLength={10}
                            maxLength={500}
                            rows={4}
                            placeholder="Describe your product..."
                            required
                        />
                    </label>

                    <div className="seller-form-row">
                        <label>
                            Price (₹)
                            <input
                                name="price"
                                type="number"
                                min="0.01"
                                step="0.01"
                                value={form.price}
                                onChange={handleChange}
                                placeholder="1499"
                                required
                            />
                        </label>

                        <label>
                            Available stock
                            <input
                                name="stock"
                                type="number"
                                min="0"
                                step="1"
                                value={form.stock}
                                onChange={handleChange}
                                placeholder="10"
                                required
                            />
                        </label>
                        <label>
                            Product colour (optional)
                            <input
                                name="color"
                                type="text"
                                value={form.color}
                                onChange={handleChange}
                                maxLength={40}
                                placeholder="e.g. red, navy, black"
                            />
                        </label>
                    </div>

                    <button
                        type="submit"
                        className="seller-primary-button"
                        disabled={saving}
                    >
                        {saving
                            ? 'Saving...'
                            : editingId !== null
                                ? 'Save changes'
                                : 'Create product'}
                    </button>
                </form>
            </section>

            <section className="seller-products-section">
                <div className="seller-section-heading">
                    <div>
                        <span className="seller-eyebrow">
                            YOUR CATALOGUE
                        </span>

                        <h2>Your products</h2>
                    </div>

                    <button
                        type="button"
                        className="seller-secondary-button"
                        onClick={loadProducts}
                        disabled={loading}
                    >
                        {loading ? 'Refreshing...' : 'Refresh'}
                    </button>
                </div>

                {loading ? (
                    <p className="seller-empty">
                        Loading your products...
                    </p>
                ) : products.length === 0 ? (
                    <div className="seller-empty">
                        <h3>No products yet</h3>
                        <p>
                            Create your first product using the form above.
                        </p>
                    </div>
                ) : (
                    <div className="seller-product-grid">
                        {products.map((product) => (
                            <article
                                className="seller-product-card"
                                key={product.id}
                            >
                                <ProductImage
                                    product={product}
                                    className="seller-product-image"
                                />

                                <div className="seller-product-content">
                                    <span className="seller-product-id">
                                        PRODUCT #{product.id}
                                    </span>

                                    <h3>{product.name}</h3>

                                    <p className="seller-product-description">
                                        {product.description}
                                    </p>

                                    <SellerProductVariantManager product={product} />

                                    <div className="seller-product-details">
                                        <strong>
                                            {money(product.price)}
                                        </strong>

                                        <span>
                                            Stock: {product.stock}
                                        </span>
                                    </div>

                                    <label className="seller-image-button">
                                        {uploadingId === product.id
                                            ? 'Uploading image...'
                                            : 'Upload / replace image'}

                                        <input
                                            type="file"
                                            accept="image/png,image/jpeg,image/webp,image/avif"
                                            disabled={uploadingId !== null}
                                            onChange={(event) =>
                                                handleImageUpload(
                                                    product.id,
                                                    event
                                                )
                                            }
                                        />
                                    </label>

                                    <div className="seller-card-actions">
                                        <button
                                            type="button"
                                            onClick={() =>
                                                startEditing(product)
                                            }
                                        >
                                            Edit
                                        </button>

                                        <button
                                            type="button"
                                            className="seller-delete-button"
                                            disabled={
                                                deletingId === product.id
                                            }
                                            onClick={() =>
                                                handleDelete(product)
                                            }
                                        >
                                            {deletingId === product.id
                                                ? 'Deleting...'
                                                : 'Delete'}
                                        </button>
                                    </div>
                                </div>
                            </article>
                        ))}
                    </div>
                )}
            </section>

            <footer className="seller-footer">
                ShopSphere Seller Workspace
            </footer>
        </main>
    );
}
