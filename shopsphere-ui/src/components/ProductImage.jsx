import { useState } from 'react';
import { getProductImageUrl } from '../api/productApi';

const fallbackImages = {
    jacket: 'https://images.unsplash.com/photo-1551028719-00167b16eac5?w=900&auto=format&fit=crop&q=85',
    hoodie: 'https://images.unsplash.com/photo-1556821840-3a63f95609a7?w=900&auto=format&fit=crop&q=85',
    clothing: 'https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=900&auto=format&fit=crop&q=85',
    electronics: 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=900&auto=format&fit=crop&q=85',
    accessories: 'https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=900&auto=format&fit=crop&q=85',
    watch: 'https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=900&auto=format&fit=crop&q=85',
    bottle: 'https://images.unsplash.com/photo-1602143407151-7111542de6e8?w=900&auto=format&fit=crop&q=85',
    generic: 'https://images.unsplash.com/photo-1441986300917-64674bd600d8?w=900&auto=format&fit=crop&q=85',
};

function getProductCategory(product) {
    return typeof product?.category === 'object'
        ? product.category?.name
        : product?.category;
}

function getFallbackImage(product) {
    const category = getProductCategory(product);
    const value = `${category ?? ''} ${product?.name ?? ''}`.toLowerCase();

    if (/jacket|coat|blazer|windbreaker|outerwear/.test(value)) return fallbackImages.jacket;
    if (/hoodie|sweatshirt/.test(value)) return fallbackImages.hoodie;
    // Do not substitute one misleading red shoe photo for every footwear SKU.
    if (/shoe|sneaker|footwear|boot|sandal|trainer/.test(value)) return '';
    if (/headphone|earbud|speaker|electronic|laptop|tablet|charger/.test(value)) return fallbackImages.electronics;
    if (/watch/.test(value)) return fallbackImages.watch;
    if (/backpack|rucksack|bag|wallet|accessor/.test(value)) return fallbackImages.accessories;
    if (/water bottle|bottle|flask/.test(value)) return fallbackImages.bottle;
    if (/shirt|t[ -]?shirt|clothing|apparel|jean|trouser|pant|dress|kurta/.test(value)) return fallbackImages.clothing;
    return fallbackImages.generic;
}

export default function ProductImage({ product, className = '', alt }) {
    const [failedUrl, setFailedUrl] = useState('');
    const galleryCandidate = Array.isArray(product?.images)
        ? (product.images.find((item) => item?.url || item?.imageUrl || item?.imagePath)?.url
            ?? product.images.find((item) => item?.imageUrl)?.imageUrl
            ?? product.images.find((item) => item?.imagePath)?.imagePath)
        : null;

    const imageCandidate = product?.imageUrl
        ?? product?.image_url
        ?? product?.imagePath
        ?? product?.image_path
        ?? galleryCandidate;
    const imagePath = typeof imageCandidate === 'string' ? imageCandidate.trim() : '';
    const uploadedUrl = imagePath ? getProductImageUrl(imagePath) : '';
    const fallbackUrl = getFallbackImage(product);
    const imageUrl = uploadedUrl && failedUrl !== uploadedUrl
        ? uploadedUrl
        : (fallbackUrl && failedUrl !== fallbackUrl ? fallbackUrl : '');

    if (!imageUrl) {
        return (
            <div
                className={`product-image-fallback ${className}`}
                role="img"
                aria-label={alt || product?.name || 'Product image unavailable'}
            >
                <span className="product-image-placeholder-mark" aria-hidden="true">IMAGE</span>
                <small>{product?.name || 'Product image unavailable'}</small>
            </div>
        );
    }

    return (
        <img
            className={className}
            src={imageUrl}
            alt={alt || product?.name || 'ShopSphere product'}
            loading="lazy"
            onError={() => setFailedUrl(imageUrl)}
        />
    );
}