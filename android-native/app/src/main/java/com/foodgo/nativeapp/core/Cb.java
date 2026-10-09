package com.foodgo.nativeapp.core;

/** Kết quả bất đồng bộ kiểu supabase-js: { data, error }. Luôn được gọi trên main thread. */
public interface Cb<T> {
    void done(T data, String error);
}
