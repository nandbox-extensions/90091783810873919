package com.nandbox.extension;

import com.nandbox.bots.api.Nandbox;
import com.nandbox.bots.api.NandboxClient;
import com.nandbox.bots.api.data.*;
import com.nandbox.bots.api.inmessages.*;
import com.nandbox.bots.api.outmessages.*;
import com.nandbox.bots.api.util.*;
import com.nandbox.bots.api.test.*;
import com.nandbox.extension.ExtensionAdapter;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;

public class ExtensionCustomLogic extends ExtensionAdapter {

    private Nandbox.Api api;

    private static final String TABLE_NAME = "google_form_responses";
    private static final String ADMIN_ID = "90092883306759582";

    private String adminChatId;
    private String adminAppId;
    private Integer adminChatSettings;

    public static void main(String[] args) throws Exception {
        String TOKEN = "";
        Properties properties = new Properties();
        FileInputStream input = null;

        try {
            input = new FileInputStream("config.properties");
            properties.load(input);
            TOKEN = properties.getProperty("Token");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                }
            }
        }

        NandboxClient client = NandboxClient.get();
        client.connect(TOKEN, new ExtensionCustomLogic());
    }

    @Override
    public void onConnect(Nandbox.Api api) {
        this.api = api;
    }

    @Override
    public void onWebhookEvent(WebhookBody webhookBody) {
        if (webhookBody == null) {
            return;
        }

        String appId = null;
        try {
            appId = webhookBody.getAppId();
        } catch (Exception e) {
            appId = null;
        }

        String responseId = Utils.getUniqueId();
        String reference = Utils.getUniqueId();

        JSONObject record = new JSONObject();
        record.put("_id", responseId);

        try {
            Object bodyObj = null;
            try {
                bodyObj = webhookBody.getBody();
            } catch (Exception e) {
                bodyObj = null;
            }
            if (bodyObj != null) {
                record.put("payload", bodyObj);
            } else {
                record.put("payload", "");
            }
        } catch (Exception e) {
            record.put("payload", "");
        }

        try {
            record.put("received_at", new Long(System.currentTimeMillis()));
        } catch (Exception e) {
        }

        DatabaseService.getInstance().set(api, record, TABLE_NAME, responseId, reference);

        notifyAdminNewResponse(responseId, appId);
    }

    @Override
    public void onReceive(IncomingMessage incomingMsg) {
        if (incomingMsg == null) {
            return;
        }
        if (incomingMsg.getFrom() == null) {
            return;
        }

        String userId = incomingMsg.getFrom().getId();
        String appId = incomingMsg.getAppId();

        if (userId == null || !ADMIN_ID.equals(userId)) {
            return;
        }

        String chatId = null;
        if (incomingMsg.getChat() != null) {
            chatId = incomingMsg.getChat().getId();
        }
        if (chatId == null) {
            return;
        }

        String text = incomingMsg.getText();
        if (text == null) {
            return;
        }
        text = text.trim();
        if (text.length() == 0) {
            return;
        }

        Integer chatSettings = incomingMsg.getChatSettings();

        if (text.equals("/list")) {
            this.adminChatId = chatId;
            this.adminAppId = appId;
            this.adminChatSettings = chatSettings;

            String reference = Utils.getUniqueId();
            try {
                DatabaseService.getInstance().list(api, TABLE_NAME, reference);
                sendAdminText(chatId, "List requested.", userId, chatSettings, appId);
            } catch (Exception e) {
                sendAdminText(chatId, "List operation is not supported in the current environment.", userId, chatSettings, appId);
            }
            return;
        }

        if (text.startsWith("/get")) {
            String id = parseSecondToken(text);
            if (id == null || id.length() == 0) {
                sendAdminText(chatId, "Usage: /get <id>", userId, chatSettings, appId);
                return;
            }

            this.adminChatId = chatId;
            this.adminAppId = appId;
            this.adminChatSettings = chatSettings;

            String reference = Utils.getUniqueId();
            DatabaseService.getInstance().get(api, id, TABLE_NAME, reference);
            return;
        }

        if (text.startsWith("/delete")) {
            String id = parseSecondToken(text);
            if (id == null || id.length() == 0) {
                sendAdminText(chatId, "Usage: /delete <id>", userId, chatSettings, appId);
                return;
            }

            this.adminChatId = chatId;
            this.adminAppId = appId;
            this.adminChatSettings = chatSettings;

            String reference = Utils.getUniqueId();
            DatabaseService.getInstance().delete(api, id, TABLE_NAME, reference);
            sendAdminText(chatId, "Delete requested for id: " + id, userId, chatSettings, appId);
            return;
        }

        sendAdminText(chatId, "Commands:\n/list\n/get <id>\n/delete <id>", userId, chatSettings, appId);
    }

    @Override
    public void onExtensionDocResponse(ExtensionDocResponse extensionDocResponse) {
        if (extensionDocResponse == null) {
            return;
        }

        String appId = null;
        try {
            appId = extensionDocResponse.getAppId();
        } catch (Exception e) {
            appId = null;
        }

        JSONArray docs = null;
        try {
            docs = extensionDocResponse.getDocs();
        } catch (Exception e) {
            docs = null;
        }
        if (docs != null) {
            sendDocsListToAdmin(docs, appId);
            return;
        }

        JSONObject doc = null;
        try {
            doc = extensionDocResponse.getDoc();
        } catch (Exception e) {
            doc = null;
        }
        if (doc != null) {
            sendDocToAdmin(doc, appId);
            return;
        }

        sendTextToAdmin("Database operation completed.", appId);
    }

    private void notifyAdminNewResponse(String responseId, String appId) {
        String msg = "New Google Form response received. ID: " + responseId + "\nUse /get " + responseId + " to view.";
        sendTextToAdmin(msg, appId);
    }

    private void sendTextToAdmin(String text, String appId) {
        if (api == null) {
            return;
        }

        String targetChatId = adminChatId != null ? adminChatId : ADMIN_ID;
        String targetAppId = adminAppId != null ? adminAppId : appId;
        Integer targetChatSettings = adminChatSettings;

        try {
            api.sendText(
                    targetChatId,
                    text,
                    Utils.getUniqueId(),
                    null,
                    ADMIN_ID,
                    new Integer(0),
                    Boolean.FALSE,
                    targetChatSettings,
                    null,
                    null,
                    null,
                    targetAppId
            );
        } catch (Exception e) {
        }
    }

    private void sendAdminText(String chatId, String text, String userId, Integer chatSettings, String appId) {
        if (api == null) {
            return;
        }
        String reference = Utils.getUniqueId();
        api.sendText(
                chatId,
                text,
                reference,
                null,
                userId,
                new Integer(0),
                Boolean.FALSE,
                chatSettings,
                null,
                null,
                null,
                appId
        );
    }

    private void sendDocToAdmin(JSONObject doc, String appId) {
        StringBuffer sb = new StringBuffer();
        Object id = doc.get("_id");
        if (id == null) {
            id = doc.get("id");
        }
        if (id == null) {
            id = "(unknown)";
        }

        sb.append("Response ID: ").append(String.valueOf(id));

        Object receivedAt = doc.get("received_at");
        if (receivedAt != null) {
            sb.append("\nReceived at: ").append(String.valueOf(receivedAt));
        }

        Object payload = doc.get("payload");
        if (payload != null) {
            sb.append("\n\nPayload:\n").append(String.valueOf(payload));
        } else {
            sb.append("\n\nPayload: (empty)");
        }

        sendTextToAdmin(sb.toString(), appId);
    }

    private void sendDocsListToAdmin(JSONArray docs, String appId) {
        if (docs == null || docs.size() == 0) {
            sendTextToAdmin("No records found.", appId);
            return;
        }

        StringBuffer sb = new StringBuffer();
        sb.append("Records found: ").append(docs.size());

        int limit = docs.size();
        if (limit > 20) {
            limit = 20;
        }

        for (int i = 0; i < limit; i++) {
            Object item = docs.get(i);
            sb.append("\n\n").append(i + 1).append(") ");

            if (item instanceof JSONObject) {
                JSONObject jo = (JSONObject) item;
                Object id = jo.get("_id");
                if (id == null) {
                    id = jo.get("id");
                }
                sb.append("ID: ").append(id != null ? String.valueOf(id) : "(unknown)");
            } else {
                sb.append(String.valueOf(item));
            }
        }

        if (docs.size() > limit) {
            sb.append("\n\nShowing first ").append(limit).append(" records.");
        }

        sendTextToAdmin(sb.toString(), appId);
    }

    private String parseSecondToken(String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        int sp = t.indexOf(' ');
        if (sp < 0) {
            return null;
        }
        String rest = t.substring(sp + 1);
        rest = rest.trim();
        if (rest.length() == 0) {
            return null;
        }
        int sp2 = rest.indexOf(' ');
        if (sp2 >= 0) {
            rest = rest.substring(0, sp2).trim();
        }
        if (rest.length() >= 2) {
            char c0 = rest.charAt(0);
            char c1 = rest.charAt(rest.length() - 1);
            if ((c0 == '\'' && c1 == '\'') || (c0 == '"' && c1 == '"')) {
                rest = rest.substring(1, rest.length() - 1);
            }
        }
        return rest;
    }
}
