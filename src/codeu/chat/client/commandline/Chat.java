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

package codeu.chat.client.commandline;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import codeu.chat.client.core.Context;
import codeu.chat.client.core.ConversationContext;
import codeu.chat.client.core.MessageContext;
import codeu.chat.client.core.UserContext;

import codeu.chat.common.User;
import codeu.chat.util.Uuid;


import codeu.chat.util.Tokenizer;
import codeu.chat.util.Time;
import codeu.chat.util.Uuid;

import codeu.chat.common.User;

import codeu.chat.common.ConversationHeader;

public final class Chat {

  // PANELS
  //
  // We are going to use a stack of panels to track where in the application
  // we are. The command will always be routed to the panel at the top of the
  // stack. When a command wants to go to another panel, it will add a new
  // panel to the top of the stack. When a command wants to go to the previous
  // panel all it needs to do is pop the top panel.
  private final Stack<Panel> panels = new Stack<>();
  private final Context context;

  public Chat(Context context) {
    this.panels.push(createRootPanel(context));
    this.context = context;
  }

  // HANDLE COMMAND
  //
  // Take a single line of input and parse a command from it. If the system
  // is willing to take another command, the function will return true. If
  // the system wants to exit, the function will return false.
  //
  public boolean handleCommand(String line) {
    final Tokenizer tokens;
    final String command;

    try {
      tokens = new Tokenizer(line.trim());
      command = tokens.hasNext() ? tokens.next() : "";
    }
    catch (IllegalArgumentException e) {
      // Catch any misformatting or unclear input here
      // and still continue processing future commands
      System.out.println("Misformatted Input: " + e.getMessage());
      return true;
    }

    // Because "exit", "back", and "version" are applicable to every panel, handle
    // those commands here to avoid having to implement them for each
    // panel.
    if ("exit".equals(command)) {
      // The user does not want to process any more commands
      return false;
    }

    // Do not allow the root panel to be removed.
    if ("back".equals(command) && panels.size() > 1) {
      panels.pop();
      return true;
    }

    // Returns the version of the server
    if ("version".equals(command)) {
      System.out.println("This is server version # " + context.Version());
      return true;
    }

    // Returns how long the server has been running
    if ("uptime".equals(command)) {
      System.out.println("Server has been running for " + Time.formatTimeString(context.Uptime()));
      return true;
    }

    if (panels.peek().handleCommand(command, tokens)) {
      // the command was handled
      return true;
    }


    // If we get to here it means that the command was not correctly handled
    // so we should let the user know. Still return true as we want to continue
    // processing future commands.
    System.out.println("ERROR: Unsupported command");
    return true;
  }

