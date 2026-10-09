package com.foodgo.nativeapp.model;

public class Food {
    public long id;
    public long restaurant_id;
    public Long category_id;
    public String name;
    public String description;
    public long price;
    public Long old_price;
    public String image;
    public double rating;
    public int review_count;
    public int sold_count;
    public boolean is_available;
    public boolean is_popular;
    /** joined via FOOD_SELECT */
    public FoodRestaurant restaurants;}
