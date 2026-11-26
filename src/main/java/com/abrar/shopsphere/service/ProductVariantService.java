package com.abrar.shopsphere.service;

import com.abrar.shopsphere.dto.ProductVariantRequestDTO;
import com.abrar.shopsphere.dto.ProductVariantResponseDTO;
import com.abrar.shopsphere.entity.Product;
import com.abrar.shopsphere.entity.ProductVariant;
import com.abrar.shopsphere.repository.ProductRepository;
import com.abrar.shopsphere.repository.ProductVariantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
public class ProductVariantService {
    private final ProductRepository products;
    private final ProductVariantRepository variants;

    public ProductVariantService(ProductRepository products, ProductVariantRepository variants) {
        this.products = products;
        this.variants = variants;
    }

    @Transactional(readOnly = true)
    public List<ProductVariantResponseDTO> listPublic(Long productId) {
        requireProduct(productId);
        return variants.findByProduct_IdAndActiveTrueOrderByIdAsc(productId)
                .stream().map(ProductVariantResponseDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ProductVariantResponseDTO> listSeller(Long productId) {
        requireOwnedProduct(productId);
        return variants.findByProduct_IdOrderByIdAsc(productId)
                .stream().map(ProductVariantResponseDTO::from).toList();
    }

    @Transactional
    public ProductVariantResponseDTO create(Long productId, ProductVariantRequestDTO dto) {
        Product product = requireOwnedProduct(productId);
        validate(dto);
        if (variants.existsBySkuIgnoreCase(dto.getSku().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SKU already exists.");
        }
        ProductVariant v = new ProductVariant();
        v.setProduct(product);
        apply(v, dto, true);
        return ProductVariantResponseDTO.from(variants.save(v));
    }

    @Transactional
    public ProductVariantResponseDTO update(Long productId, Long variantId, ProductVariantRequestDTO dto) {
        requireOwnedProduct(productId);
        validate(dto);
        ProductVariant v = variants.findByIdForUpdate(variantId)
                .filter(x -> x.getProduct().getId().equals(productId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Variant not found."));
        if (!v.getSku().equalsIgnoreCase(dto.getSku().trim())
                && variants.existsBySkuIgnoreCase(dto.getSku().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SKU already exists.");
        }
        apply(v, dto, false);
        return ProductVariantResponseDTO.from(variants.save(v));
    }

    @Transactional
    public void deactivate(Long productId, Long variantId) {
        requireOwnedProduct(productId);
        ProductVariant v = variants.findByIdForUpdate(variantId)
                .filter(x -> x.getProduct().getId().equals(productId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Variant not found."));
        v.setActive(false);
        variants.save(v);
    }

    private void validate(ProductVariantRequestDTO dto) {
        if (dto == null || dto.getSku() == null || dto.getSku().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SKU is required.");
        if (dto.getStock() == null || dto.getStock() < 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stock must be zero or greater.");
        if (dto.getPrice() != null && dto.getPrice() <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Variant price must be greater than zero.");
    }

    private void apply(ProductVariant v, ProductVariantRequestDTO d, boolean creating) {
        v.setSku(d.getSku().trim());
        v.setSize(blankToNull(d.getSize()));
        v.setColor(blankToNull(d.getColor()));
        v.setPrice(d.getPrice());
        v.setStock(d.getStock());
        if (d.getActive() != null) v.setActive(d.getActive());
        else if (creating) v.setActive(true);
    }

    private String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    private Product requireProduct(Long id) {
        return products.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found."));
    }

    private Product requireOwnedProduct(Long id) {
        Product p = requireProduct(id);
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || a.getName() == null
                || p.getSeller() == null || !a.getName().equalsIgnoreCase(p.getSeller().getEmail())) {
            throw new AccessDeniedException("You do not own this product.");
        }
        return p;
    }
}
