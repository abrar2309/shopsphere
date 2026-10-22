package com.abrar.shopsphere.service;

import com.abrar.shopsphere.config.RateLimitProperties;
import com.abrar.shopsphere.dto.ProductRequestDTO;
import com.abrar.shopsphere.dto.ProductResponseDTO;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import org.springframework.data.redis.core.StringRedisTemplate;

import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class ProductService {

    private static final Logger logger =
            LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;

    private final UserRepository userRepository;

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    private final RateLimitService rateLimitService;

    private final RateLimitProperties rateLimitProperties;


    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public ProductService(
            ProductRepository productRepository,
            UserRepository userRepository,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            RateLimitService rateLimitService,
            RateLimitProperties rateLimitProperties) {

        this.productRepository = productRepository;

        this.userRepository = userRepository;

        this.redisTemplate = redisTemplate;

        this.objectMapper = objectMapper;

        this.rateLimitService = rateLimitService;

        this.rateLimitProperties = rateLimitProperties;
    }


    // ============================================================
    // CREATE PRODUCT
    // ============================================================

    public ProductResponseDTO addProduct(
            ProductRequestDTO dto) {

        logger.info(
                "Creating product: {}",
                dto.getName()
        );


        // --------------------------------------------------------
        // GET CURRENT AUTHENTICATED SELLER
        // --------------------------------------------------------

        User currentUser =
                getCurrentAuthenticatedUser();


        // --------------------------------------------------------
        // RATE LIMIT PRODUCT CREATION
        // --------------------------------------------------------

        int limit =
                rateLimitProperties
                        .getProductCreate()
                        .getLimit();

        long windowSeconds =
                rateLimitProperties
                        .getProductCreate()
                        .getWindowSeconds();

        String rateLimitKey =
                "rate_limit:product-create:"
                        + currentUser.getId();

        rateLimitService.checkRateLimit(
                rateLimitKey,
                limit,
                Duration.ofSeconds(windowSeconds)
        );


        // --------------------------------------------------------
        // CHECK DUPLICATE PRODUCT
        // --------------------------------------------------------

        if (productRepository.existsByName(
                dto.getName())) {

            throw new IllegalArgumentException(
                    "Product already exists."
            );
        }


        // --------------------------------------------------------
        // CREATE PRODUCT
        // --------------------------------------------------------

        Product product =
                new Product();

        product.setName(
                dto.getName()
        );

        product.setDescription(
                dto.getDescription()
        );

        product.setPrice(
                dto.getPrice()
        );

        product.setStock(
                dto.getStock()
        );


        // --------------------------------------------------------
        // ASSIGN PRODUCT OWNER
        // --------------------------------------------------------

        product.setSeller(
                currentUser
        );


        // --------------------------------------------------------
        // SAVE
        // --------------------------------------------------------

        Product savedProduct =
                productRepository.save(product);


        logger.info(
                "Product created successfully with id {} by user {}",
                savedProduct.getId(),
                currentUser.getEmail()
        );


        return mapToResponse(
                savedProduct
        );
    }


    // ============================================================
    // GET ALL PRODUCTS
    // ============================================================

    public Page<ProductResponseDTO> getAllProducts(
            int page,
            int size,
            String sortBy) {

        Pageable pageable =
                PageRequest.of(
                        page,
                        size,
                        Sort.by(sortBy)
                );

        return productRepository
                .findAll(pageable)
                .map(this::mapToResponse);
    }


    // ============================================================
    // GET PRODUCT BY ID
    // ============================================================

    public ProductResponseDTO getProductById(
            Long id) {

        String cacheKey =
                getProductCacheKey(id);


        // --------------------------------------------------------
        // CHECK REDIS
        // --------------------------------------------------------

        String cachedProduct = null;

        try {

            cachedProduct =
                    redisTemplate
                            .opsForValue()
                            .get(cacheKey);

        } catch (Exception e) {

            logger.warn(
                    "Redis unavailable while reading product {}, falling back to database",
                    id,
                    e
            );
        }


        // --------------------------------------------------------
        // CACHE HIT
        // --------------------------------------------------------

        if (cachedProduct != null) {

            logger.info(
                    "Cache HIT for product {}",
                    id
            );

            try {

                return objectMapper.readValue(
                        cachedProduct,
                        ProductResponseDTO.class
                );

            } catch (JsonProcessingException e) {

                logger.warn(
                        "Invalid cached data for product {}, ignoring cache",
                        id,
                        e
                );
            }
        }


        // --------------------------------------------------------
        // CACHE MISS → DATABASE
        // --------------------------------------------------------

        logger.info(
                "Cache MISS for product {}",
                id
        );

        Product product =
                productRepository.findById(id)
                        .orElseThrow(() ->
                                new ProductNotFoundException(
                                        "Product not found with id: "
                                                + id
                                )
                        );


        ProductResponseDTO response =
                mapToResponse(product);


        // --------------------------------------------------------
        // CACHE PRODUCT
        // --------------------------------------------------------

        try {

            String json =
                    objectMapper.writeValueAsString(
                            response
                    );

            try {

                redisTemplate
                        .opsForValue()
                        .set(
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
    // UPDATE PRODUCT
    // ============================================================

    @PreAuthorize("@productSecurity.isOwner(#id)")
    public ProductResponseDTO updateProduct(
            Long id,
            ProductRequestDTO dto) {

        Product product =
                productRepository.findById(id)
                        .orElseThrow(() ->
                                new ProductNotFoundException(
                                        "Product not found with id: "
                                                + id
                                )
                        );


        // --------------------------------------------------------
        // UPDATE PRODUCT
        // --------------------------------------------------------

        product.setName(
                dto.getName()
        );

        product.setDescription(
                dto.getDescription()
        );

        product.setPrice(
                dto.getPrice()
        );

        product.setStock(
                dto.getStock()
        );


        // --------------------------------------------------------
        // SAVE
        // --------------------------------------------------------

        Product updatedProduct =
                productRepository.save(product);


        // --------------------------------------------------------
        // INVALIDATE CACHE
        // --------------------------------------------------------

        String cacheKey =
                getProductCacheKey(id);

        try {

            redisTemplate.delete(
                    cacheKey
            );

            logger.info(
                    "Cache invalidated for product {}",
                    id
            );

        } catch (Exception e) {

            logger.warn(
                    "Redis unavailable while invalidating cache for product {}",
                    id,
                    e
            );
        }


        logger.info(
                "Product updated successfully with id {}",
                updatedProduct.getId()
        );


        return mapToResponse(
                updatedProduct
        );
    }


    // ============================================================
    // DELETE PRODUCT
    // ============================================================

    @PreAuthorize("@productSecurity.isOwner(#id)")
    public void deleteProduct(
            Long id) {

        Product product =
                productRepository.findById(id)
                        .orElseThrow(() ->
                                new ProductNotFoundException(
                                        "Product not found with id: "
                                                + id
                                )
                        );


        // --------------------------------------------------------
        // DELETE FROM DATABASE
        // --------------------------------------------------------

        productRepository.delete(
                product
        );


        // --------------------------------------------------------
        // INVALIDATE REDIS
        // --------------------------------------------------------

        String cacheKey =
                getProductCacheKey(id);

        try {

            redisTemplate.delete(
                    cacheKey
            );

            logger.info(
                    "Cache invalidated for deleted product {}",
                    id
            );

        } catch (Exception e) {

            logger.warn(
                    "Redis unavailable while invalidating deleted product {}",
                    id,
                    e
            );
        }


        logger.info(
                "Product deleted successfully with id {}",
                id
        );
    }


    // ============================================================
    // SEARCH PRODUCTS
    // ============================================================

    public List<ProductResponseDTO> searchProducts(
            String name) {

        return productRepository
                .findByNameContainingIgnoreCase(name)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }


    // ============================================================
    // FILTER BY PRICE RANGE
    // ============================================================

    public List<ProductResponseDTO> getProductsByPriceRange(
            Double minPrice,
            Double maxPrice) {

        return productRepository
                .findByPriceBetween(
                        minPrice,
                        maxPrice
                )
                .stream()
                .map(this::mapToResponse)
                .toList();
    }


    // ============================================================
    // FILTER BY CATEGORY
    // ============================================================

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
                || !authentication.isAuthenticated()) {

            throw new IllegalStateException(
                    "No authenticated user found."
            );
        }


        String email =
                authentication.getName();


        return userRepository
                .findByEmail(email)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Authenticated user not found in database."
                        )
                );
    }


    // ============================================================
    // REDIS CACHE KEY
    // ============================================================

    private String getProductCacheKey(
            Long id) {

        return "product:" + id;
    }


    // ============================================================
    // ENTITY → DTO
    // ============================================================

    private ProductResponseDTO mapToResponse(
            Product product) {

        return new ProductResponseDTO(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice()
        );
    }
}