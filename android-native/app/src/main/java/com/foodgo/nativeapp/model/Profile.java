package com.foodgo.nativeapp.model;

public class Profile {
    public String id;
    public String full_name;
    public String phone;
    public String birthday;
    public String avatar_url;
    /** 'customer' | 'admin' */
    public String role;
    public String created_at;

    public boolean isAdmin() { return "admin".equals(role); }}
