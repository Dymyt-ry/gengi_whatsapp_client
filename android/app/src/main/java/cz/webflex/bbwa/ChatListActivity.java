package cz.webflex.bbwa;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import cz.webflex.bbwa.api.ApiClient;
import cz.webflex.bbwa.model.Chat;
import cz.webflex.bbwa.service.PollingService;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

public class ChatListActivity extends Activity {

    private static final long POLL_INTERVAL = 4000;

    private ListView listView;
    private List<Chat> chats = new ArrayList<Chat>();
    private ChatAdapter adapter;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean polling = false;

    private Runnable pollRunnable = new Runnable() {
        public void run() {
            if (polling) {
                fetchChats();
                handler.postDelayed(this, POLL_INTERVAL);
            }
        }
    };

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_list);

        PollingService.schedulePolling(this);

        listView = (ListView) findViewById(R.id.chat_list);
        adapter = new ChatAdapter();
        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Chat chat = chats.get(position);
                Intent intent = new Intent(ChatListActivity.this, MessageActivity.class);
                intent.putExtra("chatId", chat.getId());
                intent.putExtra("chatName", chat.getName());
                startActivity(intent);
            }
        });

        fetchChats();
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

    private void fetchChats() {
        String url = ApiClient.getBaseUrl() + "/chats";
        Request request = new Request.Builder().url(url).get().build();

        ApiClient.getClient().newCall(request).enqueue(new Callback() {
            public void onFailure(Call call, IOException e) {
                handler.post(new Runnable() {
                    public void run() {
                        Toast.makeText(ChatListActivity.this, "Failed to load chats", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    response.close();
                    return;
                }

                String body = response.body().string();
                Type listType = new TypeToken<List<Chat>>() {}.getType();
                final List<Chat> result = new Gson().fromJson(body, listType);

                handler.post(new Runnable() {
                    public void run() {
                        chats.clear();
                        chats.addAll(result);
                        adapter.notifyDataSetChanged();
                    }
                });
            }
        });
    }

    private class ChatAdapter extends BaseAdapter {

        public int getCount() {
            return chats.size();
        }

        public Object getItem(int position) {
            return chats.get(position);
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(ChatListActivity.this)
                        .inflate(R.layout.item_chat, parent, false);
            }

            Chat chat = chats.get(position);
            TextView nameView = (TextView) convertView.findViewById(R.id.chat_name);
            TextView lastMsgView = (TextView) convertView.findViewById(R.id.chat_last_message);

            nameView.setText(chat.getName() != null ? chat.getName() : chat.getId());
            lastMsgView.setText(chat.getLastMessage() != null ? chat.getLastMessage() : "");

            return convertView;
        }
    }
}
