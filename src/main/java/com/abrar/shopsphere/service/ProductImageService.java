package com.abrar.shopsphere.service;

import com.abrar.shopsphere.dto.ProductResponseDTO;

import com.abrar.shopsphere.entity.Product;
import com.abrar.shopsphere.entity.User;
import com.abrar.shopsphere.repository.ProductRepository;
import com.abrar.shopsphere.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProductImageService {
    private final ProductRepository products; private final UserRepository users; private final ProductImageStorageService storage;
    public ProductImageService(ProductRepository products, UserRepository users, ProductImageStorageService storage) { this.products=products; this.users=users; this.storage=storage; }
    @Transactional
    public ProductResponseDTO upload(Long id, MultipartFile file) {
        Product product = products.findById(id).orElseThrow(() -> new IllegalArgumentException("Product not found."));
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) throw new org.springframework.security.access.AccessDeniedException("Authentication required.");
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        User current = users.findByEmail(auth.getName()).orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Authenticated user not found."));
        if (!admin && (product.getSeller() == null || !product.getSeller().getId().equals(current.getId()))) throw new org.springframework.security.access.AccessDeniedException("You can only upload images for your own products.");
        String newUrl = storage.store(file); String oldUrl = product.getImageUrl(); product.setImageUrl(newUrl);
        Product saved = products.save(product); if (oldUrl != null && !oldUrl.equals(newUrl)) storage.deleteByPublicUrl(oldUrl);
        return new ProductResponseDTO(saved.getId(), saved.getName(), saved.getDescription(), saved.getPrice(), saved.getStock(), saved.getImageUrl());
    }
}
