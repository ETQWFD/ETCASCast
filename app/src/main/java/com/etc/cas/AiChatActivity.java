package com.etc.cas;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class AiChatActivity extends BaseActivity {

    private static final String ENDPOINT = "https://api.hcnsec.cn/v1/chat/completions";
    private static final String MODEL = "DeepSeek-V4-Flash";
    private static final String API_KEY = "sk-iulr7ePG32AVIKBvXFs6m5Vgik2osFzluDMShJwGubyJCxnt";

    private static final String SYSTEM_PROMPT =
            "你是 ETCAS 投屏官方 AI 客服助手，产品背景如下，请基于这些信息认真思考后回答用户：\n"
            + "软件：ETCAS 投屏，手机端包名 com.etc.cas，电视端包名 com.etc.cas.tv；官网 https://etc.os.kg\n"
            + "开源仓库：手机 https://github.com/ETQWFD/ETCASCast ，电视 https://github.com/ETQWFD/ETCASCastTV\n"
            + "开发者：ETC 协会；翻译者：POAI；基于 MIT License 开源\n"
            + "功能：本地文件/图片投屏（支持 m4s 等全格式）、网站链接投屏（哔哩哔哩等解析直链）、屏幕同步镜像、扫码投屏、手动添加设备、DLNA 设备搜索（云视听小电视/酷喵/芒果等）、画质与倍速切换、音量调节、检查更新\n"
            + "最低支持 Android 7，兼容 64/32/x86；电视端支持 U 盘安装（v1 签名）\n"
            + "使用前提：手机与电视同一 Wi-Fi 网络；投屏走局域网直连，不经过公网服务器\n"
            + "常见问题：搜不到设备→确认同一网络且电视端投屏应用已开启，可扫码或手动添加电视 IP；扫码连接自家电视端→扫电视二维码自动配对免输码，手动搜索自家设备需输入电视显示的 6 位配对码；投屏黑屏→确认格式支持、网页无直链时先在手机端观看；检查更新→设置内自动匹配对应版本\n"
            + "请用简体中文简洁友好地回答；不要编造不存在的功能或版本；涉及官网与仓库时给出准确链接。";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private LinearLayout chatBox;
    private ScrollView scroll;
    private EditText input;
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_chat);

        chatBox = findViewById(R.id.chat_box);
        scroll = findViewById(R.id.chat_scroll);
        input = findViewById(R.id.et_ai_input);
        findViewById(R.id.btn_ai_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_ai_send).setOnClickListener(v -> send());
        appendMsg(getString(R.string.ai_welcome), false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ThemeManager.apply(this);
        FontManager.apply(findViewById(android.R.id.content), this);
    }

    private void send() {
        if (busy) return;
        String text = input.getText().toString().trim();
        if (text.isEmpty()) return;
        input.setText("");
        appendMsg(text, true);
        busy = true;
        setBusy(true);
        new Thread(() -> {
            final String reply = callApi(text);
            ui.post(() -> {
                busy = false;
                setBusy(false);
                if (reply == null) {
                    appendMsg(getString(R.string.ai_error), false);
                } else {
                    appendMsg(reply, false);
                }
            });
        }, "etcas-ai").start();
    }

    private void setBusy(boolean b) {
        findViewById(R.id.btn_ai_send).setEnabled(!b);
        TextView tv = findViewById(R.id.tv_ai_status);
        if (b) {
            tv.setVisibility(View.VISIBLE);
            tv.setText(R.string.ai_thinking);
        } else {
            tv.setVisibility(View.GONE);
        }
    }

    private void appendMsg(String text, boolean fromMe) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(14f);
        int pad = dp(10);
        tv.setPadding(pad, pad, pad, pad);
        tv.setTextColor(0xFF222222);
        if (fromMe) {
            tv.setBackgroundColor(0xFFD9E8FF);
        } else {
            tv.setBackgroundColor(0xFFF0F0F0);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(6), 0, 0);
        tv.setLayoutParams(lp);
        chatBox.addView(tv);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private String callApi(String userText) {
        try {
            JSONObject body = new JSONObject();
            body.put("model", MODEL);
            JSONArray msgs = new JSONArray();
            JSONObject sys = new JSONObject();
            sys.put("role", "system");
            sys.put("content", SYSTEM_PROMPT);
            msgs.put(sys);
            JSONObject user = new JSONObject();
            user.put("role", "user");
            user.put("content", userText);
            msgs.put(user);
            body.put("messages", msgs);
            body.put("temperature", 0.7);
            body.put("max_tokens", 800);

            HttpURLConnection conn = (HttpURLConnection) new URL(ENDPOINT).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + API_KEY);
            conn.setDoOutput(true);
            byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
            OutputStream os = conn.getOutputStream();
            os.write(data);
            os.flush();
            os.close();
            int code = conn.getResponseCode();
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (in == null) return null;
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            conn.disconnect();
            if (code != 200) return null;
            JSONObject resp = new JSONObject(bos.toString("UTF-8"));
            JSONArray choices = resp.optJSONArray("choices");
            if (choices == null || choices.length() == 0) return null;
            String content = choices.getJSONObject(0).optJSONObject("message").optString("content");
            return content == null || content.trim().isEmpty() ? null : content.trim();
        } catch (Exception e) {
            return null;
        }
    }
}
