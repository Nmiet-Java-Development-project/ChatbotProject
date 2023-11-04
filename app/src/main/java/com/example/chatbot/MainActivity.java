package com.example.chatbot;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private TextView welcomeTextView;
    private EditText messageEditText;
    private ImageButton sendButton;

    private List<Message> messageList;
    private MessageAdapter messageAdapter;

    private String apiUrl = "https://api.openai.com/v1/chat/completions";
    private String apiKey = "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";
    private OkHttpClient client = new OkHttpClient();

    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private Handler uiHandler = new Handler(Looper.getMainLooper());

    // Create an ExecutorService to handle asynchronous tasks
    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        messageList = new ArrayList<>();

        recyclerView = findViewById(R.id.recycler_view);
        welcomeTextView = findViewById(R.id.welcome_text);
        messageEditText = findViewById(R.id.message_edit_text);
        sendButton = findViewById(R.id.send_btn);

        // Setup recycler view
        messageAdapter = new MessageAdapter(messageList);
        recyclerView.setAdapter(messageAdapter);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);

        messageEditText.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                if (!event.isShiftPressed()) {
                    sendMessage();
                }
                return true;
            }
            return false;
        });

        sendButton.setOnClickListener(v -> sendMessage());
    }

    private void sendMessage() {
        String question = messageEditText.getText().toString().trim();
        if (!question.isEmpty()) {
            addMessage(question, Message.SENT_BY_ME);
            messageEditText.setText("");
            // Perform API call asynchronously
            executorService.submit(() -> callAPI(question));
            welcomeTextView.setVisibility(View.GONE);
        }
    }

    private void addMessage(String message, String sentBy) {
        uiHandler.post(() -> {
            messageList.add(new Message(message, sentBy));
            messageAdapter.notifyDataSetChanged();
            recyclerView.smoothScrollToPosition(messageAdapter.getItemCount());
        });
    }

    private void addResponse(String response) {
        addMessage(response, Message.SENT_BY_BOT);
    }

    private void callAPI(String question) {
        String combinedUserMessages = String.join("\n", question);
        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("model", "gpt-3.5-turbo");
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content", "You are a helpful assistant."));
            messages.put(new JSONObject().put("role", "user").put("content", combinedUserMessages));
            jsonBody.put("messages", messages);
            jsonBody.put("max_tokens", 256);
            jsonBody.put("temperature", 1);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        RequestBody body = RequestBody.create(jsonBody.toString(), JSON);
        Request request = new Request.Builder()
                .url(apiUrl)
                .header("Authorization", "Bearer " + apiKey)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                addResponse("Failed to Load: " + e.getMessage());
                // Handle retries asynchronously
                retryAPIRequest(combinedUserMessages, 0, 5);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (response) {
                    if (response.isSuccessful()) {
                        JSONObject jsonObject = new JSONObject(response.body().string());
                        JSONArray jsonArray = jsonObject.getJSONArray("choices");
                        String result = jsonArray.getJSONObject(0).getString("message").trim();

                        // Extract and display only the content of the assistant's response
                        JSONObject resultJSON = new JSONObject(result);
                        String assistantResponse = resultJSON.getString("content");
                        addResponse(assistantResponse);
                    } else {
                        addResponse("Failed to Load: " + response.code() + " - " + response.message());
                    }
                } catch (IOException | JSONException e) {
                    addResponse("Error: " + e.getMessage());
                }
            }
        });
    }

    private void retryAPIRequest(String question, int retryCount, int maxRetries) {
        if (retryCount < maxRetries) {
            int delaySeconds = (int) Math.pow(2, retryCount);
            try {
                Thread.sleep(delaySeconds * 1000);
                // Retry the API call asynchronously
                executorService.submit(() -> callAPI(question));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else {
            addResponse("Max retry attempts reached.");
        }
    }
}
