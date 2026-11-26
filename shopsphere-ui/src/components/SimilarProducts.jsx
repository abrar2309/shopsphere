
import { useEffect, useState } from 'react';
import ProductImage from './ProductImage';
import { searchBgeProducts } from '../api/productApi';
import './SimilarProducts.css';

const formatPrice = (price) =>
    new Intl.NumberFormat('en-IN', {
        style: 'currency',
        currency: 'INR',
        maximumFractionDigits: 2,
    }).format(Number(price ?? 0));

function getSimilarityLabel(value) {
    const score = Number(value);

    if (!Number.isFinite(score)) return null;

    return `${Math.round(Math.max(0, Math.min(1, score)) * 100)}% match`;
}

export default function SimilarProducts({ product, onProductSelect }) {
    const [recommendations, setRecommendations] = useState([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    useEffect(() => {
        let cancelled = false;

        async function loadRecommendations() {
            if (!product?.id || !product?.name) {
                setRecommendations([]);
                return;
            }

            setLoading(true);
            setError('');
            setRecommendations([]);

            const query = [
                product.name,
                product.description || '',
                product.category?.name || product.category || '',
                'similar products and alternatives',
            ]
                .filter(Boolean)
                .join(' ')
                .slice(0, 500);

            try {
                const results = await searchBgeProducts(
                    query,
                    10,
                    typeof product.category === 'string'
                        ? product.category
                        : product.category?.name
                );

                if (cancelled) return;

                const currentId = String(product.id);

                const filtered = results
                    .filter((item) => String(item.id) !== currentId)
                    .filter((item) => Number(item.stock ?? 0) > 0)
                    .slice(0, 4);

                setRecommendations(filtered);
            } catch (requestError) {
                console.error(
                    'Failed to load similar products:',
                    requestError
                );

                if (!cancelled) {
                    setError(
                        'Similar product recommendations are temporarily unavailable.'
                    );
                }
            } finally {
                if (!cancelled) {
                    setLoading(false);
                }
            }
        }

        loadRecommendations();

        return () => {
            cancelled = true;
        };
    }, [
        product?.id,
        product?.name,
        product?.description,
        product?.category,
    ]);

    return (
        <section className="similar-products">
            <div className="similar-products-heading">
                <span className="eyebrow">POWERED BY SHOPSPHERE AI</span>
                <h3>You may also like</h3>
                <p>Products discovered using semantic similarity.</p>
            </div>

            {loading && (
                <p className="similar-products-status" role="status">
                    Finding products you might like...
                </p>
            )}

            {error && (
                <p className="similar-products-status" role="status">
                    {error}
                </p>
            )}

            {!loading && !error && recommendations.length === 0 && (
                <p className="similar-products-status">
                    No similar in-stock products found yet.
                </p>
            )}

            {recommendations.length > 0 && (
                <div className="similar-products-grid">
                    {recommendations.map((item) => (
                        <article
                            className="similar-product-card"
                            key={item.id}
                        >
                            <div className="similar-product-image-wrap">
                                <ProductImage
                                    product={item}
                                    className="similar-product-image"
                                    alt={item.name}
                                />
                            </div>                            <div className="similar-product-category">
                                {item.category || 'ShopSphere discovery'}
                            </div>

                            <h4>{item.name}</h4>

                            <p className="similar-product-description">
                                {item.description ||
                                    'Discover this product from ShopSphere.'}
                            </p>

                            <strong className="similar-product-price">
                                {formatPrice(item.price)}
                            </strong>
                            {item.stock === null || item.stock === undefined ? (
                                <span className="stock-status stock-unknown">
                                    Stock information unavailable
                                </span>
                            ) : Number(item.stock) <= 0 ? (
                                <span className="stock-status stock-out">
                                    Out of stock
                                </span>
                            ) : Number(item.stock) <= 5 ? (
                                <span className="stock-status stock-low">
                                    Only {item.stock} left in stock!
                                </span>
                            ) : (
                                <span className="stock-status stock-available">
                                    In stock
                                </span>
                            )}

                            <div className="similar-product-footer">
                                <span className="similar-product-match">
                                    {getSimilarityLabel(item.similarity)}
                                </span>

                                <button
                                    type="button"
                                    onClick={() => onProductSelect?.(item)}
                                >
                                    View product
                                </button>
                            </div>
                        </article>
                    ))}
                </div>
            )}
        </section>
    );
}

