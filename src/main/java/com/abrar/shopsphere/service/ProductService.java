
package com.abrar.shopsphere.service;

import com.abrar.shopsphere.dto.ProductResponseDTO;

import com.abrar.shopsphere.config.RateLimitProperties;

import com.abrar.shopsphere.dto.ProductCatalogResponseDTO;
import com.abrar.shopsphere.dto.ProductRequestDTO;

import com.abrar.shopsphere.entity.Product;
import com.abrar.shopsphere.entity.User;

import com.abrar.shopsphere.exception.ProductNotFoundException;

import com.abrar.shopsphere.repository.ProductRepository;
import com.abrar.shopsphere.repository.UserRepository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

import java.util.List;
import java.util.Set;

@Service
public class ProductService {

    private static final Logger logger =
            LoggerFactory.getLogger(ProductService.class);

    private static final Set<String> ALLOWED_CATALOG_SORT_FIELDS =
            Set.of("id", "name", "price");

    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RateLimitService rateLimitService;
    private final RateLimitProperties rateLimitProperties;
    private final OutboxService outboxService;
    private final JdbcTemplate jdbcTemplate;

    public ProductService(
            ProductRepository productRepository,
            UserRepository userRepository,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            RateLimitService rateLimitService,
            RateLimitProperties rateLimitProperties,
            OutboxService outboxService,
            JdbcTemplate jdbcTemplate) {

        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.rateLimitService = rateLimitService;
        this.rateLimitProperties = rateLimitProperties;
        this.outboxService = outboxService;
        this.jdbcTemplate = jdbcTemplate;
    }


    // ============================================================

    // ============================================================
    // SELLER DASHBOARD - GET LOGGED-IN SELLER'S PRODUCTS
    // ============================================================

    @PreAuthorize("hasRole('SELLER')")
    @Transactional(readOnly = true)
    public List<ProductResponseDTO> getMyProducts() {

        // Identify the currently authenticated seller.
        User currentUser = getCurrentAuthenticatedUser();

        // Fetch only products belonging to this seller.
        List<Product> products =
                productRepository.findBySeller_Id(currentUser.getId());

        // Convert entities into response DTOs.
        return products.stream()
                .map(this::mapToResponse)
                .toList();
    }
    // CREATE PRODUCT
    // ============================================================
    /**
     * Sets transaction-local metadata consumed by the PostgreSQL V8
     * inventory trigger. This must execute in the same transaction and
     * datasource connection as the subsequent Product stock update.
     */
    private void setInventoryContext(
            String reason,
            Long actorId,
            String referenceType,
            String referenceId,
            String note) {

        jdbcTemplate.query(
                """
                SELECT
                    set_config('shopsphere.inventory_reason', ?, true),
                    set_config('shopsphere.actor_user_id', ?, true),
                    set_config('shopsphere.reference_type', ?, true),
                    set_config('shopsphere.reference_id', ?, true),
                    set_config('shopsphere.inventory_note', ?, true)
                """,
                rs -> {
                    if (rs.next()) {
                        // Consume the complete result row. The settings are
                        // transaction-local and remain active for the stock update.
                    }
                    return null;
                },
                reason == null || reason.isBlank()
                        ? "SYSTEM_STOCK_UPDATE" : reason,
                actorId == null ? "" : actorId.toString(),
                referenceType == null ? "" : referenceType,
                referenceId == null ? "" : referenceId,
                note == null ? "" : note
        );
    }

    @Transactional
    @PreAuthorize("hasRole('SELLER')")
    public ProductResponseDTO addProduct(ProductRequestDTO dto) {

        if (dto == null) {
            throw new IllegalArgumentException(
                    "Product request cannot be null."
            );
        }

        if (dto.getStock() == null || dto.getStock() < 0) {
            throw new IllegalArgumentException(
                    "Stock is required and cannot be negative."
            );
        }

        logger.info("Creating product: {}", dto.getName());

        User currentUser = getCurrentAuthenticatedUser();

        int limit = rateLimitProperties
                .getProductCreate()
                .getLimit();

        long windowSeconds = rateLimitProperties
                .getProductCreate()
                .getWindowSeconds();

        String rateLimitKey =
                "rate_limit:product-create:" + currentUser.getId();

        rateLimitService.checkRateLimit(
                rateLimitKey,
                limit,
                Duration.ofSeconds(windowSeconds)
        );

        if (productRepository.existsByName(dto.getName())) {
            throw new IllegalArgumentException(
                    "Product already exists."
            );
        }

        Product product = new Product();

        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        product.setStock(dto.getStock());
        product.setColor(normalizeProductColor(dto.getColor()));
        product.setSeller(currentUser);

        Product savedProduct = productRepository.save(product);

        logger.info(
                "Product created successfully with id {} by user {}",
                savedProduct.getId(),
                currentUser.getEmail()
        );

        return mapToResponse(savedProduct);
    }


