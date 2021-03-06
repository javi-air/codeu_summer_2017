// Copyright 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package codeu.chat.server;

import java.util.Comparator;
import java.util.HashMap;

import codeu.chat.common.ConversationHeader;
import codeu.chat.common.ConversationPayload;
import codeu.chat.common.LinearUuidGenerator;
import codeu.chat.common.Message;
import codeu.chat.common.User;
import codeu.chat.common.CleverBotUser;
import codeu.chat.util.Time;
import codeu.chat.util.Uuid;
import codeu.chat.util.store.Store;
import codeu.chat.util.store.StoreAccessor;

public final class Model {

  private static final Comparator<Uuid> UUID_COMPARE = new Comparator<Uuid>() {

    @Override
    public int compare(Uuid a, Uuid b) {

      if (a == b) { return 0; }

      if (a == null && b != null) { return -1; }

      if (a != null && b == null) { return 1; }

      final int order = Integer.compare(a.id(), b.id());
      return order == 0 ? compare(a.root(), b.root()) : order;
    }
  };

  private static final Comparator<Time> TIME_COMPARE = new Comparator<Time>() {
    @Override
    public int compare(Time a, Time b) {
      return a.compareTo(b);
    }
  };

  private static final Comparator<String> STRING_COMPARE = String.CASE_INSENSITIVE_ORDER;

  private final Store<Uuid, User> userById = new Store<>(UUID_COMPARE);
  private final Store<Time, User> userByTime = new Store<>(TIME_COMPARE);
  private final Store<String, User> userByText = new Store<>(STRING_COMPARE);

  private final Store<Uuid, ConversationHeader> conversationById = new Store<>(UUID_COMPARE);
  private final Store<Time, ConversationHeader> conversationByTime = new Store<>(TIME_COMPARE);
  private final Store<String, ConversationHeader> conversationByText = new Store<>(STRING_COMPARE);

  private final Store<Uuid, ConversationPayload> conversationPayloadById = new Store<>(UUID_COMPARE);

  private final Store<Uuid, Message> messageById = new Store<>(UUID_COMPARE);
  private final Store<Time, Message> messageByTime = new Store<>(TIME_COMPARE);
  private final Store<String, Message> messageByText = new Store<>(STRING_COMPARE);

  // TODO: remove stucture all together to mimick UserFollowing Class implementation for users
  private final HashMap<Uuid, HashMap<Uuid, Integer>> userConversationTracking = new HashMap<Uuid, HashMap<Uuid, Integer>>();

  private final String version = "1.1";
  private final Time serverStartTime = Time.now();

  public int togglePermission(Uuid user, Uuid targetUser, int permission, Uuid conversation) {
    ConversationHeader foundConversation = conversationById().first(conversation);
    int sourcePerm = foundConversation.getPermission(user);

    int targetPerm = foundConversation.getPermission(targetUser);
    targetPerm = (targetPerm == -1) ? 0 : targetPerm;
    int permissionDiff = permission ^ targetPerm & 0b0111;

    if (((permissionDiff & ConversationHeader.ADMIN_PERM) >= 1)  && !ConversationHeader.isOwner(sourcePerm)) {
      return -1;
    }
    if (((permissionDiff & ConversationHeader.MEMBER_PERM) >= 1) && !(ConversationHeader.isOwner(sourcePerm) || ConversationHeader.isAdmin(sourcePerm))) {
      return -1;
    }
    foundConversation.togglePermission(targetUser, (byte) permission);
    return foundConversation.getPermission(targetUser);
  }
  
  public CleverBotUser addBot(CleverBotUser user, Uuid conversation) {
    ConversationPayload foundConversation = conversationPayloadById().first(conversation);
    System.out.println("This the conversation: " + foundConversation);
    foundConversation.bots.add(user);
    return user;
  }

  public void add(User user) {
    userConversationTracking.put(user.id, new HashMap<Uuid, Integer>());
    userById.insert(user.id, user);
    userByTime.insert(user.creation, user);
    userByText.insert(user.name, user);
  }

  public String statusUpdate(Uuid user) {
    StringBuilder status = new StringBuilder();
    HashMap<Uuid, Integer> userConversationSize = userConversationTracking.get(user);
    for (Uuid conversation : userConversationSize.keySet()) {
      ConversationHeader convo = conversationById().first(conversation);
      String title = convo.title;
      int newMessages = convo.size - userConversationSize.get(conversation);
      String line = String.format("CONVERSATION %s: You have %d new messages!\n", title, newMessages);
      status.append(line);
      userConversationTracking.get(user).put(conversation, convo.size);
    }
    User userA = userById().first(user);
    status.append(userA.statusUpdate());
    return status.toString();
  }

  public void unfollowUser(User userA, User userB) {
    User user1 = userById().first(userA.id);
    User user2 = userById().first(userB.id);
    User.unfollow(user1, user2);
  }

  public void followUser(User userA, User userB) {
    User user1 = userById().first(userA.id);
    User user2 = userById().first(userB.id);
    User.follow(user1, user2);
  }

  public void unfollowConversation(Uuid user, Uuid conversation) {
    userConversationTracking.get(user).remove(conversation);
  }

  public void followConversation(Uuid user, Uuid conversation) {
    // Put into hashmap the conversation and what the size of the conversation
    // is for the user at the time of following
    ConversationHeader convo = conversationById().first(conversation);
    userConversationTracking.get(user).put(conversation, convo.size);
  }

  public long uptime() {
    return Time.now().inMs() - serverStartTime.inMs();
  }

  public String version() {
    return version;
  }

  public StoreAccessor<Uuid, User> userById() {
    return userById;
  }

  public StoreAccessor<Time, User> userByTime() {
    return userByTime;
  }

  public StoreAccessor<String, User> userByText() {
    return userByText;
  }

  public void add(User user, ConversationHeader conversation) {
    user.addCreatedConversation(conversation);
    conversationById.insert(conversation.id, conversation);
    conversationByTime.insert(conversation.creation, conversation);
    conversationByText.insert(conversation.title, conversation);
    conversationPayloadById.insert(conversation.id, new ConversationPayload(conversation.id));
  }

  public StoreAccessor<Uuid, ConversationHeader> conversationById() {
    return conversationById;
  }

  public StoreAccessor<Time, ConversationHeader> conversationByTime() {
    return conversationByTime;
  }

  public StoreAccessor<String, ConversationHeader> conversationByText() {
    return conversationByText;
  }

  public StoreAccessor<Uuid, ConversationPayload> conversationPayloadById() {
    return conversationPayloadById;
  }

  public void add(Message message) {
    messageById.insert(message.id, message);
    messageByTime.insert(message.creation, message);
    messageByText.insert(message.content, message);
  }

  public StoreAccessor<Uuid, Message> messageById() {
    return messageById;
  }

  public StoreAccessor<Time, Message> messageByTime() {
    return messageByTime;
  }

  public StoreAccessor<String, Message> messageByText() {
    return messageByText;
  }
}
