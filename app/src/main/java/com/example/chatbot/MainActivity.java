package com.example.chatbot;

import android.os.Bundle;
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

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    RecyclerView recyclerView;
    TextView welcomeTextView;
    EditText messageEditText;
    ImageButton sendButton;

    List<Message> messagelist;
    MessageAdapter messageAdapter;

    String apiUrl = "https://api.openai.com/v1/chat/completions";
    String apiKey = "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";
    OkHttpClient client = new OkHttpClient();

    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        messagelist = new ArrayList<>();

        recyclerView = findViewById(R.id.recycler_view);
        welcomeTextView = findViewById(R.id.welcome_text);
        messageEditText = findViewById(R.id.message_edit_text);
        sendButton = findViewById(R.id.send_btn);

        // Setup recycler view
        messageAdapter = new MessageAdapter(messagelist);
        recyclerView.setAdapter(messageAdapter);
        LinearLayoutManager llm = new LinearLayoutManager(this);
        llm.setStackFromEnd(true);
        recyclerView.setLayoutManager(llm);

        sendButton.setOnClickListener((v) -> {
            String question = messageEditText.getText().toString().trim();
            addtoChat(question, Message.SENT_BY_ME);
            messageEditText.setText("");
            callAPI(question);
            welcomeTextView.setVisibility(View.GONE);
        });
    }

    // Add a message to the chat
    void addtoChat(String message, String sentBy) {
        runOnUiThread(() -> {
            messagelist.add(new Message(message, sentBy));
            messageAdapter.notifyDataSetChanged();
            recyclerView.smoothScrollToPosition(messageAdapter.getItemCount());
        });
    }

    void addResponse(String response) {
        addtoChat(response, Message.SENT_BY_BOT);
    }

    void callAPI(String question) {
        callAPIWithRetry(question, 0, 5);  // Adjust the maximum retry count as needed
    }

    void callAPIWithRetry(String question, int retryCount, int maxRetries) {
        if (retryCount < maxRetries) {
            // Combine user messages into a single input
            String combinedUserMessages = String.join("\n", question);

            // OkHttp
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
                    // Implement retry logic
                    if (e instanceof IOException) {
                        // Handle other network errors (optional)
                    } else {
                        // Handle other exceptions or errors (optional)
                    }
                    // Retry the API call
                    int delaySeconds = (int) Math.pow(2, retryCount);  // Exponential backoff
                    try {
                        Thread.sleep(delaySeconds * 1000);
                        callAPIWithRetry(combinedUserMessages, retryCount + 1, maxRetries);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    try (response) {
                        if (response.isSuccessful()) {
                            JSONObject jsonObject = new JSONObject(response.body().string());
                            JSONArray jsonArray = jsonObject.getJSONArray("choices");
                            String result = jsonArray.getJSONObject(0).getString("message").trim();
                            addResponse(result);
                        } else {
                            addResponse("Failed to Load: " + response.code() + " - " + response.message());
                        }
                    } catch (IOException | JSONException e) {
                        addResponse("Error: " + e.getMessage());
                    }
                }
            });
        } else {
            addResponse("Max retry attempts reached.");
        }
    }
}
