package cz.webflex.bbwa;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import cz.webflex.bbwa.api.ApiClient;
import cz.webflex.bbwa.ContactResolver;
import cz.webflex.bbwa.model.Message;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MessageActivity extends Activity {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final long POLL_INTERVAL = 4000;
    private static final int REQUEST_PICK_IMAGE = 1;
    private static final int MAX_SEND_PX = 800;

    private ListView listView;
    private EditText inputField;
    private Button sendButton;
    private Button attachButton;
    private List<Message> messages = new ArrayList<Message>();
    private MessageAdapter adapter;
    private Handler handler = new Handler(Looper.getMainLooper());
    private String chatId;
    // Name as passed from the chat list — used as final fallback (WhatsApp pushName).
    private String fallbackName;
    // Currently displayed name (alias > contact > fallbackName).
    private String currentChatName;
    private boolean polling = false;
    private long lastClickTime = 0;
    private int lastClickPosition = -1;

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
        fallbackName = getIntent().getStringExtra("chatName");

        // Resolve display name using priority system.
        String number = ContactResolver.extractNumber(chatId);
        String resolved = ContactResolver.getName(this, number);
        currentChatName = resolved != null ? resolved : fallbackName;
        if (currentChatName != null) {
            setTitle(currentChatName);
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

        attachButton = (Button) findViewById(R.id.attach_button);
        attachButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { pickImage(); }
        });
        ImageLoader.init();
        ImageLoader.cleanup(MessageActivity.this);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                long now = System.currentTimeMillis();
                if (position == lastClickPosition && now - lastClickTime < 300) {
                    Message msg = (Message) adapter.getItem(position);
                    sendReactionAsync(msg, "\u2764\uFE0F");
                }
                lastClickTime = now;
                lastClickPosition = position;
            }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> parent, View view,
                                           final int position, long id) {
                final Message msg = (Message) adapter.getItem(position);
                final String[] options = {"\u2764\uFE0F", "\uD83D\uDE02", "\uD83D\uDE2E",
                        "\uD83D\uDE22", "\uD83D\uDE4F", "\uD83D\uDC4D", "Remove Reaction"};
                new AlertDialog.Builder(MessageActivity.this)
                    .setTitle("React")
                    .setItems(options, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            String emoji = options[which].equals("Remove Reaction")
                                    ? "" : options[which];
                            sendReactionAsync(msg, emoji);
                        }
                    })
                    .show();
                return true;
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

    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_message, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_rename) {
            showRenameDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showRenameDialog() {
        final EditText input = new EditText(this);
        input.setText(currentChatName != null ? currentChatName : "");
        input.setSingleLine(true);
        // Add comfortable horizontal padding so text doesn't hug the dialog edge.
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, input.getPaddingTop(), pad, input.getPaddingBottom());

        new AlertDialog.Builder(this)
            .setTitle("Rename contact")
            .setView(input)
            .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    String alias = input.getText().toString().trim();
                    String number = ContactResolver.extractNumber(chatId);
                    if (alias.length() > 0) {
                        ContactResolver.saveAlias(MessageActivity.this, number, alias);
                        currentChatName = alias;
                    } else {
                        // Empty input clears the alias, reverting to contact/pushName.
                        ContactResolver.removeAlias(MessageActivity.this, number);
                        ContactResolver.clearCache();
                        String resolved = ContactResolver.getName(MessageActivity.this, number);
                        currentChatName = resolved != null ? resolved : fallbackName;
                    }
                    ContactResolver.clearCache();
                    setTitle(currentChatName != null ? currentChatName : "");
                    adapter.notifyDataSetChanged();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    protected void onResume() {
        super.onResume();

        // Re-query sources in case the user added/edited a contact while away.
        ContactResolver.clearCache();
        if (chatId != null) {
            String number = ContactResolver.extractNumber(chatId);
            String resolved = ContactResolver.getName(this, number);
            currentChatName = resolved != null ? resolved : fallbackName;
            setTitle(currentChatName != null ? currentChatName : "");
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        }

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

    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        startActivityForResult(i, REQUEST_PICK_IMAGE);
    }

    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQUEST_PICK_IMAGE || res != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;

        InputStream in = null;
        try {
            // Pass 1: bounds only
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            in = getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(in, null, bounds);
            in.close(); in = null;

            // Compute inSampleSize (target MAX_SEND_PX)
            int s = 1, h = bounds.outHeight, w = bounds.outWidth;
            if (h > MAX_SEND_PX || w > MAX_SEND_PX) {
                int hh = h / 2, hw = w / 2;
                while ((hh / s) >= MAX_SEND_PX && (hw / s) >= MAX_SEND_PX) s *= 2;
            }

            // Pass 2: decode with sampling
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize      = s;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            opts.inPurgeable       = true;
            opts.inInputShareable  = true;
            in = getContentResolver().openInputStream(uri);
            Bitmap bmp = BitmapFactory.decodeStream(in, null, opts);
            in.close(); in = null;

            if (bmp == null) {
                Toast.makeText(this, "Could not read image", Toast.LENGTH_SHORT).show();
                return;
            }

            // Compress to JPEG 70%
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 70, baos);
            bmp.recycle();
            uploadImage(baos.toByteArray());

        } catch (IOException e) {
            Toast.makeText(this, "Could not read image", Toast.LENGTH_SHORT).show();
        } finally {
            if (in != null) try { in.close(); } catch (IOException ignored) {}
        }
    }

    private void uploadImage(final byte[] bytes) {
        String url    = ApiClient.getBaseUrl() + "/api/messages/sendMedia";
        String number = ContactResolver.extractNumber(chatId);

        RequestBody fileBody = RequestBody.create(
                okhttp3.MediaType.parse("image/jpeg"), bytes);
        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", "photo.jpg", fileBody)
                .addFormDataPart("number", number)
                .build();

        Request request = new Request.Builder().url(url).post(body).build();
        ApiClient.getClient().newCall(request).enqueue(new okhttp3.Callback() {
            public void onFailure(okhttp3.Call call, IOException e) {
                handler.post(new Runnable() { public void run() {
                    Toast.makeText(MessageActivity.this,
                            "Failed to send image", Toast.LENGTH_SHORT).show();
                }});
            }
            public void onResponse(okhttp3.Call call, okhttp3.Response response)
                    throws IOException {
                response.close();
                handler.post(new Runnable() { public void run() { fetchMessages(); }});
            }
        });
    }

    public void onLowMemory() {
        super.onLowMemory();
        ImageLoader.clearMemCache();
    }

    private void sendReactionAsync(final Message msg, final String emoji) {
        // Optimistic update
        msg.setReaction(emoji.length() > 0 ? emoji : null);
        adapter.notifyDataSetChanged();

        String url = ApiClient.getBaseUrl() + "/api/messages/reaction";
        String escapedChatId = chatId.replace("\"", "\\\"");
        String escapedMsgId  = msg.getId().replace("\"", "\\\"");
        String escapedEmoji  = emoji.replace("\"", "\\\"");
        String json = "{\"chatId\":\"" + escapedChatId
                + "\",\"messageId\":\"" + escapedMsgId
                + "\",\"originalFromMe\":" + msg.isFromMe()
                + ",\"emoji\":\"" + escapedEmoji + "\"}";
        RequestBody body = RequestBody.create(JSON, json);
        Request request = new Request.Builder().url(url).post(body).build();
        ApiClient.getClient().newCall(request).enqueue(new Callback() {
            public void onFailure(Call call, IOException e) {}
            public void onResponse(Call call, Response response) throws IOException {
                response.close();
            }
        });
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
            ImageView imageView = (ImageView) convertView.findViewById(R.id.message_image);

            if (msg.isFromMe()) {
                senderView.setText("You");
            } else {
                String sender = msg.getSender();
                String number = ContactResolver.extractNumber(sender);
                String contactName = ContactResolver.getName(MessageActivity.this, number);
                senderView.setText(contactName != null ? contactName : (sender != null ? sender : ""));
            }

            if ("image".equals(msg.getType()) && msg.getMediaId() != null) {
                imageView.setVisibility(View.VISIBLE);
                String mediaUrl = ApiClient.getBaseUrl() + "/api/media/" + msg.getMediaId();
                ImageLoader.load(MessageActivity.this, msg.getMediaId(), mediaUrl, imageView);
                if (msg.getText() != null && msg.getText().length() > 0) {
                    textView.setText(msg.getText());
                    textView.setVisibility(View.VISIBLE);
                } else {
                    textView.setVisibility(View.GONE);
                }
            } else {
                imageView.setVisibility(View.GONE);
                imageView.setTag(null);
                textView.setText(msg.getText() != null ? msg.getText() : "");
                textView.setVisibility(View.VISIBLE);
            }

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
