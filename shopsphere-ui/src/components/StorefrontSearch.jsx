import { useEffect, useRef, useState } from 'react';
import { searchProducts } from '../api/productApi';
import './StorefrontSearch.css';

function unwrapProducts(data) {
    if (Array.isArray(data)) return data;
    if (Array.isArray(data?.content)) return data.content;
    if (Array.isArray(data?.products)) return data.products;
    return [];
}

function formatPrice(value) {
    return new Intl.NumberFormat('en-IN', {
        style: 'currency',
        currency: 'INR',
        maximumFractionDigits: 0,
    }).format(Number(value ?? 0));
}

export default function StorefrontSearch({ value, products = [], onChange, onSearch }) {
    const [focused, setFocused] = useState(false);
    const [suggestions, setSuggestions] = useState([]);
    const [loading, setLoading] = useState(false);
    const [activeIndex, setActiveIndex] = useState(-1);
    const requestRef = useRef(0);

    useEffect(() => {
        const query = String(value ?? '').trim();
        if (!focused || query.length < 2) {
            setSuggestions([]);
            setLoading(false);
            setActiveIndex(-1);
            return undefined;
        }

        const requestId = ++requestRef.current;
        const timer = window.setTimeout(async () => {
            setLoading(true);
            try {
                const data = await searchProducts(query);
                if (requestId !== requestRef.current) return;
                const matches = unwrapProducts(data)
                    .filter((item) => item && item.id != null && item.name)
                    .filter((item) => Number(item.stock ?? 0) > 0)
                    .slice(0, 7);
                setSuggestions(matches);
                setActiveIndex(-1);
            } catch {
                if (requestId === requestRef.current) setSuggestions([]);
            } finally {
                if (requestId === requestRef.current) setLoading(false);
            }
        }, 220);

        return () => window.clearTimeout(timer);
    }, [value, focused]);

    function choose(product) {
        if (!product) return;
        const exactName = String(product.name);
        onChange?.(exactName);
        setFocused(false);
        setSuggestions([]);
        onSearch?.(exactName);
    }

    function submit(event) {
        event.preventDefault();
        const query = String(value ?? '').trim();
        if (!query) return;
        if (activeIndex >= 0 && suggestions[activeIndex]) {
            choose(suggestions[activeIndex]);
            return;
        }
        setFocused(false);
        onSearch?.(query);
    }

    function handleKeyDown(event) {
        if (event.key === 'ArrowDown' && suggestions.length) {
            event.preventDefault();
            setActiveIndex((current) => (current + 1) % suggestions.length);
        } else if (event.key === 'ArrowUp' && suggestions.length) {
            event.preventDefault();
            setActiveIndex((current) => current <= 0 ? suggestions.length - 1 : current - 1);
        } else if (event.key === 'Escape') {
            setFocused(false);
            setSuggestions([]);
            setActiveIndex(-1);
        } else if (event.key === 'Enter' && activeIndex >= 0 && suggestions[activeIndex]) {
            event.preventDefault();
            choose(suggestions[activeIndex]);
        }
    }

    return (
        <section className="storefront-search-section" aria-label="Search ShopSphere">
            <div className="storefront-search-heading">
                <span>FIND YOUR NEXT FAVOURITE</span>
                <h2>What are you looking for?</h2>
                <p>Search the live catalogue or describe what you need.</p>
            </div>

            <form className="storefront-search-form" onSubmit={submit}>
                <span className="storefront-search-icon" aria-hidden="true">âŒ•</span>
                <input
                    type="search"
                    value={value}
                    onChange={(event) => onChange?.(event.target.value)}
                    onFocus={() => setFocused(true)}
                    onBlur={() => {
                        window.setTimeout(() => {
                            setFocused(false);
                            setSuggestions([]);
                        }, 160);
                    }}
                    onKeyDown={handleKeyDown}
                    placeholder="Try: blue shoes under â‚¹2,000"
                    aria-label="Search products"
                    aria-autocomplete="list"
                    aria-expanded={focused && suggestions.length > 0}
                    aria-controls="shopsphere-live-suggestions"
                    autoComplete="off"
                    maxLength={500}
                />
                {value && (
                    <button
                        type="button"
                        className="storefront-search-clear"
                        onMouseDown={(event) => event.preventDefault()}
                        onClick={() => {
                            onChange?.('');
                            setSuggestions([]);
                            setActiveIndex(-1);
                        }}
                        aria-label="Clear search"
                    >
                        Ã—
                    </button>
                )}
                <button className="storefront-search-submit" type="submit">Search</button>
            </form>

            {focused && (loading || suggestions.length > 0) && (
                <div
                    id="shopsphere-live-suggestions"
                    className="storefront-search-suggestions"
                    role="listbox"
                    aria-label="Matching catalogue products"
                >
                    {loading && <div className="storefront-search-status">Searching the catalogueâ€¦</div>}
                    {!loading && suggestions.map((product, index) => (
                        <button
                            type="button"
                            className={`storefront-search-suggestion${activeIndex === index ? ' is-active' : ''}`}
                            key={product.id}
                            role="option"
                            aria-selected={activeIndex === index}
                            onMouseDown={(event) => event.preventDefault()}
                            onClick={() => choose(product)}
                        >
                            <span className="storefront-search-suggestion-main">
                                <strong>{product.name}</strong>
                                <small>
                                    {product.category?.name ?? product.category ?? 'ShopSphere product'}
                                    {product.stock != null ? ` Â· ${product.stock} in stock` : ''}
                                </small>
                            </span>
                            {product.price != null && (
                                <span className="storefront-search-suggestion-price">
                                    {formatPrice(product.price)}
                                </span>
                            )}
                        </button>
                    ))}
                    {!loading && suggestions.length === 0 && (
                        <div className="storefront-search-status">No matching catalogue products.</div>
                    )}
                </div>
            )}
        </section>
    );
}