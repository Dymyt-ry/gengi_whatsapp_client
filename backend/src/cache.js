// In-memory message store populated by webhook events.
// No TTL — data lives until process restart (by design).

var chats = {};    // chatId → {id, name, customName, lastMessage, timestamp}
var messages = {};  // chatId → [message, ...]
var seenIds = {};   // messageId → true (dedup)
var lidToPhone = {}; // @lid jid → resolved phone chatId (e.g. 123@s.whatsapp.net)

function getChatName(msg, existingChat) {
  // customName always wins
  if (existingChat && existingChat.customName) return existingChat.customName;
  // Groups
  if (msg.chatId.indexOf('@g.us') !== -1) {
    return (existingChat && existingChat.name) || 'Skupina';
  }
  // Incoming message with pushName → use pushName (counterparty name)
  if (!msg.fromMe && msg.pushName) return msg.pushName;
  // Outgoing or no pushName → preserve existing name or fall back to chatId
  return (existingChat && existingChat.name) || msg.chatId;
}

function upsertMessage(msg) {
  if (seenIds[msg.id]) return;
  seenIds[msg.id] = true;

  var chatId = msg.chatId;

  // Update chat entry
  chats[chatId] = {
    id: chatId,
    name: getChatName(msg, chats[chatId]),
    customName: (chats[chatId] && chats[chatId].customName) || null,
    lastMessage: msg.text || '',
    timestamp: msg.timestamp,
    unreadCount: (chats[chatId] && chats[chatId].unreadCount) || 0
  };

  if (!msg.fromMe) {
    chats[chatId].unreadCount++;
  }

  // Append to messages
  if (!messages[chatId]) {
    messages[chatId] = [];
  }

  var notifyText = null;
  if (!msg.fromMe) {
    var senderName = msg.pushName || msg.chatId;
    notifyText = senderName + ': ' + (msg.text || '[Média/Jiná zpráva]');
  }

  messages[chatId].push({
    id: msg.id,
    text: msg.text || '',
    fromMe: msg.fromMe,
    sender: msg.pushName || null,
    timestamp: msg.timestamp,
    notify: !msg.fromMe,
    notifyText: notifyText
  });
}

function renameChat(chatId, customName) {
  if (!chats[chatId]) {
    chats[chatId] = { id: chatId, name: customName, customName: customName, lastMessage: '', timestamp: 0 };
  } else {
    chats[chatId].customName = customName;
    chats[chatId].name = customName;
  }
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

function getChatEntry(chatId) {
  return chats[chatId] || null;
}

function updateGroupName(chatId, name) {
  if (chats[chatId] && !chats[chatId].customName) {
    chats[chatId].name = name;
  }
}

function clearUnread(chatId) {
  if (chats[chatId]) chats[chatId].unreadCount = 0;
}

function addReaction(chatId, targetMsgId, emoji) {
  var msgs = messages[chatId];
  if (!msgs) return;
  for (var i = 0; i < msgs.length; i++) {
    if (msgs[i].id === targetMsgId) {
      msgs[i].reaction = emoji || null;
      return;
    }
  }
}

module.exports = {
  upsertMessage: upsertMessage,
  getChats: getChats,
  getMessages: getMessages,
  addSentMessage: addSentMessage,
  renameChat: renameChat,
  resolveLid: resolveLid,
  storeLidMapping: storeLidMapping,
  getChatEntry: getChatEntry,
  updateGroupName: updateGroupName,
  clearUnread: clearUnread,
  addReaction: addReaction
};
