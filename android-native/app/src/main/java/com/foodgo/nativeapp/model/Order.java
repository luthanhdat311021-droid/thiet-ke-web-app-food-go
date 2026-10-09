package com.foodgo.nativeapp.model;

public class Order {
    public long id;
    public String code;
    public String user_id;
    public Long restaurant_id;
    public String restaurant_name;
    /** pending | confirmed | preparing | picking_up | delivering | delivered | cancelled */
    public String status;
    /** cod | qr | momo */
    public String payment_method;
    /** unpaid | paid | refunded */
    public String payment_status;
    public long subtotal;
    public long shipping_fee;
    public long discount;
    public String voucher_code;
    public long total;
    public String recipient;
    public String phone;
    public String address;
    public String note;
    public String paid_at;
    public String created_at;
    public String updated_at;
    public Double delivery_lat;
    public Double delivery_lng;
    public String momo_trans_id;
    public String delivered_at;
    public java.util.List<OrderItem> order_items;
    public OrderRestaurant restaurant;

    public java.util.List<OrderItem> items() { return order_items != null ? order_items : new java.util.ArrayList<>(); }}
