
package com.abrar.shopsphere.controller;

import com.abrar.shopsphere.dto.ProductImageDTO;
import com.abrar.shopsphere.service.ProductGalleryService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/products/{productId}/images")
@SecurityRequirement(name = "bearerAuth")
public class ProductGalleryController {

    private final ProductGalleryService gallery;

    public ProductGalleryController(ProductGalleryService gallery) {
        this.gallery = gallery;
    }

    @GetMapping
    public List<ProductImageDTO> getImages(
            @PathVariable Long productId) {
        return gallery.getImages(productId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public List<ProductImageDTO> upload(
            @PathVariable Long productId,
            @RequestPart("files") List<MultipartFile> files) {
        return gallery.upload(productId, files);
    }

    @PutMapping("/{imageId}/primary")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setPrimary(
            @PathVariable Long productId,
            @PathVariable Long imageId) {
        gallery.setPrimary(productId, imageId);
    }

    @DeleteMapping("/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Long productId,
            @PathVariable Long imageId) {
        gallery.delete(productId, imageId);
    }
}
