// In-memory message store populated by webhook events.
// No TTL — data lives until process restart (by design).

var chats = {};    // chatId → {id, name, lastMessage, timestamp}
var messages = {};  // chatId → [message, ...]
var seenIds = {};   // messageId → true (dedup)
var lidToPhone = {}; // @lid jid → resolved phone chatId (e.g. 123@s.whatsapp.net)

function upsertMessage(msg) {
  if (seenIds[msg.id]) return;
  seenIds[msg.id] = true;

  var chatId = msg.chatId;

  // Update chat entry
  chats[chatId] = {
    id: chatId,
    name: msg.pushName || chats[chatId] && chats[chatId].name || chatId,
    lastMessage: msg.text || '',
    timestamp: msg.timestamp
  };

  // Append to messages
  if (!messages[chatId]) {
    messages[chatId] = [];
  }
  messages[chatId].push({
    id: msg.id,
    text: msg.text || '',
    fromMe: msg.fromMe,
    sender: msg.pushName || null,
    timestamp: msg.timestamp
  });
}

function getChats() {
  var list = [];
  var keys = Object.keys(chats);
  for (var i = 0; i < keys.length; i++) {
    list.push(chats[keys[i]]);
  }
  // Sort by timestamp descending
  list.sort(function (a, b) { return b.timestamp - a.timestamp; });
  return list;
}

function getMessages(chatId) {
  return messages[chatId] || [];
}

function addSentMessage(chatId, text, messageId) {
  var ts = Math.floor(Date.now() / 1000);
  var msg = {
    id: messageId || ('sent_' + ts + '_' + Math.random().toString(36).substr(2, 6)),
    text: text,
    fromMe: true,
    sender: null,
    timestamp: ts,
    chatId: chatId
  };
  upsertMessage(msg);
  return msg;
}

function resolveLid(lid) {
  return lidToPhone[lid] || null;
}

function storeLidMapping(lid, phoneChatId) {
  lidToPhone[lid] = phoneChatId;
}

module.exports = {
  upsertMessage: upsertMessage,
  getChats: getChats,
  getMessages: getMessages,
  addSentMessage: addSentMessage,
  resolveLid: resolveLid,
  storeLidMapping: storeLidMapping
};
