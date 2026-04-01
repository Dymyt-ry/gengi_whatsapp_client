package cz.webflex.bbwa;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import cz.webflex.bbwa.api.ApiClient;
import cz.webflex.bbwa.model.Message;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MessageActivity extends Activity {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final long POLL_INTERVAL = 4000;

    private ListView listView;
    private EditText inputField;
    private Button sendButton;
    private List<Message> messages = new ArrayList<Message>();
    private MessageAdapter adapter;
    private Handler handler = new Handler(Looper.getMainLooper());
    private String chatId;
    private boolean polling = false;

    private Runnable pollRunnable = new Runnable() {
        public void run() {
            if (polling) {
                fetchMessages();
                handler.postDelayed(this, POLL_INTERVAL);
            }
        }
    };

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_message);

        chatId = getIntent().getStringExtra("chatId");
        String chatName = getIntent().getStringExtra("chatName");
        if (chatName != null) {
            setTitle(chatName);
        }

        listView = (ListView) findViewById(R.id.message_list);
        inputField = (EditText) findViewById(R.id.message_input);
        sendButton = (Button) findViewById(R.id.send_button);

        adapter = new MessageAdapter();
        listView.setAdapter(adapter);

        sendButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                trySend();
            }
        });

        // Hardware keyboard: Enter key sends
        inputField.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                    trySend();
                    return true;
                }
                return false;
            }
        });

        // Soft keyboard: IME action send
        inputField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    trySend();
                    return true;
                }
                return false;
            }
        });

        fetchMessages();
    }

    protected void onResume() {
        super.onResume();
        polling = true;
        handler.postDelayed(pollRunnable, POLL_INTERVAL);
    }

    protected void onPause() {
        super.onPause();
        polling = false;
        handler.removeCallbacks(pollRunnable);
    }

    private void trySend() {
        String text = inputField.getText().toString().trim();
        if (text.length() > 0) {
            sendMessage(text);
            inputField.setText("");
        }
    }

    private void fetchMessages() {
        String url = ApiClient.getBaseUrl() + "/chat/" + chatId;
        Request request = new Request.Builder().url(url).get().build();

        ApiClient.getClient().newCall(request).enqueue(new Callback() {
            public void onFailure(Call call, IOException e) {
                handler.post(new Runnable() {
                    public void run() {
                        Toast.makeText(MessageActivity.this, "Failed to load messages", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    response.close();
                    return;
                }

                String body = response.body().string();
                Type listType = new TypeToken<List<Message>>() {}.getType();
                final List<Message> result = new Gson().fromJson(body, listType);

                handler.post(new Runnable() {
                    public void run() {
                        messages.clear();
                        messages.addAll(result);
                        adapter.notifyDataSetChanged();
                        if (messages.size() > 0) {
                            listView.setSelection(messages.size() - 1);
                        }
                    }
                });
            }
        });
    }

    private void sendMessage(String text) {
        String url = ApiClient.getBaseUrl() + "/send";
        String json = "{\"chatId\":\"" + chatId.replace("\"", "\\\"") + "\",\"text\":\"" + text.replace("\"", "\\\"") + "\"}";
        RequestBody requestBody = RequestBody.create(JSON, json);
        Request request = new Request.Builder().url(url).post(requestBody).build();

        ApiClient.getClient().newCall(request).enqueue(new Callback() {
            public void onFailure(Call call, IOException e) {
                handler.post(new Runnable() {
                    public void run() {
                        Toast.makeText(MessageActivity.this, "Failed to send", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            public void onResponse(Call call, Response response) throws IOException {
                response.close();
                handler.post(new Runnable() {
                    public void run() {
                        fetchMessages();
                    }
                });
            }
        });
    }

    private class MessageAdapter extends BaseAdapter {

        public int getCount() {
            return messages.size();
        }

        public Object getItem(int position) {
            return messages.get(position);
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(MessageActivity.this)
                        .inflate(R.layout.item_message, parent, false);
            }

            Message msg = messages.get(position);
            TextView textView = (TextView) convertView.findViewById(R.id.message_text);
            TextView senderView = (TextView) convertView.findViewById(R.id.message_sender);
            TextView reactionView = (TextView) convertView.findViewById(R.id.message_reaction);

            textView.setText(msg.getText() != null ? msg.getText() : "");
            senderView.setText(msg.isFromMe() ? "You" : (msg.getSender() != null ? msg.getSender() : ""));

            String reaction = msg.getReaction();
            if (reaction != null && reaction.length() > 0) {
                reactionView.setText(reaction);
                reactionView.setVisibility(View.VISIBLE);
            } else {
                reactionView.setVisibility(View.GONE);
            }

            return convertView;
        }
    }
}
