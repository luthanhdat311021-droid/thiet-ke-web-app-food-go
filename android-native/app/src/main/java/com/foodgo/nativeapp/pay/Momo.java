package com.foodgo.nativeapp.pay;

import android.app.Activity;
import android.content.Intent;

import com.foodgo.nativeapp.Config;
import com.foodgo.nativeapp.core.Auth;
import com.foodgo.nativeapp.core.Cb;
import com.foodgo.nativeapp.core.Json;
import com.foodgo.nativeapp.core.Net;
import com.google.gson.JsonElement;

import okhttp3.Request;
import okhttp3.RequestBody;

/** lib/momo.ts: the payment is created by the web app's /api/payment/momo/create (it holds the MoMo secret). */
public final class Momo {
    private Momo() {}

    /** Keys MoMo appends to redirectUrl after payment (same set as the IPN body). */
    public static final String[] RETURN_KEYS = {"partnerCode", "orderId", "requestId", "amount", "orderInfo", "orderType", "transId",
            "resultCode", "message", "payType", "responseTime", "extraData", "signature"};

    /** Creates a MoMo payment for the order and opens MoMo's payment page; error only when it couldn't start. */
    public static void start(Activity a, long orderId, Cb<Void> cb) {
        Net.bg(() -> {
            String token = Auth.accessToken();
            if (token == null) { Net.main(() -> cb.done(null, "Bạn cần đăng nhập lại để thanh toán")); return; }
            Net.Res res = Net.exec(new Request.Builder().url(Config.SITE + "/api/payment/momo/create")
                    .header("Authorization", "Bearer " + token)
                    .post(RequestBody.create(Json.gson.toJson(Json.obj("orderId", orderId)), Net.JSON)).build());
            JsonElement json = Json.parse(res.body);
            String payUrl = json.isJsonObject() ? Json.str(json.getAsJsonObject(), "payUrl") : null;
            String err = res.ok() && payUrl != null ? null
                    : res.code == 0 ? Net.NETWORK_ERROR
                    : json.isJsonObject() && Json.str(json.getAsJsonObject(), "error") != null ? Json.str(json.getAsJsonObject(), "error")
                    : "Không tạo được giao dịch MoMo";
            Net.main(() -> {
                if (err != null) { cb.done(null, err); return; }
                // same as the WebView app: MoMo's page opens inside the app and sends the customer back to /orders/<id>
                a.startActivity(new Intent(a, MomoPayActivity.class).putExtra(MomoPayActivity.EXTRA_URL, payUrl));
                cb.done(null, null);
            });
        });
    }
}