    // ============================================================
    // GET ALL PRODUCTS - DTO PROJECTION
    // ============================================================

    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> getAllProducts(
            int page,
            int size,
            String sortBy,
            String direction,
            String search,
            String category,
            Double minPrice,
            Double maxPrice) {

        if (page < 0) {
            throw new IllegalArgumentException("Page number cannot be negative.");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("Page size must be between 1 and 100.");
        }
        if (sortBy == null || !Set.of("id", "name", "price").contains(sortBy)) {
            throw new IllegalArgumentException("Invalid sort field. Allowed values: id, name, price.");
        }
        if (direction == null || !(direction.equalsIgnoreCase("asc") || direction.equalsIgnoreCase("desc"))) {
            throw new IllegalArgumentException("Sort direction must be 'asc' or 'desc'.");
        }
        if (minPrice != null && minPrice < 0) {
            throw new IllegalArgumentException("Minimum price cannot be negative.");
        }
        if (maxPrice != null && maxPrice < 0) {
            throw new IllegalArgumentException("Maximum price cannot be negative.");
        }
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw new IllegalArgumentException("Minimum price cannot exceed maximum price.");
        }

        String normalizedSearch = search == null ? null : search.trim();
        String normalizedCategory = category == null ? null : category.trim();
        if (normalizedSearch != null && normalizedSearch.isEmpty()) normalizedSearch = null;
        if (normalizedCategory != null && normalizedCategory.isEmpty()) normalizedCategory = null;

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.fromString(direction), sortBy)
        );

        logger.debug(
                "Fetching product catalog page={}, size={}, sort={} {}, search={}, category={}, minPrice={}, maxPrice={}",
                page, size, sortBy, direction, normalizedSearch, normalizedCategory, minPrice, maxPrice
        );

        return productRepository.searchCatalog(
                normalizedSearch,
                normalizedCategory,
                minPrice,
                maxPrice,
                pageable
        );
    }


    // ============================================================
    // OPTIMIZED PRODUCT CATALOG
    // ============================================================

    @Transactional(readOnly = true)
    public Page<ProductCatalogResponseDTO> getProductCatalog(
            int page,
            int size,
            String sortBy) {

        if (page < 0) {
            throw new IllegalArgumentException(
                    "Page number cannot be negative."
            );
        }

        if (size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Page size must be between 1 and 100."
            );
        }

        if (sortBy == null
                || !ALLOWED_CATALOG_SORT_FIELDS.contains(sortBy)) {

            throw new IllegalArgumentException(
                    "Invalid sort field. Allowed values: id, name, price."
            );
        }

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(sortBy).ascending()
        );

        logger.debug(
                "Fetching optimized product catalog. Page: {}, Size: {}, Sort: {}",
                page,
                size,
                sortBy
        );

        return productRepository.findCatalogPage(pageable);
    }


    // ============================================================
    // GET PRODUCT BY ID - REDIS CACHE
    // ============================================================

    @Transactional(readOnly = true)
    public ProductResponseDTO getProductById(Long id) {

        String cacheKey = getProductCacheKey(id);

        String cachedProduct = null;

        try {
            cachedProduct = redisTemplate
                    .opsForValue()
                    .get(cacheKey);

        } catch (Exception e) {
            logger.warn(
                    "Redis unavailable while reading product {}, falling back to database",
                    id,
                    e
            );
        }

        // CACHE HIT
        if (cachedProduct != null) {

            logger.info("Cache HIT for product {}", id);

            try {
                ProductResponseDTO cachedResponse =
                        objectMapper.readValue(
                                cachedProduct,
                                ProductResponseDTO.class
                        );

                // Old cached responses may not contain stock.
                // Ignore them and reload from the database.
                if (cachedResponse.getStock() != null) {
                    return cachedResponse;
                }

                logger.warn(
                        "Cached product {} has no stock value; refreshing cache",
                        id
                );

            } catch (JsonProcessingException e) {
                logger.warn(
                        "Invalid cached data for product {}, ignoring cache",
                        id,
                        e
                );
            }
        }

        // CACHE MISS
        logger.info("Cache MISS for product {}", id);

        Product product = productRepository.findById(id)
                .orElseThrow(() ->
                        new ProductNotFoundException(
                                "Product not found with id: " + id
                        )
                );

        ProductResponseDTO response = mapToResponse(product);

        // CACHE PRODUCT
        try {
            String json = objectMapper.writeValueAsString(response);

            try {
                redisTemplate.opsForValue().set(
                        cacheKey,
                        json,
                        Duration.ofMinutes(10)
                );

                logger.info(
                        "Product {} cached successfully",
                        id
                );

            } catch (Exception e) {
                logger.warn(
                        "Redis unavailable while caching product {}, returning database result",
                        id,
                        e
                );
            }

        } catch (JsonProcessingException e) {
            logger.error(
                    "Failed to serialize product {} for Redis",
                    id,
                    e
            );
        }

        return response;
    }


    // ============================================================
    // UPDATE PRODUCT - TRANSACTIONAL OUTBOX INVALIDATION
    // ============================================================

    @Transactional
    @PreAuthorize("@productSecurity.isOwner(#id)")
    public ProductResponseDTO updateProduct(
            Long id,
            ProductRequestDTO dto) {

        if (dto == null) {
            throw new IllegalArgumentException(
                    "Product request cannot be null."
            );
        }

        if (dto.getStock() == null || dto.getStock() < 0) {
            throw new IllegalArgumentException(
                    "Stock is required and cannot be negative."
            );
        }

        User currentUser = getCurrentAuthenticatedUser();

        Product product = productRepository.findByIdForUpdate(id)
                .orElseThrow(() ->
                        new ProductNotFoundException(
                                "Product not found with id: " + id
                        )
                );

        // The PostgreSQL V8 trigger writes inventory_movements whenever
        // stock changes. Set its metadata before Hibernate flushes the update.
        if (!product.getStock().equals(dto.getStock())) {
            setInventoryContext(
                    "SELLER_STOCK_UPDATE",
                    currentUser.getId(),
                    "PRODUCT_UPDATE",
                    id.toString(),
                    "Seller updated product stock"
            );
        }

        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        product.setStock(dto.getStock());
        product.setColor(normalizeProductColor(dto.getColor()));

        Product updatedProduct =
                productRepository.save(product);

        // PostgreSQL outbox event is saved in this transaction.
        outboxService.saveProductCacheInvalidationEvent(
                id,
                "UPDATED"
        );

        logger.info(
                "Product updated successfully with id {}",
                updatedProduct.getId()
        );

        return mapToResponse(updatedProduct);
    }


    // ============================================================
    // DELETE PRODUCT - TRANSACTIONAL OUTBOX INVALIDATION
    // ============================================================

    @Transactional
    @PreAuthorize("@productSecurity.isOwner(#id)")
    public void deleteProduct(Long id) {

        Product product = productRepository.findById(id)
                .orElseThrow(() ->
                        new ProductNotFoundException(
                                "Product not found with id: " + id
                        )
                );

        productRepository.delete(product);

        outboxService.saveProductCacheInvalidationEvent(
                id,
                "DELETED"
        );

        logger.info(
                "Product deleted successfully with id {}",
                id
        );
    }


    // ============================================================
    // SEARCH PRODUCTS
    // ============================================================

    @Transactional(readOnly = true)
    public List<ProductResponseDTO> searchProducts(String name) {

        return productRepository
                .findByNameContainingIgnoreCase(name)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }


    // ============================================================
    // FILTER BY PRICE RANGE
    // ============================================================

    @Transactional(readOnly = true)
    public List<ProductResponseDTO> getProductsByPriceRange(
            Double minPrice,
            Double maxPrice) {

        return productRepository
                .findByPriceBetween(minPrice, maxPrice)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }


    // ============================================================
    // FILTER BY CATEGORY
    // ============================================================

    @Transactional(readOnly = true)
    public List<ProductResponseDTO> getProductsByCategory(
            String categoryName) {

        return productRepository
                .findByCategoryName(categoryName)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }


    // ============================================================
    // GET CURRENT AUTHENTICATED USER
    // ============================================================

    private User getCurrentAuthenticatedUser() {

        var authentication =
                org.springframework.security.core.context
                        .SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {

            throw new IllegalStateException(
                    "No authenticated user found."
            );
        }

        String email = authentication.getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Authenticated user not found in database."
                        )
                );
    }


    // ============================================================
    // REDIS CACHE KEY
    // ============================================================

    private String getProductCacheKey(Long id) {
        return "product:v2:" + id;
    }


    // ============================================================
    // ENTITY TO DTO
    // ============================================================

    private ProductResponseDTO mapToResponse(Product product) {

        ProductResponseDTO response = new ProductResponseDTO(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStock(),
                product.getImageUrl()
        );
        response.setColor(product.getColor());
        return response;
    }

    private String normalizeProductColor(String color) {
        if (color == null || color.isBlank()) {
            return null;
        }
        String normalized = color.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.length() > 40) {
            throw new IllegalArgumentException("Colour cannot exceed 40 characters.");
        }
        return normalized;
    }
}
