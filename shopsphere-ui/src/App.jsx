import './App.css';
import './catalog-pagination.css';
import './product-images.css';
import Login from './pages/Login';
import AdminDashboard from './pages/AdminDashboard';
import PersonalizedRecommendations from './components/PersonalizedRecommendations';
import ForgotPassword from './pages/ForgotPassword';
import { useEffect, useState } from 'react';
import { Routes, Route, Link, Navigate } from 'react-router-dom';
import ProductImage from './components/ProductImage';
import AIShoppingAssistant from './components/AIShoppingAssistant';
import StorefrontSearch from './components/StorefrontSearch';
import Register from './pages/Register';
import Checkout from './pages/Checkout';
import Orders from './pages/Orders';
import OrderDetails from './pages/OrderDetails';
import SellerDashboard from './pages/SellerDashboard';
import AdminPayments from './pages/AdminPayments';
import Wishlist from './pages/Wishlist';
import ProductGallery from './components/ProductGallery';
import SimilarProducts from './components/SimilarProducts';
import {
    getWishlist,
    addWishlistItem,
    removeWishlistItem,
} from './api/wishlistApi';
import {
    getProducts,
    getProductById,
    getProductsByCategory,
    getCategories,
    uploadProductImage,
    searchBgeProducts,
} from './api/productApi';

import {
    getCart,
    addCartItem,
    updateCartItem,
    removeCartItem,
    clearCart,
} from './api/cartApi';
import { getProductVariants } from './api/variantApi';

const productColors = ['lavender', 'peach', 'mint', 'blue'];

const formatPrice = (price) =>
    new Intl.NumberFormat('en-IN', {
        style: 'currency',
        currency: 'INR',
        maximumFractionDigits: 2,
    }).format(Number(price ?? 0));

function getCartItems(cart) {
    if (Array.isArray(cart)) return cart;
    if (Array.isArray(cart?.items)) return cart.items;
    if (Array.isArray(cart?.cartItems)) return cart.cartItems;
    if (Array.isArray(cart?.cart?.items)) return cart.cart.items;
    return [];
}

function getCartCount(cart) {
    return getCartItems(cart).reduce(
        (total, item) => total + Number(item.quantity ?? 0),
        0
    );
}

function getCartItemId(item) {
    return item.productId ?? item.product?.id ?? item.id;
}

