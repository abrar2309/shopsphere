import { useCallback, useEffect, useState } from 'react';
import apiClient from '../api/client';
import { getVariantImages, uploadVariantImages, deleteVariantImage } from '../api/variantApi';
import { getProductImageUrl } from '../api/productApi';
import './SellerProductVariantManager.css';

const emptyForm = {
    size: '',
    color: '',
    price: '',
    stock: '0',
};

const money = (value) =>
    new Intl.NumberFormat('en-IN', {
        style: 'currency',
        currency: 'INR',
        maximumFractionDigits: 2,
    }).format(Number(value ?? 0));

function makeSku(productId, size, color) {
    const clean = (value) => String(value || 'one-size')
        .trim()
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, '-')
        .replace(/^-|-$/g, '');
    return `SS-${productId}-${clean(size)}-${clean(color)}`.slice(0, 80);
}

function getError(error) {
    return error?.response?.data?.message
        || error?.response?.data?.error
        || error?.message
        || 'Variant request failed.';
}

export default function SellerProductVariantManager({ product }) {
    const productId = product?.id;
    const [variants, setVariants] = useState([]);
    const [form, setForm] = useState(emptyForm);
    const [editingId, setEditingId] = useState(null);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState('');
    const [message, setMessage] = useState('');
    const [variantImages, setVariantImages] = useState({});
    const [imageBusy, setImageBusy] = useState(null);

    const loadVariants = useCallback(async () => {
        if (!productId) return;
        setLoading(true);
        setError('');
        try {
            const response = await apiClient.get(`/products/${productId}/variants/seller`);
            const rows = Array.isArray(response.data) ? response.data : [];
            setVariants(rows);
            const imageEntries = await Promise.all(rows.map(async (v) => [v.id, await getVariantImages(productId, v.id).catch(() => [])]));
            setVariantImages(Object.fromEntries(imageEntries));
        } catch (requestError) {
            setError(getError(requestError));
        } finally {
            setLoading(false);
        }
    }, [productId]);

    useEffect(() => {
        loadVariants();
    }, [loadVariants]);

    function change(event) {
        const { name, value } = event.target;
        setForm((current) => ({ ...current, [name]: value }));
    }

    function reset() {
        setForm(emptyForm);
        setEditingId(null);
    }

    async function submit(event) {
        event.preventDefault();
        setError('');
        setMessage('');

        const size = form.size.trim();
        const color = form.color.trim();
        const stock = Number(form.stock);
        const price = form.price.trim() === '' ? null : Number(form.price);

        if (!color) {
            setError('Enter a colour for this variant.');
            return;
        }
        if (!Number.isInteger(stock) || stock < 0) {
            setError('Stock must be a whole number and cannot be negative.');
            return;
        }
        if (price !== null && (!Number.isFinite(price) || price <= 0)) {
            setError('Enter a valid variant price, or leave it blank to use the product price.');
            return;
        }

        const payload = {
            sku: makeSku(productId, size, color),
            size: size || null,
            color,
            price,
            stock,
            active: true,
        };

        setSaving(true);
        try {
            if (editingId !== null) {
                await apiClient.put(`/products/${productId}/variants/${editingId}`, payload);
                setMessage('Variant updated.');
            } else {
                await apiClient.post(`/products/${productId}/variants`, payload);
                setMessage('Variant added.');
            }
            reset();
            await loadVariants();
        } catch (requestError) {
            setError(getError(requestError));
        } finally {
            setSaving(false);
        }
    }

    function startEdit(variant) {
        setEditingId(variant.id);
        setForm({
            size: variant.size || '',
            color: variant.color || '',
            price: variant.price == null ? '' : String(variant.price),
            stock: String(variant.stock ?? 0),
        });
        setError('');
        setMessage('');
    }

    async function deactivate(variant) {
        if (!window.confirm(`Deactivate ${variant.color || ''} ${variant.size || ''} (${variant.sku})?`)) return;
        setError('');
        setMessage('');
        try {
            await apiClient.delete(`/products/${productId}/variants/${variant.id}`);
            setMessage('Variant deactivated.');
            if (editingId === variant.id) reset();
            await loadVariants();
        } catch (requestError) {
            setError(getError(requestError));
        }
    }

    return (
        <section className="seller-variant-manager">
            <div className="seller-variant-heading">
                <h4>Variants</h4>
                <span>{variants.length} total</span>
            </div>

            {error && <p className="seller-variant-error" role="alert">{error}</p>}
            {message && <p className="seller-variant-success" role="status">{message}</p>}

            <form className="seller-variant-form" onSubmit={submit}>
                <label>
                    Size
                    <input name="size" value={form.size} onChange={change}
                        maxLength={40} placeholder="e.g. M /  UK 8 / One Size" />
                </label>
                <label>
                    Colour *
                    <input name="color" value={form.color} onChange={change}
                        maxLength={40} placeholder="e.g. Navy Blue" required />
                </label>
                <label>
                    Variant price (₹)
                    <input name="price" type="number" min="0.01" step="0.01"
                        value={form.price} onChange={change}
                        placeholder={`Use product price (${product.price})`} />
                </label>
                <label>
                    Stock *
                    <input name="stock" type="number" min="0" step="1"
                        value={form.stock} onChange={change} required />
                </label>
                <div className="seller-variant-actions">
                    <button type="submit" disabled={saving}>
                        {saving ? 'Saving…' : editingId !== null ? 'Save variant' : 'Add variant'}
                    </button>
                    {editingId !== null && (
                        <button type="button" className="secondary" onClick={reset}>Cancel</button>
                    )}
                </div>
            </form>

            {loading ? (
                <p className="seller-variant-muted">Loading variants…</p>
            ) : variants.length === 0 ? (
                <p className="seller-variant-muted">No variants yet. Add a size/colour option above.</p>
            ) : (
                <div className="seller-variant-list">
                    {variants.map((variant) => (
                        <article className={!variant.active ? 'inactive' : ''} key={variant.id}>
                            <div>
                                <strong>{[variant.color, variant.size].filter(Boolean).join(' / ') || 'Standard'}</strong>
                                <small>{variant.sku}</small>
                                <small>{money(variant.price ?? product.price)} · Stock: {variant.stock}</small>
                                <div className="seller-variant-image-tools">
                                    <label className="seller-variant-image-upload">Add colour images<input type="file" accept="image/jpeg,image/png" multiple disabled={imageBusy === variant.id} onChange={async (event) => { const chosen = Array.from(event.target.files || []); if (!chosen.length) return; setImageBusy(variant.id); setError(''); try { const rows = await uploadVariantImages(productId, variant.id, chosen); setVariantImages((old) => ({ ...old, [variant.id]: rows })); setMessage('Variant images uploaded.'); } catch (e) { setError(getError(e)); } finally { setImageBusy(null); event.target.value = ''; } }} /></label>
                                    <div className="seller-variant-image-thumbs">{(variantImages[variant.id] || []).map((img) => <span key={img.id}><img src={getProductImageUrl(img.imageUrl)} alt={`${variant.color || 'Variant'} view`} /><button type="button" aria-label="Delete variant image" onClick={async () => { try { await deleteVariantImage(productId, variant.id, img.id); setVariantImages((old) => ({ ...old, [variant.id]: (old[variant.id] || []).filter((x) => x.id !== img.id) })); } catch (e) { setError(getError(e)); } }}>×</button></span>)}</div>
                                </div>
                                {!variant.active && <small>Inactive</small>}
                            </div>
                            <div className="seller-variant-row-actions">
                                {variant.active && (
                                    <>
                                        <button type="button" onClick={() => startEdit(variant)}>Edit</button>
                                        <button type="button" className="danger" onClick={() => deactivate(variant)}>Deactivate</button>
                                    </>
                                )}
                            </div>
                        </article>
                    ))}
                </div>
            )}
            <p className="seller-variant-note">
                SKU is generated automatically. Blank variant price inherits the product price.
            </p>
        </section>
    );
}