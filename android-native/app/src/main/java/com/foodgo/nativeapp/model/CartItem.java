package com.foodgo.nativeapp.model;

public class CartItem {
    public long food_id;
    public String name;
    public long price;
    public String image;
    public long restaurant_id;
    public String restaurant_name;
    public int qty;

    public CartItem copy() {
        CartItem c = new CartItem();
        c.food_id = food_id; c.name = name; c.price = price; c.image = image;
        c.restaurant_id = restaurant_id; c.restaurant_name = restaurant_name; c.qty = qty;
        return c;
    }}
