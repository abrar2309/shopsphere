# ShopSphere — Product Walkthrough and Screenshot Gallery

This page records the customer and seller flows shown in the supplied browser captures. The screenshots were captured from local browser sessions on 22–26 November 2026. They are static product evidence, not an online deployment or a screen-recorded demo.

## Customer experience

### 1. Home page

![ShopSphere home page](demo-screenshots/01-homepage.jpg)

The landing page presents the ShopSphere brand, navigation, bag entry point and a discovery-focused hero section.

### 2. Discover catalogue

![Discover catalogue](demo-screenshots/02-discover-catalogue.jpg)

The Discover page shows the product catalogue with a natural-language prompt, minimum/maximum price filters, sorting and the captured total of 12 products.

### 3. Product cards and availability

![Product listing cards](demo-screenshots/03-product-cards.jpg)

The captured cards include Casual Sneakers and Trail Running Shoes with category labels, descriptions, stock state and prices. The captured UI still uses an Image coming soon placeholder for these product cards; this is a known presentation item to improve.

### 4. Product details

![Product detail modal](demo-screenshots/07-product-detail.jpg)

The product detail overlay shows the Classic Leather Loafers title, description, price, stock state, image-refresh control, seller/admin image upload entry point, add-to-bag and wishlist actions. The capture also shows the image placeholder rather than a loaded product image.

## Cart and checkout

### 5. Checkout with cash on delivery

![Checkout COD](demo-screenshots/04-checkout-cod.jpg)

The checkout panel shows the Cotton T Shirt order summary, subtotal and COD selection before placing the order.

### 6. Checkout with UPI

![Checkout UPI with sensitive values redacted](demo-screenshots/05-checkout-upi-redacted.jpg)

The manual UPI checkout panel displays the ShopSphere payee, amount and transaction-reference input. The supplied capture has been sanitized for repository publication: the UPI identifier and entered transaction reference are redacted. Manual UPI is not a verified payment-gateway transaction; the backend records manual payments as pending until the appropriate verification flow.

## AI-assisted discovery

### 7. BGE semantic search assistant

![BGE semantic search assistant](demo-screenshots/06-bge-semantic-search.jpg)

The screenshot shows the ShopSphere AI assistant with the query “Running shoes under 2000”. The displayed result count is zero in this capture. This image documents the UI path only; it does not claim that the query returned relevant products. BGE retrieval requires the embedding service to be healthy and product embeddings to have been indexed.

### 8. Home search entry point

![Home search and recommendations](demo-screenshots/08-homepage-search.jpg)

The home page also exposes a dedicated search input and AI recommendation area. The screenshot shows a currency-character rendering issue in the search placeholder; this is a UI polish item to check alongside the product-image placeholders.

## Seller experience

### 9. Seller product variants

![Seller variant management](demo-screenshots/09-seller-variants.jpg)

The seller dashboard screenshot shows product cards and variant management controls, including size, colour, variant price, stock and add/edit actions. This demonstrates the seller-side variant-management UI.

## Suggested live walkthrough order

When local services or a hosted deployment are available, use this order for a live demo recording:

1. Home page → Discover → filter/sort catalogue.
2. Open a product → inspect detail → add to bag.
3. Review bag → checkout → show COD and manual UPI options without entering real payment information.
4. Sign in as seller → open seller dashboard → create or edit a product variant.
5. Open the AI assistant and run a query only after confirming BGE health and indexed product embeddings.
6. End with the architecture diagram and deployment caveat.

Do not present these screenshots as a live URL or as a recorded video. The AWS EC2 instance required by the previous SSM-to-RDS tunnel is stopped, so the remote private database tunnel is unavailable until that infrastructure is deliberately restored.