function parseNaturalLanguagePriceQuery(input) {
    let query = String(input ?? '').trim();
    let minPrice;
    let maxPrice;

    const currency = '(?:â‚¹|rs\\.?|inr|\\$)?\\s*';
    const amount = '([0-9][0-9,]*(?:\\.[0-9]+)?\\s*k?)';
    const toNumber = (value) => {
        const cleaned = String(value).toLowerCase().replace(/,/g, '').replace(/\s/g, '');
        const multiplier = cleaned.endsWith('k') ? 1000 : 1;
        const numeric = Number(cleaned.replace(/k$/, ''));
        return Number.isFinite(numeric) ? numeric * multiplier : undefined;
    };

    const betweenPattern = new RegExp(
        '\\bbetween\\s+' + currency + amount +
        '\\s+(?:and|to|-)\\s+' + currency + amount + '\\b',
        'i'
    );
    const betweenMatch = query.match(betweenPattern);

    if (betweenMatch) {
        minPrice = toNumber(betweenMatch[1]);
        maxPrice = toNumber(betweenMatch[2]);
        query = query.replace(betweenMatch[0], ' ');
    } else {
        const maxPattern = new RegExp(
            '\\b(?:below|under|less\\s+than|upto|up\\s+to|at\\s+most|not\\s+more\\s+than|within|max(?:imum)?|budget(?:\\s+of)?)\\s*' +
            currency + amount + '\\b',
            'i'
        );
        const minPattern = new RegExp(
            '\\b(?:above|over|more\\s+than|at\\s+least|not\\s+less\\s+than|min(?:imum)?)\\s*' +
            currency + amount + '\\b',
            'i'
        );
        const maxMatch = query.match(maxPattern);
        const minMatch = query.match(minPattern);

        if (maxMatch) {
            maxPrice = toNumber(maxMatch[1]);
            query = query.replace(maxMatch[0], ' ');
        }
        if (minMatch) {
            minPrice = toNumber(minMatch[1]);
            query = query.replace(minMatch[0], ' ');
        }

        if (maxPrice === undefined) {
            const trailingBudget = new RegExp(
                amount + '\\s*(?:budget|max|maximum)\\b',
                'i'
            );
            const trailingMatch = query.match(trailingBudget);
            if (trailingMatch) {
                maxPrice = toNumber(trailingMatch[1]);
                query = query.replace(trailingMatch[0], ' ');
            }
        }
    }

    query = query
        .replace(/\b(?:price|priced|costing|cost|budget)\b/gi, ' ')
        .replace(/[â‚¹$]/g, ' ')
        .replace(/\b(?:rs\.?|inr)\b/gi, ' ')
        .replace(/\s+/g, ' ')
        .trim();

    return { query: query || 'products', minPrice, maxPrice };
}function inferBgeCategory(query) {
    const value = String(query ?? '').toLowerCase();

    // More specific product intent must win over broad keyword matches.
    if (/\b(backpacks?|rucksacks?|handbags?|wallets?|bags?|backpacking)\b/.test(value)) {
        return 'Accessories';
    }

    if (/\b(jackets?|coats?|blazers?|outerwear)\b/.test(value)) {
        return 'Clothing';
    }

    if (/\b(clothes?|clothing|apparel|shirts?|t-?shirts?|hoodies?|sweatshirts?|jeans|trousers|pants|dresses|kurtas?)\b/.test(value)) {
        return 'Clothing';
    }

    if (/\b(shoes?|sneakers?|footwear|boots|sandals)\b/.test(value)) {
        return 'Footwear';
    }

    if (/\b(electronics?|headphones?|earbuds?|speakers?|smart ?watches?|laptops?|tablets?|chargers?)\b/.test(value)) {
        return 'Electronics';
    }

    if (/\b(watches?)\b/.test(value)) {
        return 'Accessories';
    }

    // Broad phrases remain unfiltered rather than being forced into a category.
    return undefined;
}
function filterSpecificProductIntent(products, query) {
    const normalize = (value) => String(value ?? '')
        .toLowerCase()
        .normalize('NFKD')
        .replace(/[\u0300-\u036f]/g, '')
        .replace(/[^a-z0-9]+/g, ' ')
        .trim();

    const text = normalize(query);
    const list = Array.isArray(products) ? products : [];
    if (!text) return list;

    const typeGroups = [
        { pattern: /\b(?:jackets?|coats?|blazers?|windbreakers?|outerwear)\b/, name: /\b(?:jackets?|coats?|blazers?|windbreakers?)\b/ },
        { pattern: /\b(?:hoodies?|sweatshirts?)\b/, name: /\b(?:hoodies?|sweatshirts?)\b/ },
        { pattern: /\b(?:sweaters?|pullovers?|cardigans?)\b/, name: /\b(?:sweaters?|pullovers?|cardigans?)\b/ },
        { pattern: /\b(?:track pants|joggers?)\b/, name: /\b(?:track pants|joggers?)\b/ },
        { pattern: /\b(?:jeans|trousers?|chinos?|leggings?)\b/, name: /\b(?:jeans|trousers?|chinos?|leggings?)\b/ },
        { pattern: /\b(?:t shirts?|tees?)\b/, name: /\b(?:t shirts?|tees?)\b/ },
        { pattern: /\b(?:shirts?|polos?)\b/, name: /\b(?:shirts?|polos?)\b/ },
        { pattern: /\b(?:shoes?|sneakers?|footwear|boots?|sandals?|trainers?)\b/, name: /\b(?:shoes?|sneakers?|footwear|boots?|sandals?|trainers?)\b/ },
        { pattern: /\b(?:backpacks?|rucksacks?)\b/, name: /\b(?:backpacks?|rucksacks?)\b/ },
        { pattern: /\b(?:handbags?|tote bags?)\b/, name: /\b(?:handbags?|tote bags?)\b/ },
        { pattern: /\b(?:headphones?|earbuds?|headsets?)\b/, name: /\b(?:headphones?|earbuds?|headsets?)\b/ },
        { pattern: /\b(?:speakers?)\b/, name: /\b(?:speakers?)\b/ },
        { pattern: /\b(?:watches?|smart watches?)\b/, name: /\b(?:watches?|smart watches?)\b/ },
        { pattern: /\b(?:water bottles?|flasks?)\b/, name: /\b(?:water bottles?|flasks?)\b/ },
        { pattern: /\b(?:wallets?)\b/, name: /\b(?:wallets?)\b/ },
        { pattern: /\b(?:sunglasses?)\b/, name: /\b(?:sunglasses?)\b/ },
        { pattern: /\b(?:laptops?|notebooks?)\b/, name: /\b(?:laptops?|notebooks?)\b/ },
    ];

    const colourAliases = {
        red: ['red', 'maroon', 'burgundy'],
        blue: ['blue', 'navy', 'royal blue', 'sky blue'],
        black: ['black'],
        white: ['white', 'ivory'],
        green: ['green', 'olive'],
        yellow: ['yellow', 'mustard'],
        pink: ['pink'],
        purple: ['purple', 'violet', 'lavender'],
        brown: ['brown', 'tan', 'beige'],
        grey: ['grey', 'gray', 'charcoal'],
        orange: ['orange'],
    };
    const requestedColour = Object.entries(colourAliases).find(([canonical, aliases]) =>
        [canonical, ...aliases].some((alias) => new RegExp(`(^|[^a-z])${alias.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}([^a-z]|$)`, 'i').test(text))
    );
    const sizeMatch = text.match(/\b(?:size\s*)?(xxs|xs|s|m|l|xl|xxl|xxxl|2xl|3xl|4xl|small|medium|large)\b/);
    const requestedSize = sizeMatch?.[1] ?? null;


    const stopWords = new Set([
        'a', 'an', 'the', 'and', 'or', 'for', 'to', 'of', 'in', 'on', 'with',
        'me', 'my', 'show', 'find', 'search', 'looking', 'look', 'want', 'need',
        'get', 'give', 'please', 'some', 'any', 'product', 'products', 'item',
        'items', 'buy', 'shop', 'under', 'below', 'above', 'over', 'upto', 'up', 'size',
        'at', 'most', 'least', 'price', 'priced', 'cost', 'costing', 'cheap',
        'cheapest', 'affordable', 'budget', 'best', 'top', 'good', 'nice',
        'available', 'within', 'maximum', 'minimum', 'not', 'more', 'than',
        'less', 'between', 'rs', 'inr', 'rupees', 'rupee', 'k'
    ]);

    const matchedTypes = typeGroups.filter((group) => group.pattern.test(text));
    let residual = text;
    for (const group of matchedTypes) residual = residual.replace(group.pattern, ' ');
    if (requestedColour) {
        for (const alias of requestedColour[1]) {
            const aliasPattern = new RegExp(`(^|[^a-z])${alias.replace(/[.*+?^${}()|[\\]\\]/g, '\\$&')}([^a-z]|$)`, 'gi');
            residual = residual.replace(aliasPattern, ' ');
        }
    }
    if (requestedSize) {
        residual = residual.replace(/\b(?:size\s*)?(?:xxs|xs|s|m|l|xl|xxl|xxxl|2xl|3xl|4xl|small|medium|large)\b/g, ' ');
    }
    const requiredTerms = residual.split(/\s+/).filter((word) =>
        word && !stopWords.has(word) && !/^\d+$/.test(word)
    );

    const productName = (product) => normalize(product?.name);
    const searchable = (product) => normalize([
        product?.name,
        product?.description,
        product?.shortDescription,
        typeof product?.category === 'object' ? product?.category?.name : product?.category,
        product?.brand,
        product?.color,
        product?.colour,
        ...(Array.isArray(product?.variants) ? product.variants.map((v) => v?.color ?? v?.colour ?? v?.name) : []),
        ...(Array.isArray(product?.productVariants) ? product.productVariants.map((v) => v?.color ?? v?.colour ?? v?.name) : []),
    ].filter(Boolean).join(' '));

    return list.filter((product) => {
        const name = productName(product);
        const allText = searchable(product);

        // Product type may be recorded in the name, description or category.
        if (matchedTypes.length && !matchedTypes.every((group) => {
            const isFootwearIntent = /sneakers?|shoes?|footwear|boots?|sandals?|trainers?/.test(group.pattern.source);
            return isFootwearIntent
                ? /\b(?:shoes?|sneakers?|footwear|boots?|sandals?|trainers?)\b/.test(allText)
                : group.name.test(allText);
        })) {
            return false;
        }

        // BGE already ranks semantic relevance. Do not require every adjective
        // to appear literally in product metadata: this was hiding valid results
        // such as backpacks when the query contained the word "everyday".

        // Size always requires a real available variant. If both colour and size
        // are requested, they must match the same variant row.
        // Colour-only searches may use product.color only when no variants exist.
        if (requestedColour || requestedSize) {
            const variants = Array.isArray(product?.variants)
                ? product.variants
                : Array.isArray(product?.productVariants)
                    ? product.productVariants
                    : [];
            const availableVariants = variants.filter((variant) =>
                variant?.active !== false && Number(variant?.stock ?? 0) > 0
            );
            const aliases = requestedColour?.[1] ?? [];
            const normalizedRequestedSize = requestedSize === 'small' ? 's'
                : requestedSize === 'medium' ? 'm'
                : requestedSize === 'large' ? 'l'
                : requestedSize === '2xl' ? 'xxl'
                : requestedSize === '3xl' ? 'xxxl'
                : requestedSize;

            const matchingVariant = availableVariants.some((variant) => {
                const variantColour = normalize(variant?.color ?? variant?.colour);
                const variantSize = normalize(variant?.size);
                const colourMatches = !requestedColour ||
                    aliases.some((alias) => variantColour.split(/\s+/).includes(normalize(alias)));
                const sizeMatches = !requestedSize ||
                    variantSize === normalizedRequestedSize ||
                    variantSize === requestedSize;
                return colourMatches && sizeMatches;
            });

            const parentColour = normalize(product?.color ?? product?.colour);
            const parentColourMatches = !requestedSize &&
                variants.length === 0 &&
                aliases.some((alias) => parentColour.split(/\s+/).includes(normalize(alias)));

            if (!matchingVariant && !parentColourMatches) return false;
        }

        return true;
    });
}
function HomePage() {
    const [products, setProducts] = useState([]);
    const [categories, setCategories] = useState([]);
    const [wishlistIds, setWishlistIds] = useState([]);
    const [wishlistLoading, setWishlistLoading] = useState(false);
    const [wishlistActionId, setWishlistActionId] = useState(null);
    const [wishlistError, setWishlistError] = useState('');
    const [loadingProducts, setLoadingProducts] = useState(true);
    const [loadingCategories, setLoadingCategories] = useState(true);
    const [productError, setProductError] = useState('');
    const [categoryError, setCategoryError] = useState('');

    const [searchQuery, setSearchQuery] = useState('');
    const [selectedCategory, setSelectedCategory] = useState('All');
    const [sortBy, setSortBy] = useState('featured');
    const [minPrice, setMinPrice] = useState('');
    const [maxPrice, setMaxPrice] = useState('');
    const [page, setPage] = useState(0);
    const [pageInfo, setPageInfo] = useState({ totalElements: 0, totalPages: 0 });
    const [reloadKey, setReloadKey] = useState(0);
    const pageSize = 20;

    const [cart, setCart] = useState(null);
    const [cartLoading, setCartLoading] = useState(false);
    const [cartError, setCartError] = useState('');
    const [cartMessage, setCartMessage] = useState('');
    const [cartOpen, setCartOpen] = useState(false);
    const [cartActionId, setCartActionId] = useState(null);

    const [addingProductId, setAddingProductId] = useState(null);

    const [selectedProduct, setSelectedProduct] = useState(null);
    const [productVariants, setProductVariants] = useState([]);
    const [selectedVariantId, setSelectedVariantId] = useState('');
    const [detailLoading, setDetailLoading] = useState(false);
    const [detailError, setDetailError] = useState('');
    const [imageUploadMessage, setImageUploadMessage] = useState('');
    const [imageUploading, setImageUploading] = useState(false);

    useEffect(() => {
        let cancelled = false;

        async function loadProductVariants() {
            const productId = selectedProduct?.id;
            if (!productId) {
                setProductVariants([]);
                setSelectedVariantId('');
                return;
            }

            setProductVariants([]);
            setSelectedVariantId('');

            try {
                const response = await getProductVariants(productId);
                const rows = Array.isArray(response)
                    ? response
                    : Array.isArray(response?.value)
                        ? response.value
                        : Array.isArray(response?.content)
                            ? response.content
                            : [];
                if (!cancelled) {
                    const activeRows = rows.filter((variant) => variant.active !== false);
                    setProductVariants(activeRows);
                    const query = String(searchQuery ?? '').toLowerCase();
                    const requestedColour = ['black', 'white', 'red', 'blue', 'green', 'yellow', 'pink', 'purple', 'brown', 'grey', 'gray', 'orange']
                        .find((colour) => new RegExp(`\\b${colour}\\b`, 'i').test(query));
                    const sizeMatch = query.match(/\b(?:size\s*)?(xxs|xs|s|m|l|xl|xxl|xxxl|2xl|3xl|4xl|small|medium|large)\b/i);
                    const requestedSize = sizeMatch?.[1]?.toLowerCase();
                    const normalizedSize = requestedSize === 'small' ? 's'
                        : requestedSize === 'medium' ? 'm'
                        : requestedSize === 'large' ? 'l'
                        : requestedSize === '2xl' ? 'xxl'
                        : requestedSize === '3xl' ? 'xxxl'
                        : requestedSize;
                    const match = activeRows.find((variant) =>
                        Number(variant.stock ?? 0) > 0 &&
                        (!requestedColour || String(variant.color ?? variant.colour ?? '').toLowerCase().includes(requestedColour)) &&
                        (!normalizedSize || String(variant.size ?? '').toLowerCase() === normalizedSize)
                    );
                    if (match) setSelectedVariantId(String(match.id));
                }
            } catch (error) {
                console.error('Failed to load product variants:', error);
                if (!cancelled) {
                    setProductVariants([]);
                }
            }
        }

        loadProductVariants();
        return () => { cancelled = true; };
    }, [selectedProduct?.id, searchQuery]);
    useEffect(() => {
        let cancelled = false;

        async function loadWishlist() {
            if (!sessionStorage.getItem('shopsphere_token')) {
                setWishlistIds([]);
                return;
            }

            setWishlistLoading(true);

            try {
                const data = await getWishlist();

                const items = Array.isArray(data)
                    ? data
                    : Array.isArray(data?.items)
                        ? data.items
                        : [];

                if (!cancelled) {
                    setWishlistIds(
                        items
                            .map((item) => Number(item.productId ?? item.product?.id))
                            .filter(Number.isFinite)
                    );
                }
            } catch (error) {
                console.error('Failed to load wishlist:', error);

                if (!cancelled) {
                    setWishlistError(
                        'Unable to load your wishlist. Please try again.'
                    );
                }
            } finally {
                if (!cancelled) setWishlistLoading(false);
            }
        }

        loadWishlist();

        return () => {
            cancelled = true;
        };
    }, []);
    // Load products from the backend. The All Products view uses true
    // server-side pagination and sorting. Category results are currently
    // returned as a List by the existing category endpoint, so those are
    // paginated locally until a paginated category endpoint is introduced.
    async function handleWishlistToggle(product) {
        if (!sessionStorage.getItem('shopsphere_token')) {
            setWishlistError('Please log in to save products to your wishlist.');
            return;
        }

        if (wishlistActionId !== null) return;

        const productId = Number(product.id);
        const alreadySaved = wishlistIds.includes(productId);

        try {
            setWishlistActionId(productId);
            setWishlistError('');

            if (alreadySaved) {
                await removeWishlistItem(productId);

                setWishlistIds((current) =>
                    current.filter((id) => id !== productId)
                );
            } else {
                await addWishlistItem(productId);

                setWishlistIds((current) =>
                    current.includes(productId)
                        ? current
                        : [...current, productId]
                );
            }
        } catch (error) {
            console.error('Wishlist update failed:', error);

            setWishlistError(
                error.response?.data?.message ||
                'Could not update your wishlist. Please try again.'
            );
        } finally {
            setWishlistActionId(null);
        }
    }

    useEffect(() => {
        let cancelled = false;

        async function loadProducts() {
            setLoadingProducts(true);
            setProductError('');

            try {
                const manualMinPrice = minPrice === '' ? undefined : Number(minPrice);
                const manualMaxPrice = maxPrice === '' ? undefined : Number(maxPrice);

                const invalidMinimum = manualMinPrice !== undefined &&
                    (!Number.isFinite(manualMinPrice) || manualMinPrice < 0);
                const invalidMaximum = manualMaxPrice !== undefined &&
                    (!Number.isFinite(manualMaxPrice) || manualMaxPrice < 0);
                const invertedRange = manualMinPrice !== undefined &&
                    manualMaxPrice !== undefined &&
                    manualMinPrice > manualMaxPrice;

                if (invalidMinimum || invalidMaximum || invertedRange) {
                    setProducts([]);
                    setPageInfo({ totalElements: 0, totalPages: 0 });
                    setProductError(
                        'Invalid price range: minimum price cannot be greater than maximum price. Please enter a valid range.'
                    );
                    return;
                }

                const backendSort =
                    sortBy === 'price-low'
                        ? { sortBy: 'price', direction: 'asc' }
                        : sortBy === 'price-high'
                            ? { sortBy: 'price', direction: 'desc' }
                            : sortBy === 'name'
                                ? { sortBy: 'name', direction: 'asc' }
                                : { sortBy: 'id', direction: 'asc' };

                if (searchQuery.trim()) {
                    const parsedSearch = parseNaturalLanguagePriceQuery(searchQuery);
                    const cleanQuery = parsedSearch.query;

                    // Combine natural-language limits with the explicit price controls.
                    const manualMin = minPrice === '' ? undefined : Number(minPrice);
                    const manualMax = maxPrice === '' ? undefined : Number(maxPrice);
                    const effectiveMin = [manualMin, parsedSearch.minPrice]
                        .filter(Number.isFinite)
                        .reduce((current, value) => current === undefined ? value : Math.max(current, value), undefined);
                    const effectiveMax = [manualMax, parsedSearch.maxPrice]
                        .filter(Number.isFinite)
                        .reduce((current, value) => current === undefined ? value : Math.min(current, value), undefined);

                    if (effectiveMin !== undefined && effectiveMax !== undefined &&
                        effectiveMin > effectiveMax) {
                        setProducts([]);
                        setPageInfo({ totalElements: 0, totalPages: 0 });
                        setProductError(
                            'Invalid price range: the minimum price is greater than the maximum price. Please adjust your filters.'
                        );
                        return;
                    }

                    // Do not infer a category from product words: catalogue category
                    // labels are not always consistent (for example, backpacks).
                    const searchCategory = selectedCategory === 'All'
                        ? undefined
                        : selectedCategory;

                    const semanticResults = await searchBgeProducts(
                        cleanQuery,
                        50,
                        searchCategory,
                        effectiveMin,
                        effectiveMax
                    );

                    if (cancelled) return;

                    // Hydrate BGE hits from the product endpoint so each card
                    // receives its own seller-uploaded image and full product data.
                    const enrichedResults = await Promise.all(
                        semanticResults.map(async (hit) => {
                            if (hit?.id === undefined || hit?.id === null) return hit;
                            try {
                                const [fullProduct, variantResponse] = await Promise.all([
                                    getProductById(hit.id),
                                    getProductVariants(hit.id).catch(() => []),
                                ]);
                                const variants = Array.isArray(variantResponse)
                                    ? variantResponse
                                    : Array.isArray(variantResponse?.value)
                                        ? variantResponse.value
                                        : Array.isArray(variantResponse?.content)
                                            ? variantResponse.content
                                            : [];
                                return { ...hit, ...fullProduct, variants };
                            } catch {
                                return hit;
                            }
                        })
                    );

                    let productList = enrichedResults.filter((product) => {
                        const price = Number(product.price ?? 0);
                        const aboveMinimum = effectiveMin === undefined || price >= effectiveMin;
                        const belowMaximum = effectiveMax === undefined || price <= effectiveMax;
                        return aboveMinimum && belowMaximum;
                    });

                    productList = filterSpecificProductIntent(productList, cleanQuery);

                    if (sortBy === 'price-low') {
                        productList = [...productList].sort((a, b) => Number(a.price) - Number(b.price));
                    } else if (sortBy === 'price-high') {
                        productList = [...productList].sort((a, b) => Number(b.price) - Number(a.price));
                    } else if (sortBy === 'name') {
                        productList = [...productList].sort((a, b) => String(a.name).localeCompare(String(b.name)));
                    }

                    setProducts(productList);
                    setPageInfo({ totalElements: productList.length, totalPages: 1 });
                    return;
                }
                const data = await getProducts({
                    page,
                    size: pageSize,
                    ...backendSort,
                    category: selectedCategory === 'All' ? undefined : (/everyday\s+backpacking/i.test(selectedCategory) ? 'Accessories' : selectedCategory),
                    minPrice: minPrice === '' ? undefined : Number(minPrice),
                    maxPrice: maxPrice === '' ? undefined : Number(maxPrice),
                });

                if (cancelled) return;

                const productList = Array.isArray(data)
                    ? data
                    : Array.isArray(data?.content)
                        ? data.content
                        : [];

                setProducts(productList);

                setPageInfo({
                    totalElements: Number(data?.totalElements ?? productList.length),
                    totalPages: Number(data?.totalPages ?? 1),
                });
            } catch (error) {
                console.error('Failed to load products:', error);

                if (!cancelled) {
                    setProducts([]);
                    setPageInfo({ totalElements: 0, totalPages: 0 });
                    setProductError(
                        error.response?.status === 401 ||
                        error.response?.status === 403
                            ? 'Please log in to view products.'
                            : error.response?.data?.message ||
                            'Unable to load products. Check that the backend is running and try again.'
                    );
                }
            } finally {
                if (!cancelled) setLoadingProducts(false);
            }
        }

        loadProducts();

        return () => {
            cancelled = true;
        };
    }, [selectedCategory, page, sortBy, searchQuery, minPrice, maxPrice, reloadKey]);

    // Load categories from the backend.
    useEffect(() => {
        let cancelled = false;

        async function loadCategories() {
            try {
                const data = await getCategories();

                if (!cancelled) {
                    setCategories(Array.isArray(data) ? data : []);
                    setCategoryError('');
                }
            } catch (error) {
                console.error('Failed to load categories:', error);

                if (!cancelled) {
                    setCategories([]);
                    setCategoryError('Unable to load categories.');
                }
            } finally {
                if (!cancelled) setLoadingCategories(false);
            }
        }

        loadCategories();

        return () => {
            cancelled = true;
        };
    }, []);

    // Load the customer's cart only when a login token exists.
    useEffect(() => {
        let cancelled = false;

        async function loadInitialCart() {
            setCartLoading(true);

            try {
                const data = await getCart();

                if (!cancelled) {
                    setCart(data);
                    setCartError('');
                }
            } catch (error) {
                console.error('Failed to load cart:', error);

                if (!cancelled) {
                    setCartError(
                        error.response?.status === 401 ||
                        error.response?.status === 403
                            ? 'Your session may have expired. Please log in again.'
                            : 'Unable to load your bag.'
                    );
                }
            } finally {
                if (!cancelled) setCartLoading(false);
            }
        }

        if (sessionStorage.getItem('shopsphere_token')) {
            loadInitialCart();
        }

        return () => {
            cancelled = true;
        };
    }, []);

    // All filtering, sorting and pagination now happen in the database.
    const filteredProducts = products;
    const visibleProducts = products;
    const displayedTotal = pageInfo.totalElements;
    const displayedTotalPages = pageInfo.totalPages;

    async function refreshCart() {
        const updatedCart = await getCart();
        setCart(updatedCart);
        return updatedCart;
    }

    async function handleAddToCart(product, variantId = null) {
        if (!sessionStorage.getItem('shopsphere_token')) {
            setCartError('Please log in before adding products to your bag.');
            setCartMessage('');
            return;
        }

        if (addingProductId !== null) return;

        try {
            setAddingProductId(product.id);
            setCartError('');
            setCartMessage('');

            await addCartItem(product.id, 1, variantId);
            await refreshCart();

            setCartMessage(`${product.name} added to your bag.`);
        } catch (error) {
            console.error('Failed to add product to cart:', error);

            setCartError(
                error.response?.data?.message ||
                (error.response?.status === 401 ||
                error.response?.status === 403
                    ? 'Your session may have expired. Please log in again.'
                    : 'Could not add this product to your bag. Please try again.')
            );
        } finally {
            setAddingProductId(null);
        }
    }

    async function openCart() {
        setCartOpen(true);
        setCartMessage('');
        setCartError('');

        if (!sessionStorage.getItem('shopsphere_token')) {
            setCart(null);
            setCartError('Please log in to view your shopping bag.');
            return;
        }

        setCartLoading(true);

        try {
            await refreshCart();
        } catch (error) {
            console.error('Failed to refresh cart:', error);

            setCartError(
                error.response?.status === 401 ||
                error.response?.status === 403
                    ? 'Your session may have expired. Please log in again.'
                    : 'Unable to load your shopping bag.'
            );
        } finally {
            setCartLoading(false);
        }
    }

    async function handleQuantityChange(item, nextQuantity) {
        const productId = getCartItemId(item);

        if (!productId || cartActionId !== null) return;

        if (nextQuantity < 1) {
            await handleRemoveItem(item);
            return;
        }

        try {
            setCartActionId(productId);
            setCartError('');
            setCartMessage('');

            await updateCartItem(productId, nextQuantity);
            await refreshCart();
        } catch (error) {
            console.error('Failed to update cart quantity:', error);
            setCartError(
                error.response?.data?.message ||
                'Could not update the quantity. Please try again.'
            );
        } finally {
            setCartActionId(null);
        }
    }

    async function handleRemoveItem(item) {
        const productId = getCartItemId(item);

        if (!productId || cartActionId !== null) return;

        try {
            setCartActionId(productId);
            setCartError('');
            setCartMessage('');

            await removeCartItem(productId);
            await refreshCart();

            setCartMessage('Item removed from your bag.');
        } catch (error) {
            console.error('Failed to remove cart item:', error);
            setCartError(
                error.response?.data?.message ||
                'Could not remove the item. Please try again.'
            );
        } finally {
            setCartActionId(null);
        }
    }

    async function handleClearCart() {
        if (cartActionId !== null) return;

        try {
            setCartActionId('clear');
            setCartError('');
            setCartMessage('');

            await clearCart();
            await refreshCart();

            setCartMessage('Your bag is now empty.');
        } catch (error) {
            console.error('Failed to clear cart:', error);
            setCartError(
                error.response?.data?.message ||
                'Could not clear your bag. Please try again.'
            );
        } finally {
            setCartActionId(null);
        }
    }

    async function openProductDetails(product) {
        setSelectedProduct(product);
        setDetailLoading(true);
        setDetailError('');

        try {
            const data = await getProductById(product.id);
            setSelectedProduct(data);
        } catch (error) {
            console.error('Failed to load product details:', error);
            setDetailError(
                'Could not load the latest product details. Showing the catalogue information instead.'
            );
        } finally {
            setDetailLoading(false);
        }
    }

    function closeProductDetails() {
        setSelectedProduct(null);
        setDetailError('');
    }

    return (
        <div className="app-shell">
            <div className="announcement">
                Discover something you'll love. Welcome to ShopSphere.
            </div>

            <header className="navbar">
                <a className="brand" href="#home" aria-label="ShopSphere home">
                    <span className="brand-mark">S</span>
                    <span>
            Shop<span className="brand-accent">Sphere</span>
          </span>
                </a>

                <nav className="nav-links" aria-label="Main navigation">
                    <a href="#home">Home</a>
                    <a href="#categories">Categories</a>
                    <a href="#products">Discover</a>
                </nav>

                <div className="nav-actions">
                    <button
                        type="button"
                        className="icon-button"
                        aria-label="Focus product search"
                        onClick={() => {
                            document.getElementById('search')?.focus();
                            document.getElementById('products')?.scrollIntoView({
                                behavior: 'smooth',
                            });
                        }}
                    >
                        <span aria-hidden="true">?</span>
                    </button>

                    <Link className="account-link" to="/login">
                        Account
                    </Link>

                    <button
                        type="button"
                        className="cart-button"
                        onClick={openCart}
                        aria-label={`Open shopping bag, ${getCartCount(cart)} items`}
                    >
                        Bag <span className="cart-count">{getCartCount(cart)}</span>
                    </button>
                </div>
            </header>

            <main>
                <section className="hero" id="home">
                    <div className="hero-copy">
                        <span className="eyebrow">YOUR EVERYDAY, ELEVATED</span>

                        <h1>
                            Find your next <span>favourite.</span>
                        </h1>

                        <p>
                            Thoughtfully picked essentials, everyday discoveries and
                            products that make life a little better.
                        </p>

                        <a className="primary-button" href="#products">
                            Explore the collection <span aria-hidden="true">?</span>
                        </a>

                        <div className="hero-note">
                            <span className="note-dot" />
                            A fresh way to discover everyday essentials
                        </div>
                    </div>

                    <div
                        className="hero-art"
                        aria-label="Decorative shopping illustration"
                    >
                        <div className="art-orbit orbit-one" />
                        <div className="art-orbit orbit-two" />
                        <div className="art-card art-card-back">?</div>

                        <div className="art-card art-card-front">
                            <span className="art-sparkle">?</span>
                            <span className="art-label">GOOD FINDS.</span>
                            <span className="art-label art-label-bottom">
                GREAT FEELING.
              </span>
                        </div>

                        <div className="floating-tag tag-one">Made for you</div>
                        <div className="floating-tag tag-two">Discover more ?</div>
                    </div>
                </section>

                <section className="benefits" aria-label="Shopping highlights">
                    <div>
                        <span>?</span>
                        <p>Curated discoveries</p>
                    </div>
                    <div>
                        <span>?</span>
                        <p>Easy browsing</p>
                    </div>
                    <div>
                        <span>?</span>
                        <p>A smoother shopping experience</p>
                    </div>
                </section>

                <section className="section categories-section" id="categories">
                    <div className="section-heading">
                        <div>
                            <span className="eyebrow">EXPLORE YOUR INTERESTS</span>
                            <h2>Shop by category</h2>
                        </div>

                        <button
                            type="button"
                            className="text-link category-reset"
                            onClick={() => {
                                setSelectedCategory('All');
                                setPage(0);
                                document.getElementById('products')?.scrollIntoView({
                                    behavior: 'smooth',
                                });
                            }}
                        >
                            Explore all <span>?</span>
                        </button>
                    </div>

                    {loadingCategories ? (
                        <p role="status">Loading categories...</p>
                    ) : (
                        <div className="category-grid">
                            <button
                                type="button"
                                className={`category-card ${
                                    selectedCategory === 'All' ? 'category-card-active' : ''
                                }`}
                                aria-pressed={selectedCategory === 'All'}
                                onClick={() => { setSearchQuery(''); setSelectedCategory('All'); setPage(0); }}
                            >
                                <span className="category-icon">?</span>
                                <span className="category-name">All products</span>
                                <span className="category-arrow">?</span>
                            </button>

                            {categories.map((category) => (
                                <button
                                    type="button"
                                    className={`category-card ${
                                        selectedCategory === category.name
                                            ? 'category-card-active'
                                            : ''
                                    }`}
                                    key={category.id}
                                    aria-pressed={selectedCategory === category.name}
                                    onClick={() => { setSearchQuery(''); setSelectedCategory(category.name); setPage(0); }}
                                >
                                    <span className="category-icon">?</span>
                                    <span className="category-name">{category.name}</span>
                                    <span className="category-arrow">?</span>
                                </button>
                            ))}
                        </div>
                    )}

                    {categoryError && <p role="alert">{categoryError}</p>}
                </section>

                <StorefrontSearch
                    value={searchQuery}
                    products={products}
                    onChange={(value) => {
                        setSearchQuery(value);
                        setPage(0);
                    }}
                    onSearch={(query) => {
                        setSearchQuery(query);
                        setPage(0);
                        document.getElementById('products')?.scrollIntoView({
                            behavior: 'smooth',
                        });
                    }}
                />
                {/* SHOPSPHERE AI SEMANTIC SHOPPING SEARCH */}
                <AIShoppingAssistant
                    onSearch={(query) => {
                        setSearchQuery(query);
                        setPage(0);
                        document.getElementById('products')?.scrollIntoView({
                            behavior: 'smooth',
                        });
                    }}
                />
                {/* AI PERSONALIZED RECOMMENDATIONS */}
                <PersonalizedRecommendations
                    onSelectProduct={openProductDetails}
                />

                {/* EXISTING PRODUCT CATALOGUE */}
                <section className="section products-section" id="products">
                    <div className="section-heading">
                        <div>
                            <span className="eyebrow">A LITTLE INSPIRATION</span>
                            <h2>
                                {selectedCategory === 'All'
                                    ? 'Discover something new'
                                    : selectedCategory}
                            </h2>
                            <p className="section-description">
                                {searchQuery.trim() ? "Semantic results powered by BGE." : "Explore products from the ShopSphere catalogue."}
                            </p>
                        </div>

                        <span className="product-count">
              {loadingProducts
                  ? 'Loading products...'
                  : `${displayedTotal} products`}
            </span>
                    </div>


                    <div className="catalog-price-filters" aria-label="Filter by price range">
                        <label>
                            <span>Minimum price</span>
                            <input
                                type="number"
                                min="0"
                                inputMode="decimal"
                                placeholder="Min price"
                                value={minPrice}
                                onChange={(event) => { setMinPrice(event.target.value); setPage(0); }}
                            />
                        </label>
                        <label>
                            <span>Maximum price</span>
                            <input
                                type="number"
                                min="0"
                                inputMode="decimal"
                                placeholder="Max price"
                                value={maxPrice}
                                onChange={(event) => { setMaxPrice(event.target.value); setPage(0); }}
                            />
                        </label>
                        <button
                            type="button"
                            className="add-button"
                            onClick={() => { setMinPrice(''); setMaxPrice(''); setPage(0); }}
                        >
                            Clear price filters
                        </button>
                    </div>

                    <div className="catalog-toolbar">
              <span className="catalog-results">
                Showing {visibleProducts.length} of {displayedTotal} product(s)
              </span>
                        <label className="sort-control" htmlFor="product-sort">
                            <span>Sort by</span>
                            <select
                                id="product-sort"
                                value={sortBy}
                                onChange={(event) => { setSortBy(event.target.value); setPage(0); }}
                            >
                                <option value="featured">Featured</option>
                                <option value="price-low">Price: low to high</option>
                                <option value="price-high">Price: high to low</option>
                                <option value="name">Name: A to Z</option>
                            </select>
                        </label>
                    </div>

                    {loadingProducts && (
                        <p role="status">Loading products from ShopSphere...</p>
                    )}

                    {!loadingProducts && productError && (
                        <div role="alert">
                            <p>{productError}</p>
                            {productError.toLowerCase().includes('log in') && (
                                <p>
                                    <Link to="/login">Log in to your account</Link>
                                </p>
                            )}
                            <button
                                type="button"
                                className="add-button"
                                onClick={() => setReloadKey((current) => current + 1)}
                            >
                                Retry
                            </button>
                        </div>
                    )}

                    {!loadingProducts &&
                        !productError &&
                        displayedTotal === 0 && (
                            <p>
                                {selectedCategory === 'All'
                                    ? 'No products are available yet.'
                                    : `No products were found in ${selectedCategory}.`}
                            </p>
                        )}

                    {!loadingProducts &&
                        !productError &&
                        displayedTotal > 0 &&
                        filteredProducts.length === 0 && (
                            <p>No products match your search.</p>
                        )}

                    <div className="product-grid">
                        {!loadingProducts &&
                            !productError &&
                            visibleProducts.map((product, index) => (
                                <article
                                    className="product-card"
                                    key={product.id}
                                    data-product={`${product.name ?? ''} ${
                                        product.description ?? ''
                                    }`.toLowerCase()}
                                >
                                    <button
                                        type="button"
                                        className={`product-art ${
                                            productColors[index % productColors.length]
                                        } product-art-clickable`}
                                        onClick={() => openProductDetails(product)}
                                        aria-label={`View details for ${product.name}`}
                                    >
                                        <span className="product-badge">{product.similarity !== undefined ? "BGE match" : "Discover"}</span>
                                        <ProductImage
                                            product={product}
                                            className="product-card-image"
                                            alt={product.name}
                                        />
                                        <span className="product-view-hint">View details ?</span>
                                    </button>

                                    <div className="product-info">
                    <span className="product-category">
                      {product.category || (selectedCategory === 'All' ? 'ShopSphere' : selectedCategory)}
                    </span>

                                        <button
                                            type="button"
                                            className="product-title-button"
                                            onClick={() => openProductDetails(product)}
                                        >
                                            <h3>{product.name}</h3>
                                        </button>

                                        <p>{product.description}</p>

                                        {product.stock === null || product.stock === undefined ? (
                                            <span className="stock-status stock-unknown">
                                Stock information unavailable
                              </span>
                                        ) : Number(product.stock) <= 0 ? (
                                            <span className="stock-status stock-out">
                                Out of stock
                              </span>
                                        ) : Number(product.stock) <= 5 ? (
                                            <span className="stock-status stock-low">
                                Only {product.stock} left in stock!
                              </span>
                                        ) : (
                                            <span className="stock-status stock-available">
                                In stock
                              </span>
                                        )}

                                        <div className="product-bottom">
                      <span className="product-price">
                        {formatPrice(product.price)}
                      </span>
                                            <div className="product-card-actions">
                                                <button
                                                    type="button"
                                                    className={`wishlist-toggle ${
                                                        wishlistIds.includes(product.id)
                                                            ? 'wishlist-toggle-active'
                                                            : ''
                                                    }`}
                                                    aria-label={
                                                        wishlistIds.includes(product.id)
                                                            ? `Remove ${product.name} from wishlist`
                                                            : `Add ${product.name} to wishlist`
                                                    }
                                                    aria-pressed={wishlistIds.includes(product.id)}
                                                    disabled={wishlistActionId === product.id}
                                                    onClick={() => handleWishlistToggle(product)}
                                                >
                                                    {wishlistActionId === product.id ? '...' : wishlistIds.includes(product.id) ? '\u2665' : '\u2661'}
                                                </button>

                                                <button
                                                    type="button"
                                                    className="add-button"
                                                    aria-label={`Add ${product.name} to bag`}
                                                    disabled={
                                                        addingProductId !== null ||
                                                        cartLoading ||
                                                        (product.stock !== null &&
                                                            product.stock !== undefined &&
                                                            Number(product.stock) <= 0)
                                                    }
                                                    onClick={() => handleAddToCart(product)}
                                                >
                                                    {addingProductId === product.id
                                                        ? 'Adding...'
                                                        : product.stock !== null &&
                                                        product.stock !== undefined &&
                                                        Number(product.stock) <= 0
                                                            ? 'Sold out'
                                                            : '+ Add'}
                                                </button>
                                            </div>
                                        </div>
                                    </div>
                                </article>
                            ))}
                    </div>

                    {!loadingProducts && !productError && displayedTotalPages > 1 && (
                        <nav className="catalog-pagination" aria-label="Product pages">
                            <button
                                type="button"
                                className="pagination-button"
                                disabled={page === 0}
                                onClick={() => {
                                    setPage((current) => Math.max(0, current - 1));
                                    document.getElementById('products')?.scrollIntoView({ behavior: 'smooth' });
                                }}
                            >
                                Previous
                            </button>
                            <span className="pagination-status">
                                Page {page + 1} of {displayedTotalPages}
                            </span>
                            <button
                                type="button"
                                className="pagination-button"
                                disabled={page + 1 >= displayedTotalPages}
                                onClick={() => {
                                    setPage((current) => Math.min(displayedTotalPages - 1, current + 1));
                                    document.getElementById('products')?.scrollIntoView({ behavior: 'smooth' });
                                }}
                            >
                                Next
                            </button>
                        </nav>
                    )}

                    {cartError && !cartOpen && (
                        <p role="alert" className="prototype-note">
                            {cartError}{' '}
                            {(cartError.includes('log in') ||
                                cartError.includes('session')) && (
                                <Link to="/login">Log in</Link>
                            )}
                        </p>
                    )}

                    {cartMessage && !cartOpen && (
                        <p role="status" className="prototype-note">
                            {cartMessage}
                        </p>
                    )}

                    <p className="prototype-note">
                        Product information and prices come from the ShopSphere backend.
                        Cart changes are sent to the backend for the logged-in customer.
                        Checkout and order history are available from your account. Stock is rechecked by the backend when you add items and place an order.
                    </p>
                </section>

                <section className="newsletter">
                    <div>
                        <span className="eyebrow">STAY IN THE LOOP</span>
                        <h2>Your next great find starts here.</h2>
                        <p>Explore the collection and discover something new.</p>
                    </div>

                    <a className="newsletter-button" href="#products">
                        Start exploring ?
                    </a>
                </section>
            </main>

            <footer className="footer" id="account">
                <a className="brand footer-brand" href="#home">
                    <span className="brand-mark">S</span>
                    <span>
            Shop<span className="brand-accent">Sphere</span>
          </span>
                </a>

                <p>Discover more. Shop thoughtfully.</p>
                <span>Ãƒâ€šÃ‚Â© ShopSphere</span>
            </footer>

            {/* Product details dialog */}
            {selectedProduct && (
                <div
                    className="shop-overlay"
                    role="presentation"
                    onMouseDown={(event) => {
                        if (event.target === event.currentTarget) {
                            closeProductDetails();
                        }
                    }}
                >
                    <section
                        className="shop-dialog product-dialog"
                        role="dialog"
                        aria-modal="true"
                        aria-labelledby="product-dialog-title"
                    >
                        <button
                            type="button"
                            className="shop-close-button"
                            onClick={closeProductDetails}
                            aria-label="Close product details"
                        >
                            &times;
                        </button>

                        <span className="eyebrow">PRODUCT DETAILS</span>
                        {detailLoading && <p role="status">Loading latest details...</p>}
                        {detailError && <p role="status">{detailError}</p>}
                        <ProductGallery
                            product={selectedProduct}
                            className="product-detail-gallery"
                            allowRefresh
                            variantId={selectedVariantId || null}
                        />
                        <h2 id="product-dialog-title">{selectedProduct.name}</h2>
                        <p>{selectedProduct.description || 'No description available.'}</p>
                        {productVariants.length > 0 && (
                            <div className="product-variant-picker">
                                <label htmlFor="shopsphere-variant-select">Choose size / colour</label>
                                <select
                                    id="shopsphere-variant-select"
                                    value={selectedVariantId}
                                    onChange={(event) => setSelectedVariantId(event.target.value)}
                                >
                                    <option value="">Select an available option</option>
                                    {productVariants.map((variant) => (
                                        <option
                                            key={variant.id}
                                            value={String(variant.id)}
                                            disabled={Number(variant.stock ?? 0) <= 0}
                                        >
                                            {[
                                                variant.size,
                                                variant.color,
                                                variant.sku,
                                            ].filter(Boolean).join(' / ')}
                                            {' — '}
                                            {Number(variant.stock ?? 0) > 0
                                                ? `${variant.stock} in stock`
                                                : 'Out of stock'}
                                        </option>
                                    ))}
                                </select>
                            </div>
                        )}
                        {imageUploadMessage && <p role="status">{imageUploadMessage}</p>}
                        <p className="product-price">
                            {formatPrice(selectedProduct.price)}
                        </p>

                        {productVariants.length > 0 ? (
                            (() => {
                                const chosen = productVariants.find(
                                    (variant) => String(variant.id) === String(selectedVariantId)
                                );
                                if (!selectedVariantId) {
                                    return <p className="stock-status stock-unknown">Select a size / colour to see availability.</p>;
                                }
                                const stock = Number(chosen?.stock ?? 0);
                                return stock <= 0
                                    ? <p className="stock-status stock-out">Out of stock</p>
                                    : stock <= 5
                                        ? <p className="stock-status stock-low">Only {stock} left in stock!</p>
                                        : <p className="stock-status stock-available">In stock</p>;
                            })()
                        ) : selectedProduct.stock === null || selectedProduct.stock === undefined ? (
                            <p className="stock-status stock-unknown">Stock information unavailable</p>
                        ) : Number(selectedProduct.stock) <= 0 ? (
                            <p className="stock-status stock-out">Out of stock</p>
                        ) : Number(selectedProduct.stock) <= 5 ? (
                            <p className="stock-status stock-low">Only {selectedProduct.stock} left in stock!</p>
                        ) : (
                            <p className="stock-status stock-available">In stock</p>
                        )}
                        {productVariants.length > 0 && selectedVariantId && (() => {
                            const chosen = productVariants.find(
                                (variant) => String(variant.id) === String(selectedVariantId)
                            );
                            return chosen?.price != null
                                ? <p className="product-price">Selected option: {formatPrice(chosen.price)}</p>
                                : null;
                        })()}

                        <button
                            type="button"
                            className="primary-button"
                            disabled={
                                addingProductId !== null ||
                                (productVariants.length > 0 && !selectedVariantId) ||
                                (productVariants.length > 0 && Number(
                                    productVariants.find((variant) => String(variant.id) === String(selectedVariantId))?.stock ?? 0
                                ) <= 0) ||
                                (productVariants.length === 0 &&
                                    selectedProduct.stock !== null &&
                                    selectedProduct.stock !== undefined &&
                                    Number(selectedProduct.stock) <= 0)
                            }
                            onClick={async () => {
                                await handleAddToCart(selectedProduct, selectedVariantId || null);
                            }}
                        >
                            {addingProductId === selectedProduct.id
                                ? 'Adding...'
                                : productVariants.length > 0 && !selectedVariantId
                                    ? 'Choose an option'
                                    : productVariants.length > 0 && Number(
                                        productVariants.find((variant) => String(variant.id) === String(selectedVariantId))?.stock ?? 0
                                    ) <= 0
                                        ? 'Sold out'
                                        : productVariants.length === 0 &&
                                          selectedProduct.stock !== null &&
                                          selectedProduct.stock !== undefined &&
                                          Number(selectedProduct.stock) <= 0
                                            ? 'Sold out'
                                            : 'Add to bag'}
                        </button>
                        <button
                            type="button"
                            className={`wishlist-detail-button ${
                                wishlistIds.includes(Number(selectedProduct.id))
                                    ? 'wishlist-toggle-active'
                                    : ''
                            }`}
                            disabled={wishlistActionId === Number(selectedProduct.id)}
                            aria-pressed={wishlistIds.includes(Number(selectedProduct.id))}
                            onClick={() => handleWishlistToggle(selectedProduct)}
                        >
                            {wishlistIds.includes(Number(selectedProduct.id))
                                ? '\u2665 Saved to wishlist'
                                : '\u2661 Add to wishlist'}
                        </button>
                        <SimilarProducts
                            product={selectedProduct}
                            onProductSelect={(recommendedProduct) => {
                                openProductDetails(recommendedProduct);
                            }}
                        />
                    </section>
                </div>
            )}

            {/* Shopping bag drawer */}
            {cartOpen && (
                <div
                    className="shop-overlay shop-overlay-right"
                    role="presentation"
                    onMouseDown={(event) => {
                        if (event.target === event.currentTarget) {
                            setCartOpen(false);
                        }
                    }}
                >
                    <aside
                        className="shop-dialog cart-drawer"
                        role="dialog"
                        aria-modal="true"
                        aria-labelledby="cart-title"
                    >
                        <div className="cart-drawer-header">
                            <div>
                                <span className="eyebrow">YOUR SELECTION</span>
                                <h2 id="cart-title">Your bag</h2>
                            </div>
                            <button
                                type="button"
                                className="shop-close-button"
                                onClick={() => setCartOpen(false)}
                                aria-label="Close shopping bag"
                            >
                                &times;
                            </button>
                        </div>

                        {cartError && (
                            <div role="alert" className="cart-feedback">
                                <p>{cartError}</p>
                                {(cartError.includes('log in') ||
                                    cartError.includes('session')) && (
                                    <Link to="/login" onClick={() => setCartOpen(false)}>
                                        Log in to continue
                                    </Link>
                                )}
                            </div>
                        )}
                        {wishlistError && (
                            <p role="status" className="prototype-note">
                                {wishlistError}{' '}
                                {wishlistError.includes('log in') && (
                                    <Link to="/login">Log in</Link>
                                )}
                            </p>
                        )}
                        {cartMessage && (
                            <p role="status" className="cart-feedback">
                                {cartMessage}
                            </p>
                        )}

                        {cartLoading ? (
                            <p role="status">Loading your bag...</p>
                        ) : !sessionStorage.getItem('shopsphere_token') ? (
                            <p>Log in to see and manage your shopping bag.</p>
                        ) : getCartItems(cart).length === 0 ? (
                            <div className="empty-cart">
                                <span aria-hidden="true">?</span>
                                <h3>Your bag is empty</h3>
                                <p>Find something you'll love and add it to your bag.</p>
                                <button
                                    type="button"
                                    className="primary-button"
                                    onClick={() => {
                                        setCartOpen(false);
                                        document.getElementById('products')?.scrollIntoView({
                                            behavior: 'smooth',
                                        });
                                    }}
                                >
                                    Explore products
                                </button>
                            </div>
                        ) : (
                            <>
                                <div className="cart-items">
                                    {getCartItems(cart).map((item, index) => {
                                        const productId = getCartItemId(item);
                                        const busy = cartActionId === productId;

                                        return (
                                            <article
                                                className="cart-item"
                                                key={productId ?? index}
                                            >
                                                <ProductImage
    product={{
        ...(item.product ?? {}),
        id: productId,
        name: item.productName ?? item.product?.name ?? item.name ?? 'Product',
        category: item.category ?? item.product?.category,
        imageUrl: item.imageUrl ?? item.productImageUrl ?? item.product?.imageUrl,
        image_url: item.image_url ?? item.product?.image_url
    }}
    className="cart-item-image"
    alt={item.productName ?? item.product?.name ?? item.name ?? 'Product'}
/>

                                                <div className="cart-item-info">
                                                    <h3>
                                                        {item.productName ??
                                                            item.product?.name ??
                                                            item.name ??
                                                            'Product'}
                                                    </h3>
                                                    <p>{formatPrice(item.price)}</p>
                                                    {(item.variantSize || item.variantColor || item.variantSku || item.variant?.size || item.variant?.color) && (
                                                        <p className="cart-variant-label">
                                                            {[
                                                                item.variantSize ?? item.variant?.size,
                                                                item.variantColor ?? item.variant?.color,
                                                                item.variantSku ?? item.variant?.sku,
                                                            ].filter(Boolean).join(' · ')}
                                                        </p>
                                                    )}

                                                    <div className="cart-quantity-controls">
                                                        <button
                                                            type="button"
                                                            disabled={cartActionId !== null}
                                                            aria-label="Decrease quantity"
                                                            onClick={() =>
                                                                handleQuantityChange(
                                                                    item,
                                                                    Number(item.quantity) - 1
                                                                )
                                                            }
                                                        >
                                                            -
                                                        </button>
                                                        <span>{item.quantity}</span>
                                                        <button
                                                            type="button"
                                                            disabled={cartActionId !== null}
                                                            aria-label="Increase quantity"
                                                            onClick={() =>
                                                                handleQuantityChange(
                                                                    item,
                                                                    Number(item.quantity) + 1
                                                                )
                                                            }
                                                        >
                                                            +
                                                        </button>
                                                    </div>

                                                    <button
                                                        type="button"
                                                        className="remove-cart-item"
                                                        disabled={cartActionId !== null}
                                                        onClick={() => handleRemoveItem(item)}
                                                    >
                                                        {busy ? 'Updating...' : 'Remove'}
                                                    </button>
                                                </div>

                                                <strong className="cart-item-subtotal">
                                                    {formatPrice(
                                                        item.subtotal ??
                                                        Number(item.price ?? 0) *
                                                        Number(item.quantity ?? 0)
                                                    )}
                                                </strong>
                                            </article>
                                        );
                                    })}
                                </div>

                                <div className="cart-summary">
                                    <div>
                                        <span>Subtotal</span>
                                        <strong>
                                            {formatPrice(
                                                cart?.totalAmount ??
                                                getCartItems(cart).reduce(
                                                    (sum, item) =>
                                                        sum +
                                                        Number(
                                                            item.subtotal ??
                                                            Number(item.price ?? 0) *
                                                            Number(item.quantity ?? 0)
                                                        ),
                                                    0
                                                )
                                            )}
                                        </strong>
                                    </div>

                                    <div className="cart-checkout-actions">
                                        <Link
                                            to="/checkout"
                                            className="primary-button cart-checkout-button"
                                            onClick={() => setCartOpen(false)}
                                        >
                                            Continue to checkout
                                        </Link>
                                        <Link to="/wishlist">Wishlist</Link>
                                        <Link
                                            to="/orders"
                                            className="cart-orders-link"
                                            onClick={() => setCartOpen(false)}
                                        >
                                            View my orders
                                        </Link>
                                    </div>

                                    <button
                                        type="button"
                                        className="clear-cart-button"
                                        disabled={cartActionId !== null}
                                        onClick={handleClearCart}
                                    >
                                        {cartActionId === 'clear'
                                            ? 'Clearing...'
                                            : 'Clear bag'}
                                    </button>
                                </div>
                            </>
                        )}
                    </aside>
                </div>
            )}
        </div>
    );
}



