
import { useEffect, useState } from 'react';

import {
    getPersonalizedRecommendations,
} from '../api/recommendationApi';

import ProductImage from './ProductImage';

import './PersonalizedRecommendations.css';

function formatPrice(price) {
    return new Intl.NumberFormat('en-IN', {
        style: 'currency',
        currency: 'INR',
        maximumFractionDigits: 2,
    }).format(Number(price ?? 0));
}

export default function PersonalizedRecommendations({
                                                        onSelectProduct,
                                                        refreshKey = 0,
                                                        title = 'Picked for you',
                                                    }) {
    const [products, setProducts] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        let active = true;

        async function loadRecommendations() {
                        // This endpoint is customer-personalized and requires authentication.
            // Avoid sending requests without a valid customer session.
            if (!sessionStorage.getItem('shopsphere_token')) {
                setProducts([]);
                setError('Log in to see recommendations selected for you.');
                setLoading(false);
                return;
            }
setLoading(true);
            setError('');

            try {
                const data =
                    await getPersonalizedRecommendations(8);

                if (active) {
                    setProducts(data);
                }
            } catch (err) {
                if (!active) return;

                const status = err?.response?.status;

                if (status === 401 || status === 403) {
                    setError(
                        'Log in to see recommendations selected for you.'
                    );
                } else {
                    setError(
                        err?.response?.data?.message
                        || 'Could not load recommendations right now.'
                    );
                }

                setProducts([]);
            } finally {
                if (active) {
                    setLoading(false);
                }
            }
        }

        loadRecommendations();

        return () => {
            active = false;
        };
    }, [refreshKey]);

    if (loading) {
        return (
            <section className="personalized-section">
                <div className="personalized-heading">
                    <div>
                        <span className="personalized-eyebrow">
                            SHOPSPHERE AI
                        </span>

                        <h2>{title}</h2>
                    </div>
                </div>

                <div className="personalized-status">
                    Finding products you may like...
                </div>
            </section>
        );
    }

    if (error) {
        return (
            <section className="personalized-section">
                <div className="personalized-heading">
                    <div>
                        <span className="personalized-eyebrow">
                            SHOPSPHERE AI
                        </span>

                        <h2>{title}</h2>
                    </div>
                </div>

                <p className="personalized-message">
                    {error}
                </p>
            </section>
        );
    }

    if (products.length === 0) {
        return null;
    }

    return (
        <section className="personalized-section">
            <div className="personalized-heading">
                <div>
                    <span className="personalized-eyebrow">
                        SHOPSPHERE AI
                    </span>

                    <h2>{title}</h2>

                    <p>
                        Recommendations based on your shopping activity.
                    </p>
                </div>
            </div>

            <div className="personalized-grid">
                {products.map((product) => (
                    <button
                        type="button"
                        className="personalized-card"
                        key={product.id}
                        onClick={() => onSelectProduct?.(product)}
                    >
                        <div className="personalized-image-wrap">
                            <ProductImage
                                product={product}
                                className="personalized-image"
                                alt={product.name}
                            />
                        </div>

                        <div className="personalized-card-content">
                            <span className="personalized-category">
                                {product.category || 'ShopSphere'}
                            </span>

                            <h3>{product.name}</h3>

                            <p className="personalized-price">
                                {formatPrice(product.price)}
                            </p>

                            <span className="personalized-reason">
                                {product.reason}
                            </span>
                        </div>
                    </button>
                ))}
            </div>
        </section>
    );
}
