import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import ProductImage from '../components/ProductImage';
import {
    getWishlist,
    removeFromWishlist,
} from '../api/wishlistApi';

import './Wishlist.css';

const money = (value) =>
    new Intl.NumberFormat('en-IN', {
        style: 'currency',
        currency: 'INR',
        maximumFractionDigits: 2,
    }).format(Number(value ?? 0));

export default function Wishlist() {
    const navigate = useNavigate();

    const [items, setItems] = useState([]);
    const [loading, setLoading] = useState(true);
    const [removingId, setRemovingId] = useState(null);
    const [error, setError] = useState('');

    const loadWishlist = useCallback(async () => {
        setLoading(true);
        setError('');

        try {
            const data = await getWishlist();
            setItems(Array.isArray(data) ? data : []);
        } catch (requestError) {
            if (requestError.response?.status === 401 ||
                requestError.response?.status === 403) {
                navigate('/login', { replace: true });
                return;
            }

            setError(
                requestError.response?.data?.message ||
                'Could not load your wishlist.'
            );
        } finally {
            setLoading(false);
        }
    }, [navigate]);

    useEffect(() => {
        loadWishlist();
    }, [loadWishlist]);

    async function handleRemove(productId) {
        setRemovingId(productId);
        setError('');

        try {
            await removeFromWishlist(productId);

            setItems((previous) =>
                previous.filter((item) => item.productId !== productId)
            );
        } catch (requestError) {
            setError(
                requestError.response?.data?.message ||
                'Could not remove this product.'
            );
        } finally {
            setRemovingId(null);
        }
    }

    if (loading) {
        return (
            <main className="wishlist-page">
                <p>Loading your wishlist...</p>
            </main>
        );
    }

    return (
        <main className="wishlist-page">
            <header className="wishlist-header">
                <div>
                    <span className="wishlist-eyebrow">YOUR COLLECTION</span>
                    <h1>My Wishlist</h1>
                    <p>Products you have saved for later.</p>
                </div>

                <Link to="/" className="wishlist-continue">
                    Continue shopping
                </Link>
            </header>

            {error && (
                <p className="wishlist-error" role="alert">{error}</p>
            )}

            {items.length === 0 ? (
                <section className="wishlist-empty">
                    <div aria-hidden="true">♡</div>
                    <h2>Your wishlist is waiting</h2>
                    <p>Save products you love and find them here later.</p>
                    <Link to="/">Explore products</Link>
                </section>
            ) : (
                <>
                    <p className="wishlist-count">
                        {items.length} saved product{items.length === 1 ? '' : 's'}
                    </p>

                    <section className="wishlist-grid">
                        {items.map((item) => (
                            <article
                                className="wishlist-card"
                                key={item.productId}
                            >
                                <ProductImage
                                    product={item}
                                    className="wishlist-product-image"
                                />

                                <div className="wishlist-product-info">
                                    <span>{item.category || 'ShopSphere'}</span>
                                    <h2>{item.name}</h2>
                                    <p>{item.description}</p>

                                    <strong>{money(item.price)}</strong>

                                    <small>
                                        {Number(item.stock ?? 0) > 0
                                            ? `${item.stock} in stock`
                                            : 'Currently out of stock'}
                                    </small>

                                    <button
                                        type="button"
                                        disabled={removingId === item.productId}
                                        onClick={() => handleRemove(item.productId)}
                                    >
                                        {removingId === item.productId
                                            ? 'Removing...'
                                            : 'Remove from wishlist'}
                                    </button>
                                </div>
                            </article>
                        ))}
                    </section>
                </>
            )}
        </main>
    );
}