  // CREATE ROOT PANEL
  //
  // Create a panel for the root of the application. Root in this context means
  // the first panel and the only panel that should always be at the bottom of
  // the panels stack.
  //
  // The root panel is for commands that require no specific contextual information.
  // This is before a user has signed in. Most commands handled by the root panel
  // will be user selection focused.
  //
  private Panel createRootPanel(final Context context) {

    final Panel panel = new Panel();

    // HELP
    //
    // Add a command to print a list of all commands and their description when
    // the user for "help" while on the root panel.
    //
    panel.register("help", new Panel.Command() {
      @Override
      public void invoke(Tokenizer args) {
        System.out.println("ROOT MODE");
        System.out.println("  u-list");
        System.out.println("    List all users.");
        System.out.println("  u-add <name>");
        System.out.println("    Add a new user with the given name.");
        System.out.println("  u-sign-in <name>");
        System.out.println("    Sign in as the user with the given name.");
        System.out.println("  uptime");
        System.out.println("    Display how long server has been running.");
        System.out.println("  version");
        System.out.println("    Display server version number.");
        System.out.println("  exit");
        System.out.println("    Exit the program.");
      }
    });

    // U-LIST (user list)
    //
    // Add a command to print all users registered on the server when the user
    // enters "u-list" while on the root panel.
    //
    panel.register("u-list", new Panel.Command() {
      @Override
      public void invoke(Tokenizer args) {
        for (final UserContext user : context.allUsers()) {
          System.out.format(
              "USER %s (UUID:%s)\n",
              user.user.name,
              user.user.id);
        }
      }
    });

    // U-ADD (add user)
    //
    // Add a command to add and sign-in as a new user when the user enters
    // "u-add" while on the root panel.
    //
    panel.register("u-add", new Panel.Command() {
      @Override
      public void invoke(Tokenizer args) {
        final String name = args.hasNext() ? args.next().trim() : "";
        if (args.hasNext()) {
          System.out.println("ERROR: Too many arguments for command");
        }
        else if (name.length() > 0) {
          if (context.create(name) == null) {
            System.out.println("ERROR: Failed to create new user");
          }
        } else {
          System.out.println("ERROR: Missing <username>");
        }
      }
    });

    // U-SIGN-IN (sign in user)
    //
    // Add a command to sign-in as a user when the user enters "u-sign-in"
    // while on the root panel.
    //
    panel.register("u-sign-in", new Panel.Command() {
      @Override
      public void invoke(Tokenizer args) {
        final String name = args.hasNext() ? args.next().trim() : "";
        if (args.hasNext()) {
          System.out.println("ERROR: Too many arguments for command");
        }
        else if (name.length() > 0) {
          final UserContext user = findUser(name);
          if (user == null) {
            System.out.format("ERROR: Failed to sign in as '%s'\n", name);
          } else {
            panels.push(createUserPanel(user));
          }
        } else {
          System.out.println("ERROR: Missing <username>");
        }
      }

      // Find the first user with the given name and return a user context
      // for that user. If no user is found, the function will return null.
      private UserContext findUser(String name) {
        for (final UserContext user : context.allUsers()) {
          if (user.user.name.equals(name)) {
            return user;
          }
        }
        return null;
      }
    });

    // Now that the panel has all its commands registered, return the panel
    // so that it can be used.
    return panel;
  }

