package com.abrar.shopsphere.service;

import com.abrar.shopsphere.dto.CategoryRequestDTO;
import com.abrar.shopsphere.dto.CategoryResponseDTO;
import com.abrar.shopsphere.entity.Category;
import com.abrar.shopsphere.event.OrderCreatedEvent;
import com.abrar.shopsphere.exception.CategoryNotFoundException;
import com.abrar.shopsphere.repository.CategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    // Create Category
    public CategoryResponseDTO create(CategoryRequestDTO dto) {

        if (categoryRepository.existsByName(dto.getName())) {
            throw new IllegalArgumentException("Category already exists.");
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
    public CategoryResponseDTO update(Long id,
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

    // Convert Entity to DTO
    private CategoryResponseDTO mapToResponse(Category category) {

        return new CategoryResponseDTO(
                category.getId(),
                category.getName()
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
                    "OrderCreated event published. " +
                            "eventId={}, orderId={}",
                    event.getEventId(),
                    event.getOrderId()
            );
        }
    }
}