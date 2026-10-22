package com.abrar.shopsphere.controller;

import com.abrar.shopsphere.dto.CategoryRequestDTO;
import com.abrar.shopsphere.dto.CategoryResponseDTO;
import com.abrar.shopsphere.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    // Create Category
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponseDTO createCategory(
            @Valid
            @RequestBody CategoryRequestDTO requestDTO) {

        return categoryService.create(requestDTO);
    }

    // Get All Categories
    @GetMapping
    public List<CategoryResponseDTO> getAllCategories() {

        return categoryService.getAll();
    }

    // Get Category By Id
    @GetMapping("/{id}")
    public CategoryResponseDTO getCategoryById(
            @PathVariable Long id) {

        return categoryService.getById(id);
    }

    // Update Category
    @PutMapping("/{id}")
    public CategoryResponseDTO updateCategory(
            @PathVariable Long id,

            @Valid
            @RequestBody CategoryRequestDTO requestDTO) {

        return categoryService.update(id, requestDTO);
    }

    // Delete Category
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(
            @PathVariable Long id) {

        categoryService.delete(id);
    }
}