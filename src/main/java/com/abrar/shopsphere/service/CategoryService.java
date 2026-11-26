
package com.abrar.shopsphere.service;

import com.abrar.shopsphere.dto.CategoryRequestDTO;
import com.abrar.shopsphere.dto.CategoryResponseDTO;
import com.abrar.shopsphere.dto.CategoryProductResponseDTO;
import com.abrar.shopsphere.dto.CategoryWithProductsResponseDTO;
import com.abrar.shopsphere.entity.Category;
import com.abrar.shopsphere.entity.Product;
import com.abrar.shopsphere.event.OrderCreatedEvent;
import com.abrar.shopsphere.exception.CategoryNotFoundException;
import com.abrar.shopsphere.repository.CategoryRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    // Create Category
    public CategoryResponseDTO create(CategoryRequestDTO dto) {

        if (categoryRepository.existsByName(dto.getName())) {
            throw new IllegalArgumentException(
                    "Category already exists."
            );
        }

        Category category = new Category();
        category.setName(dto.getName());

        Category savedCategory = categoryRepository.save(category);

        return mapToResponse(savedCategory);
    }

    // Get All Categories
    public List<CategoryResponseDTO> getAll() {

        return categoryRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // Get Category By Id
    public CategoryResponseDTO getById(Long id) {

        Category category = categoryRepository.findById(id)
                .orElseThrow(() ->
                        new CategoryNotFoundException(id));

        return mapToResponse(category);
    }

    // Update Category
    public CategoryResponseDTO update(
            Long id,
            CategoryRequestDTO dto) {

        Category category = categoryRepository.findById(id)
                .orElseThrow(() ->
                        new CategoryNotFoundException(id));

        category.setName(dto.getName());

        Category updatedCategory = categoryRepository.save(category);

        return mapToResponse(updatedCategory);
    }

    // Delete Category
    public void delete(Long id) {

        Category category = categoryRepository.findById(id)
                .orElseThrow(() ->
                        new CategoryNotFoundException(id));

        categoryRepository.delete(category);
    }

    // Paginated category fetching with products
    @Transactional(readOnly = true)
    public Page<CategoryWithProductsResponseDTO>
    getCategoriesWithProducts(int page, int size) {

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

        Pageable pageable = PageRequest.of(page, size);

        // QUERY 1: Fetch paginated category IDs
        Page<Long> categoryIdPage =
                categoryRepository.findCategoryIds(pageable);

        List<Long> categoryIds = categoryIdPage.getContent();

        // Handle empty pages without executing an IN query
        if (categoryIds.isEmpty()) {
            return new PageImpl<>(
                    List.of(),
                    pageable,
                    categoryIdPage.getTotalElements()
            );
        }

        // QUERY 2: Fetch categories with their products
        List<Category> categories =
                categoryRepository.findAllWithProductsByIdIn(
                        categoryIds
                );

        // Create lookup map for preserving pagination order
        Map<Long, Category> categoryMap =
                categories.stream()
                        .collect(Collectors.toMap(
                                Category::getId,
                                Function.identity()
                        ));

        // Restore the original order from the ID page
        List<CategoryWithProductsResponseDTO> content =
                categoryIds.stream()
                        .map(categoryMap::get)
                        .filter(category -> category != null)
                        .map(this::mapToCategoryWithProductsResponse)
                        .toList();

        return new PageImpl<>(
                content,
                pageable,
                categoryIdPage.getTotalElements()
        );
    }

    // Convert Category Entity to existing DTO
    private CategoryResponseDTO mapToResponse(Category category) {

        return new CategoryResponseDTO(
                category.getId(),
                category.getName()
        );
    }

    // Convert Category Entity and its Products to DTO
    private CategoryWithProductsResponseDTO
    mapToCategoryWithProductsResponse(Category category) {

        List<CategoryProductResponseDTO> products =
                category.getProducts() == null
                        ? List.of()
                        : category.getProducts()
                        .stream()
                        .map(this::mapProductToResponse)
                        .toList();

        return new CategoryWithProductsResponseDTO(
                category.getId(),
                category.getName(),
                products
        );
    }

    // Convert Product Entity to DTO
    private CategoryProductResponseDTO
    mapProductToResponse(Product product) {

        return new CategoryProductResponseDTO(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice()
        );
    }

    @Service
    public static class OrderEventPublisher {

        private static final Logger log =
                LoggerFactory.getLogger(
                        OrderEventPublisher.class
                );

        private final KafkaTemplate<String, OrderCreatedEvent>
                kafkaTemplate;

        public OrderEventPublisher(
                KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate) {

            this.kafkaTemplate = kafkaTemplate;
        }

        public void publishOrderCreated(
                OrderCreatedEvent event) {

            kafkaTemplate.send(
                    "order-created",
                    event.getOrderId().toString(),
                    event
            );

            log.info(
                    "OrderCreated event published. eventId={}, orderId={}",
                    event.getEventId(),
                    event.getOrderId()
            );
        }
    }
}
