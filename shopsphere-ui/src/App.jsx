
import './App.css';

import { useEffect, useState } from 'react';
import { Routes, Route, Link } from 'react-router-dom';

import Register from './pages/Register';
import Login from './pages/Login';
import Checkout from './pages/Checkout';
import Orders from './pages/Orders';

import {
  getProducts,
  getProductById,
  getProductsByCategory,
  getCategories,
} from './api/productApi';

import {
  getCart,
  addCartItem,
  updateCartItem,
  removeCartItem,
  clearCart,
} from './api/cartApi';

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

function HomePage() {
  const [products, setProducts] = useState([]);
  const [categories, setCategories] = useState([]);

  const [loadingProducts, setLoadingProducts] = useState(true);
  const [loadingCategories, setLoadingCategories] = useState(true);
  const [productError, setProductError] = useState('');
  const [categoryError, setCategoryError] = useState('');

  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('All');

  const [cart, setCart] = useState(null);
  const [cartLoading, setCartLoading] = useState(false);
  const [cartError, setCartError] = useState('');
  const [cartMessage, setCartMessage] = useState('');
  const [cartOpen, setCartOpen] = useState(false);
  const [cartActionId, setCartActionId] = useState(null);

  const [addingProductId, setAddingProductId] = useState(null);

  const [selectedProduct, setSelectedProduct] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState('');

  // Load products whenever the selected category changes.
  useEffect(() => {
    let cancelled = false;

    async function loadProducts() {
      setLoadingProducts(true);
      setProductError('');

      try {
        const data =
            selectedCategory === 'All'
                ? await getProducts()
                : await getProductsByCategory(selectedCategory);

        if (cancelled) return;

        const productList = Array.isArray(data)
            ? data
            : Array.isArray(data?.content)
                ? data.content
                : [];

        setProducts(productList);
      } catch (error) {
        console.error('Failed to load products:', error);

        if (!cancelled) {
          setProducts([]);
          setProductError(
              error.response?.status === 401 ||
              error.response?.status === 403
                  ? 'Please log in to view products.'
                  : 'Unable to load products. Check that the backend is running and try again.'
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
  }, [selectedCategory]);

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

  // Search works on the currently loaded category.
  const filteredProducts = products.filter((product) => {
    const query = searchQuery.trim().toLowerCase();

    return (
        product.name?.toLowerCase().includes(query) ||
        product.description?.toLowerCase().includes(query)
    );
  });

  async function refreshCart() {
    const updatedCart = await getCart();
    setCart(updatedCart);
    return updatedCart;
  }

  async function handleAddToCart(product) {
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

      await addCartItem(product.id, 1);
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
              <span aria-hidden="true">⌕</span>
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
                Explore the collection <span aria-hidden="true">→</span>
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
              <div className="art-card art-card-back">✦</div>

              <div className="art-card art-card-front">
                <span className="art-sparkle">✳</span>
                <span className="art-label">GOOD FINDS.</span>
                <span className="art-label art-label-bottom">
                GREAT FEELING.
              </span>
              </div>

              <div className="floating-tag tag-one">Made for you</div>
              <div className="floating-tag tag-two">Discover more ↗</div>
            </div>
          </section>

          <section className="benefits" aria-label="Shopping highlights">
            <div>
              <span>✧</span>
              <p>Curated discoveries</p>
            </div>
            <div>
              <span>◇</span>
              <p>Easy browsing</p>
            </div>
            <div>
              <span>↗</span>
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
                    document.getElementById('products')?.scrollIntoView({
                      behavior: 'smooth',
                    });
                  }}
              >
                Explore all <span>→</span>
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
                      onClick={() => setSelectedCategory('All')}
                  >
                    <span className="category-icon">✦</span>
                    <span className="category-name">All products</span>
                    <span className="category-arrow">↗</span>
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
                          onClick={() => setSelectedCategory(category.name)}
                      >
                        <span className="category-icon">✧</span>
                        <span className="category-name">{category.name}</span>
                        <span className="category-arrow">↗</span>
                      </button>
                  ))}
                </div>
            )}

            {categoryError && <p role="alert">{categoryError}</p>}
          </section>

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
                  Explore products from the ShopSphere catalogue.
                </p>
              </div>

              <span className="product-count">
              {loadingProducts
                  ? 'Loading products...'
                  : `${filteredProducts.length} products`}
            </span>
            </div>

            <label className="search-box" htmlFor="search">
              <span aria-hidden="true">⌕</span>
              <input
                  id="search"
                  type="search"
                  placeholder="Search the collection..."
                  value={searchQuery}
                  onChange={(event) => setSearchQuery(event.target.value)}
              />
            </label>

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
                      onClick={() => setSelectedCategory((current) => current)}
                  >
                    Retry
                  </button>
                </div>
            )}

            {!loadingProducts &&
                !productError &&
                products.length === 0 && (
                    <p>
                      {selectedCategory === 'All'
                          ? 'No products are available yet.'
                          : `No products were found in ${selectedCategory}.`}
                    </p>
                )}

            {!loadingProducts &&
                !productError &&
                products.length > 0 &&
                filteredProducts.length === 0 && (
                    <p>No products match your search.</p>
                )}

            <div className="product-grid">
              {!loadingProducts &&
                  !productError &&
                  filteredProducts.map((product, index) => (
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
                          <span className="product-badge">Discover</span>
                          <span className="product-emoji" aria-hidden="true">
                      🛍️
                    </span>
                          <span className="product-view-hint">View details ↗</span>
                        </button>

                        <div className="product-info">
                    <span className="product-category">
                      {selectedCategory === 'All'
                          ? 'ShopSphere'
                          : selectedCategory}
                    </span>

                          <button
                              type="button"
                              className="product-title-button"
                              onClick={() => openProductDetails(product)}
                          >
                            <h3>{product.name}</h3>
                          </button>

                          <p>{product.description}</p>

                          <div className="product-bottom">
                      <span className="product-price">
                        {formatPrice(product.price)}
                      </span>

                            <button
                                type="button"
                                className="add-button"
                                aria-label={`Add ${product.name} to bag`}
                                disabled={
                                    addingProductId !== null || cartLoading
                                }
                                onClick={() => handleAddToCart(product)}
                            >
                              {addingProductId === product.id
                                  ? 'Adding...'
                                  : '+ Add'}
                            </button>
                          </div>
                        </div>
                      </article>
                  ))}
            </div>

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
              Wishlist and checkout are not implemented yet.
            </p>
          </section>

          <section className="newsletter">
            <div>
              <span className="eyebrow">STAY IN THE LOOP</span>
              <h2>Your next great find starts here.</h2>
              <p>Explore the collection and discover something new.</p>
            </div>

            <a className="newsletter-button" href="#products">
              Start exploring →
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
          <span>© ShopSphere</span>
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
                  ×
                </button>

                <span className="eyebrow">PRODUCT DETAILS</span>
                {detailLoading && <p role="status">Loading latest details...</p>}
                {detailError && <p role="status">{detailError}</p>}

                <h2 id="product-dialog-title">{selectedProduct.name}</h2>
                <p>{selectedProduct.description || 'No description available.'}</p>
                <p className="product-price">
                  {formatPrice(selectedProduct.price)}
                </p>

                <button
                    type="button"
                    className="primary-button"
                    disabled={addingProductId !== null}
                    onClick={async () => {
                      await handleAddToCart(selectedProduct);
                    }}
                >
                  {addingProductId === selectedProduct.id
                      ? 'Adding...'
                      : 'Add to bag'}
                </button>
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
                    ×
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
                      <span aria-hidden="true">♡</span>
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
                                <div className="cart-item-art" aria-hidden="true">
                                  🛍️
                                </div>

                                <div className="cart-item-info">
                                  <h3>
                                    {item.productName ??
                                        item.product?.name ??
                                        item.name ??
                                        'Product'}
                                  </h3>
                                  <p>{formatPrice(item.price)}</p>

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
                                      −
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

function App() {
  return (
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/register" element={<Register />} />
        <Route path="/login" element={<Login />} />
        <Route path="/checkout" element={<Checkout />} />
        <Route path="/orders" element={<Orders />} />

        <Route
            path="*"
            element={
              <main className="auth-page">
                <div className="auth-card">
                  <h1>Page not found</h1>
                  <p className="auth-description">
                    The page you're looking for doesn't exist.
                  </p>
                  <Link to="/">Return to ShopSphere</Link>
                </div>
              </main>
            }
        />
      </Routes>
  );
}

export default App;