function AdminRoute({ children }) {
    const token = sessionStorage.getItem('shopsphere_token');
    const role = String(sessionStorage.getItem('shopsphere_role') || '')
        .toUpperCase()
        .replace(/^ROLE_/, '');

    if (!token) {
        return <Navigate to="/login" replace />;
    }

    if (role !== 'ADMIN') {
        return <Navigate to="/" replace />;
    }

    return children;
}

function App() {
    return (
        <Routes>
            {/* Storefront */}
            <Route
                path="/"
                element={<HomePage />}
            />

            {/* Authentication */}
            <Route
                path="/register"
                element={<Register />}
            />

            <Route
                path="/login"
                element={<Login />}
            />
            <Route
                path="/forgot-password"
                element={<ForgotPassword />}
            />
            {/* Checkout */}
            <Route
                path="/checkout"
                element={<Checkout />}
            />

            {/* Customer order history */}
            <Route
                path="/orders"
                element={<Orders />}
            />

            {/* Individual order details */}
            <Route
                path="/orders/:orderId"
                element={<OrderDetails />}
            />
            <Route path="/wishlist" element={<Wishlist />} />
            {/* Seller dashboard */}
            <Route
                path="/seller/dashboard"
                element={<SellerDashboard />}
            />

            {/* Admin payment verification */}
            <Route
                path="/admin/dashboard"
                element={
                    <AdminRoute>
                        <AdminDashboard />
                    </AdminRoute>
                }
            />
            <Route
                path="/admin/payments"
                element={
                    <AdminRoute>
                        <AdminPayments />
                    </AdminRoute>
                }
            />
            {/* 404 - Page not found */}
            <Route
                path="*"
                element={
                    <main className="auth-page">
                        <div className="auth-card">
                            <h1>Page not found</h1>

                            <p className="auth-description">
                                The page you're looking for doesn't exist.
                            </p>

                            <Link to="/">
                                Return to ShopSphere
                            </Link>
                        </div>
                    </main>
                }
            />
        </Routes>
    );
}


export default App;
