package com.foodgo.nativeapp.model;

public class Restaurant {
    public long id;
    public String name;
    public String cuisine;
    public String address;
    public String image;
    public String logo;
    public double rating;
    public int review_count;
    public double distance_km;
    public String delivery_time;
    public String tag;
    public boolean is_active;
    public Double lat;
    public Double lng;
    /** "HH:MM:SS", Vietnam time; null = no fixed hours */
    public String open_time;
    public String close_time;
    /** manual "tạm đóng cửa" switch */
    public boolean is_open;
    /** null = run by the admin (always live); otherwise visible only while paid_until is in the future */
    public String owner_id;
    public String paid_until;
    public String phone;}
