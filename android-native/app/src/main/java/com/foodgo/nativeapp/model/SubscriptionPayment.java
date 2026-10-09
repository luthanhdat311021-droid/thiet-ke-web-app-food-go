package com.foodgo.nativeapp.model;

public class SubscriptionPayment {
    public long id;
    public String code;
    public long restaurant_id;
    public String owner_id;
    public int months;
    public long amount;
    /** pending | paid | cancelled */
    public String status;
    /** qr | admin */
    public String method;
    public String paid_at;
    public String created_at;
    /** joined as restaurant:fg_restaurants(name) in the admin list */
    public Named restaurant;

    public static class Named { public String name; }}
