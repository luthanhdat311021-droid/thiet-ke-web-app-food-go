package com.foodgo.nativeapp;

/** Cùng giá trị công khai (NEXT_PUBLIC_*) mà bản web đang dùng. */
public final class Config {
    private Config() {}

    public static final String SUPABASE_URL = "https://xfvpbunbdvjkugrmjfqy.supabase.co";
    public static final String SUPABASE_KEY = "sb_publishable_TI4ZUHNeuqymO2nm8gNqgA_MthX3su4";

    /** Bản web trên Vercel: các API /api/geo/* và /api/payment/momo/* vẫn chạy ở đây. */
    public static final String SITE = "https://thiet-ke-web-app-food-go.vercel.app";

    public static final String VIETQR_BANK_ID = "970422";
    public static final String VIETQR_ACCOUNT_NO = "0819883208";
    public static final String VIETQR_ACCOUNT_NAME = "LU THANH DAT";

    /** Cloudflare Turnstile: Supabase Auth đang bật captcha nên đăng nhập/đăng ký cần token này. */
    public static final String TURNSTILE_SITE_KEY = "0x4AAAAAAFQaliWpJBYYKKhC";

    /** Đăng nhập Google quay về app qua deep link này (khớp com.foodgo.app://** trong Supabase). */
    public static final String AUTH_CALLBACK = "com.foodgo.app://native-auth-callback";
}
