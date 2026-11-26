package com.abrar.shopsphere.controller;

import com.abrar.shopsphere.service.ProductVariantImageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/products/{productId}/variants/{variantId}/images")
public class ProductVariantImageController {
    private final ProductVariantImageService service;
    public ProductVariantImageController(ProductVariantImageService service) { this.service = service; }

    @GetMapping
    public List<Map<String,Object>> list(@PathVariable Long productId, @PathVariable Long variantId) {
        return service.list(productId, variantId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
    public List<Map<String,Object>> upload(@PathVariable Long productId, @PathVariable Long variantId,
            @RequestPart("files") List<MultipartFile> files) {
        return service.upload(productId, variantId, files);
    }

    @DeleteMapping("/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
    public void delete(@PathVariable Long productId, @PathVariable Long variantId, @PathVariable Long imageId) {
        service.delete(productId, variantId, imageId);
    }
}