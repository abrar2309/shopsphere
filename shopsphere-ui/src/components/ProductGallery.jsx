import { useCallback, useEffect, useState } from 'react';
import { getProductImageUrl } from '../api/productApi';
import { getProductImages } from '../api/productGalleryApi';
import { getVariantImages } from '../api/variantApi';

import './ProductGallery.css';

export default function ProductGallery({
                                           product,
                                           className = '',
                                           allowRefresh = false,
                                           variantId = null,
                                       }) {
    const [images, setImages] = useState([]);
    const [selectedUrl, setSelectedUrl] = useState('');
    const [loading, setLoading] = useState(true);
    const [failed, setFailed] = useState(false);

    const loadImages = useCallback(async () => {
        if (!product?.id) {
            setLoading(false);
            return;
        }

        setLoading(true);

        try {
            const result = variantId ? await getVariantImages(product.id, variantId) : await getProductImages(product.id);
            const list = Array.isArray(result) ? result : [];

            const ordered = [...list].sort((a, b) => {
                if (Boolean(a.primary) !== Boolean(b.primary)) {
                    return a.primary ? -1 : 1;
                }

                return Number(a.displayOrder ?? 0) -
                    Number(b.displayOrder ?? 0);
            });

            setImages(ordered);

            const primary = ordered.find((image) => image.primary);
            const first = primary || ordered[0];

            setSelectedUrl(
                first?.imageUrl
                    ? getProductImageUrl(first.imageUrl)
                    : ''
            );
            setFailed(false);
        } catch {
            // A product may have only its legacy products.image_url image.
            setImages([]);
            setSelectedUrl('');
        } finally {
            setLoading(false);
        }
    }, [product?.id, variantId]);

    useEffect(() => {
        loadImages();
    }, [loadImages]);

    const galleryImage = selectedUrl || (
        product?.imageUrl || product?.image_url
            ? getProductImageUrl(product.imageUrl ?? product.image_url)
            : ''
    );

    if (loading) {
        return (
            <div className={`product-gallery-loading ${className}`}>
                Loading product images...
            </div>
        );
    }

    return (
        <div className={`product-gallery ${className}`}>
            <div className="product-gallery-main">
                {galleryImage && !failed ? (
                    <img
                        src={galleryImage}
                        alt={product?.name || 'Product'}
                        onError={() => setFailed(true)}
                    />
                ) : (
                    <div className="product-gallery-placeholder">
                        <span aria-hidden="true">
                            {getProductSymbol(product)}
                        </span>
                        <strong>{product?.name || 'ShopSphere product'}</strong>
                        <small>Product image coming soon</small>
                    </div>
                )}
            </div>

            {images.length > 1 && (
                <div
                    className="product-gallery-thumbnails"
                    aria-label="Product images"
                >
                    {images.map((image) => {
                        const url = getProductImageUrl(image.imageUrl);

                        return (
                            <button
                                type="button"
                                key={image.id}
                                className={
                                    selectedUrl === url
                                        ? 'product-gallery-thumb active'
                                        : 'product-gallery-thumb'
                                }
                                onClick={() => {
                                    setSelectedUrl(url);
                                    setFailed(false);
                                }}
                                aria-label={
                                    image.primary
                                        ? 'View primary product image'
                                        : 'View product image'
                                }
                            >
                                <img src={url} alt="" />
                            </button>
                        );
                    })}
                </div>
            )}

            {allowRefresh && (
                <button
                    type="button"
                    className="product-gallery-refresh"
                    onClick={loadImages}
                >
                    Refresh images
                </button>
            )}
        </div>
    );
}

function getProductSymbol(product) {
    const text = `${product?.name ?? ''} ${product?.category ?? ''}`
        .toLowerCase();

    if (/shirt|tshirt|t-shirt|top|clothing|apparel/.test(text)) return '👕';
    if (/shoe|sneaker|footwear/.test(text)) return '👟';
    if (/watch/.test(text)) return '⌚';
    if (/bag|backpack/.test(text)) return '👜';
    if (/dress|fashion/.test(text)) return '👗';
    if (/phone|mobile|electronic/.test(text)) return '📱';
    if (/book/.test(text)) return '📚';

    return '🛍️';
}