
package com.abrar.shopsphere.entity;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private Double totalAmount;

    private String status;

    // Immutable snapshot of the delivery address at order creation.
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "label",
                    column = @Column(name = "delivery_address_label")),
            @AttributeOverride(name = "recipientName",
                    column = @Column(name = "delivery_recipient_name")),
            @AttributeOverride(name = "phoneNumber",
                    column = @Column(name = "delivery_phone_number")),
            @AttributeOverride(name = "addressLine1",
                    column = @Column(name = "delivery_address_line1")),
            @AttributeOverride(name = "addressLine2",
                    column = @Column(name = "delivery_address_line2")),
            @AttributeOverride(name = "city",
                    column = @Column(name = "delivery_city")),
            @AttributeOverride(name = "state",
                    column = @Column(name = "delivery_state")),
            @AttributeOverride(name = "postalCode",
                    column = @Column(name = "delivery_postal_code")),
            @AttributeOverride(name = "country",
                    column = @Column(name = "delivery_country"))
    })
    private OrderAddress deliveryAddress;

    @OneToMany(
            mappedBy = "order",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<OrderItem> items = new ArrayList<>();

    public Order() {
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(Double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OrderAddress getDeliveryAddress() {
        return deliveryAddress;
    }

    public void setDeliveryAddress(OrderAddress deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }
}