  private Panel createUserPanel(final UserContext user) {

    final Panel panel = new Panel();

    // HELP
    //
    // Add a command that will print a list of all commands and their
    // descriptions when the user enters "help" while on the user panel.
    //
    panel.register("help", new Panel.Command() {
      @Override
      public void invoke(Tokenizer args) {
        System.out.println("USER MODE");
        System.out.println("  c-list");
        System.out.println("    List all conversations that the current user can interact with.");
        System.out.println("  c-add <title>");
        System.out.println("    Add a new conversation with the given title and join it as the current user.");
        System.out.println("  c-join <title>");
        System.out.println("    Join the conversation as the current user.");
        System.out.println("  u-follow <name>");
        System.out.println("    Follow a user to get updates on new user activity.");
        System.out.println("  u-unfollow <name>");
        System.out.println("    Unfollow a user to stop updates from user activity.");
        System.out.println("  c-follow <title>");
        System.out.println("    Follow a conversation to get updates on new messages.");
        System.out.println("  c-unfollow <title>");
        System.out.println("    Unfollow a conversation to stop updates from conversation.");
        System.out.println("  status-update");
        System.out.println("    Retrieve updates on users and conversations current user is following.");
        System.out.println("  info");
        System.out.println("    Display all info for the current user.");
        System.out.println("  uptime");
        System.out.println("    Display how long server has been running.");
        System.out.println("  version");
        System.out.println("    Display server version number.");
        System.out.println("  back");
        System.out.println("    Go back to ROOT MODE.");
        System.out.println("  exit");
        System.out.println("    Exit the program.");
      }
    });

    // C-LIST (list conversations)
    //
    // Add a command that will print all conversations when the user enters
    // "c-list" while on the user panel.
    //
    panel.register("c-list", new Panel.Command() {
      @Override
      public void invoke(Tokenizer args) {
        for (final ConversationContext conversation : user.conversations()) {
          System.out.format(
              "CONVERSATION %s (UUID:%s)\n",
              conversation.conversation.title,
              conversation.conversation.id);
        }
      }
    });

    // C-ADD (add conversation)
    //
    // Add a command that will create and join a new conversation when the user
    // enters "c-add" while on the user panel.
    //
    panel.register("c-add", new Panel.Command() {
      @Override
      public void invoke(Tokenizer args) {
        final String name = args.hasNext() ? args.next().trim() : "";
        if (args.hasNext()) {
          System.out.println("ERROR: Too many arguments for command");
        }
        else if (name.length() > 0) {
          final ConversationContext conversation = user.start(name);
          if (conversation == null) {
            System.out.println("ERROR: Failed to create new conversation");
          } else {
            panels.push(createConversationPanel(conversation));
          }
        } else {
          System.out.println("ERROR: Missing <title>");
        }
      }
    });

    // STATUS-UPDATE (status update)
    //
    // Add a command that will give users information on items they are following.
    //
    panel.register("status-update", new Panel.Command() {
      @Override
      public void invoke(Tokenizer args) {
        System.out.println("Status Updates!");
        System.out.print(user.statusUpdate());
      }
    });

    // U-FOLLOW (follow user)
    //
    // Add a command that will follow user activity when the user enters
    // "u-follow" while on the user panel.
    //
    panel.register("u-follow", new Panel.Command() {
      @Override
      public void invoke(Tokenizer args) {
        final String name = args.hasNext() ? args.next().trim() : "";
        if (args.hasNext()) {
          System.out.println("ERROR: Too many arguments for command");
          return;
        }
        else if (name.length() < 0) {
          System.out.println("ERROR: Missing <username>");
          return;
        }
        else if (findUser(name) == null) {
          System.out.format("ERROR: Failed to follow '%s'\n", name);
          return;
        }

        final User userToBeFollowed = findUser(name);
        user.followUser(userToBeFollowed);
      }
      // TODO: add this function to user context to avoid its duplication

      // Find the first user with the given name and return a user context
      // for that user. If no user is found, the function will return null.
      private User findUser(String name) {
        for (final UserContext context : context.allUsers()) {
          if (context.user.name.equals(name)) {
            return context.user;
          }
        }
        return null;
      }
    });

    // U-UNFOLLOW (follow user)
    //
    // Add a command that will unfollow user activity when the user enters
    // "u-unfollow" while on the user panel.
    //
    panel.register("u-unfollow", new Panel.Command() {
      @Override
      public void invoke(Tokenizer args) {
        final String name = args.hasNext() ? args.next().trim() : "";
        if (args.hasNext()) {
          System.out.println("ERROR: Too many arguments for command");
          return;
        }
        else if (name.length() < 0) {
          System.out.println("ERROR: Missing <username>");
          return;
        }
        else if (findUser(name) == null) {
          System.out.format("ERROR: Failed to follow '%s'\n", name);
          return;
        }

        final User userToBeUnfollowed = findUser(name);
        user.followUser(userToBeUnfollowed);
      }

      // Find the first user with the given name and return a user context
      // for that user. If no user is found, the function will return null.
      private User findUser(String name) {
        // TODO: fix the round trip method for an efficient lookup method
        for (final UserContext context : context.allUsers()) {
          if (context.user.name.equals(name)) {
            return context.user;
          }
        }
        return null;
      }
    });

    // C-UNFOLLOW (unfollow conversation)
    //
    // Add a command that will unfollow a conversation when the user enters
    // "c-unfollow" while on the user panel.
    //
    panel.register("c-unfollow", new Panel.Command() {
      @Override
      public void invoke(Tokenizer args) {
        final String name = args.hasNext() ? args.next().trim() : "";
        if (args.hasNext()) {
          System.out.println("ERROR: Too many arguments for command");
          return;
        }
        else if (name.length() < 0) {
          System.out.println("ERROR: Missing <title>");
          return;
        }
        else if (find(name) == null) {
          System.out.format("ERROR: No conversation with name '%s'\n", name);
          return;
        }

        final Uuid conversationID = find(name);
        user.unfollowConversation(conversationID);
      }

      // Find the first conversation with the given name and return its context.
      // If no conversation has the given name, this will return null.
      private Uuid find(String title) {
        for (final ConversationContext context : user.conversations()) {
          if (title.equals(context.conversation.title)) {
            return context.conversation.id;
          }
        }
        return null;
      }
    });

    // C-FOLLOW (follow conversation)
    //
    // Add a command that will follow a conversation when the user enters
    // "c-follow" while on the user panel.
    //
    panel.register("c-follow", new Panel.Command() {
      // TODO: add function to conversation to remove needed paramenter
      @Override
      public void invoke(Tokenizer args) {
        final String name = args.hasNext() ? args.next().trim() : "";
        if (args.hasNext()) {
          System.out.println("ERROR: Too many arguments for command");
          return;
        }
        else if (name.length() < 0) {
          System.out.println("ERROR: Missing <title>");
          return;
        }
        else if (find(name) == null) {
          System.out.format("ERROR: No conversation with name '%s'\n", name);
          return;
        }

        final Uuid conversationID = find(name);
        user.followConversation(conversationID);
      }

      // Find the first conversation with the given name and return its context.
      // If no conversation has the given name, this will return null.
      private Uuid find(String title) {
        for (final ConversationContext context : user.conversations()) {
          if (title.equals(context.conversation.title)) {
            return context.conversation.id;
          }
        }
        return null;
      }
    });
      
      // Find the first conversation with the given name and return its context.
      // If no conversation has the given name, this will return null.
      private Uuid find(String title) {
        for (final ConversationContext context : user.conversations()) {
          if (title.equals(context.conversation.title)) {
            return context.conversation.id;
          }
        }
        return null;
      }
    });

    // C-JOIN (join conversation)
    //
    // Add a command that will joing a conversation when the user enters
    // "c-join" while on the user panel.
    //
    panel.register("c-join", new Panel.Command() {
      @Override
      public void invoke(Tokenizer args) {
        final String name = args.hasNext() ? args.next().trim() : "";
        if (args.hasNext()) {
          System.out.println("ERROR: Too many arguments for command");
        }
        else if (name.length() > 0) {
          final ConversationContext conversation = find(name);
          if (conversation == null) {
            System.out.format("ERROR: No conversation with name '%s'\n", name);
          } else {
            panels.push(createConversationPanel(conversation));
          }
        } else {
          System.out.println("ERROR: Missing <title>");
        }
      }

      // Find the first conversation with the given name and return its context.
      // If no conversation has the given name, this will return null.
      private ConversationContext find(String title) {
        for (final ConversationContext conversation : user.conversations()) {
          if (title.equals(conversation.conversation.title)) {
            return conversation;
          }
        }
        return null;
      }
    });

    // INFO
    //
    // Add a command that will print info about the current context when the
    // user enters "info" while on the user panel.
    //
    panel.register("info", new Panel.Command() {
      @Override
      public void invoke(Tokenizer args) {
        System.out.println("User Info:");
        System.out.format("  Name : %s\n", user.user.name);
        System.out.format("  Id   : UUID:%s\n", user.user.id);
      }
    });

    // Now that the panel has all its commands registered, return the panel
    // so that it can be used.
    return panel;
  }

  private Panel createConversationPanel(final ConversationContext conversation) {

    final Panel panel = new Panel();

    // HELP
    //
    // Add a command that will print all the commands and their descriptions
    // when the user enters "help" while on the conversation panel.
    //
    panel.register("help", new Panel.Command() {
      @Override
      public void invoke(Tokenizer args) {
        System.out.println("USER MODE");
        System.out.println("  m-list");
        System.out.println("    List all messages in the current conversation.");
        System.out.println("  m-add <message>");
        System.out.println("    Add a new message to the current conversation as the current user.");
        System.out.println("  toggle-permission <user> <permission> ...");
        System.out.println("    Toggle the permissions (member/admin/owner) of other users.");
        System.out.println("  info");
        System.out.println("    Display all info about the current conversation.");
        System.out.println("  uptime");
        System.out.println("    Display how long server has been running.");
        System.out.println("  version");
        System.out.println("    Display server version number.");
        System.out.println("  back");
        System.out.println("    Go back to USER MODE.");
        System.out.println("  exit");
        System.out.println("    Exit the program.");
      }
    });

    // M-LIST (list messages)
    //
    // Add a command to print all messages in the current conversation when the
    // user enters "m-list" while on the conversation panel.
    //
    panel.register("m-list", new Panel.Command() {
      @Override
      public void invoke(Tokenizer args) {
        System.out.println("--- start of conversation ---");

        try {
          for (MessageContext message = conversation.firstMessage();
                              message != null;
                              message = message.next()) {
            System.out.println();
            System.out.format("USER : %s\n", message.message.author);
            System.out.format("SENT : %s\n", message.message.creation);
            System.out.println();
            System.out.println(message.message.content);
            System.out.println();
          }
        }
        catch (Exception e) {
          System.out.println("You do not have permission to view this content");
        }
          System.out.println("---  end of conversation  ---");
        }
    });

    // M-ADD (add message)
    //
    // Add a command to add a new message to the current conversation when the
    // user enters "m-add" while on the conversation panel.
    //
    panel.register("m-add", new Panel.Command() {
      @Override
      public void invoke(Tokenizer args) {
        final String message = args.hasNext() ? args.next().trim() : "";
        if (args.hasNext()) {
          System.out.println("ERROR: Too many arguments for command");
        }
        else if (message.length() > 0) {
          try {
            conversation.add(message);
          }
          catch (Exception ex) {
            System.out.println("You do not have permission to add new messages");
          }
        } else {
          System.out.println("ERROR: Messages must contain text");
        }
      }
    });

    // INFO
    //
    // Add a command to print info about the current conversation when the user
    // enters "info" while on the conversation panel.
    //
    panel.register("info", new Panel.Command() {
      @Override
      public void invoke(Tokenizer args) {
        System.out.println("Conversation Info:");
        System.out.format("  Title : %s\n", conversation.conversation.title);
        System.out.format("  Id    : UUID:%s\n", conversation.conversation.id);
        System.out.format("  Owner : %s\n", conversation.conversation.owner);
      }
    });

    // TOGGLE-PERMISSION (user's permission to be changed) (permission)
    //
    // Add a command to allow authorized users to toggle permissions of other users
    //
    panel.register("toggle-permission", new Panel.Command() {
      @Override
      public void invoke(Tokenizer args) {
        final String name = args.hasNext() ? args.next().trim() : "";

        int permission = 0;

        while (args.hasNext()) {
          final String permissionString = args.hasNext() ? args.next().trim().toLowerCase() : "";
          switch (permissionString) {
            case "admin":  permission = permission ^ ConversationHeader.ADMIN_PERM;
                           break;
            case "owner":  permission = permission ^ ConversationHeader.OWNER_PERM;
                           break;
            case "member": permission = permission ^ ConversationHeader.MEMBER_PERM;
                           break;
            default:       System.out.println("ERROR: input is not a valid condition"); return;
          }
        }
        if (conversation.conversation.isMember(conversation.user.id)) {
          System.out.println("You do not have permission to change permissions.");
          return;
        }
        if (name.length() < 0) {
          System.out.println("ERROR: Missing argument");
          return;
        }
        else if (findUser(name) == null) {
          System.out.format("ERROR: Failed to find user '%s'\n", name);
          return;
        }

        final User permissionChangedUser = findUser(name);
        conversation.togglePermission(permissionChangedUser.id, permission, conversation.conversation.id);
      }

      private User findUser(String name) {
        for (final UserContext context : context.allUsers()) {
          if (context.user.name.equals(name)) {
            return context.user;
          }
        }
        return null;
      }
    });

    // Now that the panel has all its commands registered, return the panel
    // so that it can be used.
    return panel;
  }
}
