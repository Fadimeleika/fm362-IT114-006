package Project.Server;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import Project.Common.Constants;

public class Room implements AutoCloseable {
    // protected static Server server;// used to refer to accessible server
    // functions
    private String name;
    private List<ServerThread> clients = new ArrayList<ServerThread>();
    private List<String> mutedUsers = new ArrayList<>();
    private boolean isRunning = false;
    // Commands
    private final static String COMMAND_TRIGGER = "/";
   //UCID: fm362 Date:04/16/2024 
    private final static String COMMAND_MUTE = "mute";
    private final static String COMMAND_UNMUTE = "unmute";
    // private final static String CREATE_ROOM = "createroom";
    // private final static String JOIN_ROOM = "joinroom";
    // private final static String DISCONNECT = "disconnect";
    // private final static String LOGOUT = "logout";
    // private final static String LOGOFF = "logoff";
    private Logger logger = Logger.getLogger(Room.class.getName());

    public Room(String name) {
        this.name = name;
        isRunning = true;
    }

    private void info(String message) {
        logger.info(String.format("Room[%s]: %s", name, message));
    }

    public String getName() {
        return name;
    }

    protected synchronized void addClient(ServerThread client) {
        if (!isRunning) {
            return;
        }
        client.setCurrentRoom(this);
        client.sendJoinRoom(getName());// clear first
        if (clients.indexOf(client) > -1) {
            info("Attempting to add a client that already exists");
        } else {
            clients.add(client);
            // connect status second
            sendConnectionStatus(client, true);
            syncClientList(client);
        }


    }

    protected synchronized void removeClient(ServerThread client) {
        if (!isRunning) {
            return;
        }
        clients.remove(client);
        // we don't need to broadcast it to the server
        // only to our own Room
        if (clients.size() > 0) {
            // sendMessage(client, "left the room");
            sendConnectionStatus(client, false);
        }
        checkClients();
    }

    /***
     * Checks the number of clients.
     * If zero, begins the cleanup process to dispose of the room
     */
    private void checkClients() {
        // Cleanup if room is empty and not lobby
        if (!name.equalsIgnoreCase(Constants.LOBBY) && clients.size() == 0) {
            close();
        }
    }

    /***
     * Helper function to process messages to trigger different functionality.
     * 
     * @param message The original message being sent
     * @param client  The sender of the message (since they'll be the ones
     *                triggering the actions)
     */
    private boolean processCommands(String message, ServerThread client) {
        boolean wasCommand = false;
        try {
            if (message.startsWith(COMMAND_TRIGGER)) {
                String[] comm = message.split(COMMAND_TRIGGER);
                String part1 = comm[1];
                String[] comm2 = part1.split(" ");
                String command = comm2[0];
                String targetUsername = comm2[1]; // Assuming the target username follows the command
                // String roomName;
                wasCommand = true;
                switch (command) {
                    //UCID:fm362 Date:4/3/2024 
                    case "roll":
                        processRollCommand(message, client);
                        break;
                    //UCID: fm362 date:04/16/2024
                     case COMMAND_MUTE:
                        muteUser(targetUsername);
                        break;
                    case COMMAND_UNMUTE:
                        unmuteUser(targetUsername);
                        break;
                    /*
                     * case CREATE_ROOM:
                     * roomName = comm2[1];
                     * Room.createRoom(roomName, client);
                     * break;
                     * case JOIN_ROOM:
                     * roomName = comm2[1];
                     * Room.joinRoom(roomName, client);
                     * break;
                     */
                    /*
                     * case DISCONNECT:
                     * case LOGOUT:
                     * case LOGOFF:
                     * Room.disconnectClient(client, this);
                     * break;
                     */
                    default:
                        wasCommand = false;
                        break;
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return wasCommand;
    }

    private void muteUser(String username) {
        mutedUsers.add(username);
        // Broadcast message informing clients that username has been muted
        sendMessage(null, username + " has been muted.");
    }
    
    private void unmuteUser(String username) {
        mutedUsers.remove(username);
        // Broadcast message informing clients that username has been unmuted
        sendMessage(null, username + " has been unmuted.");
    }
    //UCID: fm362 date: 4/3/2024
    protected synchronized void processRollCommand(String message, ServerThread sender) {
        try {
            // Remove the "/roll " part from the message
            String rollCommand = message.replace("/roll ", "");
    
            // Check if the roll command contains "d" (indicating dice roll)
            if (rollCommand.contains("d")) {
                // Split the command by "d" to extract the number of dice and sides
                String[] parts = rollCommand.split("d");
                if (parts.length != 2) {
                    sender.sendMessage(Constants.DEFAULT_CLIENT_ID, "Invalid roll command format. Usage: /roll #d#");
                    return;
                }
                
                int numberOfDice = Integer.parseInt(parts[0]);
                int numberOfSides = Integer.parseInt(parts[1]);
                //UCID: fm362 date:4/3/2024
                // Roll the dice
                int total = 0;
                StringBuilder rollResultMessage = new StringBuilder(sender.getClientName() + " rolled ");
                for (int i = 0; i < numberOfDice; i++) {
                    int roll = (int) (Math.random() * numberOfSides) + 1;
                    total += roll;
                    if (i > 0) {
                        rollResultMessage.append(", ");
                    }
                    rollResultMessage.append(roll);
                }
                rollResultMessage.append(" (total: ").append(total).append(")");
    
                // Broadcast the roll result to all clients in the room
                sendMessage(sender, rollResultMessage.toString());
            } else {
                // If the command doesn't contain "d", treat it as a single dice roll
                int max = Integer.parseInt(rollCommand);
                int result = (int) (Math.random() * max) + 1;
                String rollResultMessage = String.format("%s rolled %d (1-%d)", sender.getClientName(), result, max);
                sendMessage(sender, rollResultMessage);
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(Constants.DEFAULT_CLIENT_ID, "Invalid roll command format. Usage: /roll # or /roll #d#");
        }
    }
    //UCID: fm362 date:4/3/2024
    protected synchronized void processFlipCommand(ServerThread sender) {
        // a random number (0 or 1) to represent heads or tails
        int result = (int) (Math.random() * 2);
    
        // Determine the flip result
        String flipResultMessage;
        if (result == 0) {
            flipResultMessage = sender.getClientName() + " flipped heads";
        } else {
            flipResultMessage = sender.getClientName() + " flipped tails";
        }
    
        // Broadcast the flip result to all clients in the room
        sendMessage(sender, flipResultMessage);
    }
    //UCID:fm362 Date:04/16/2024
    protected synchronized void processMuteCommand(String message, ServerThread sender) {
        // take the username from the message
        String[] parts = message.split(" ");
        if (parts.length < 2) {
            // If the message format is incorrect, send an error message back to the sender
            sender.sendMessage(Constants.DEFAULT_CLIENT_ID, "Usage: /mute username");
            return;
        }
        @SuppressWarnings("unused")
        String targetUsername = parts[1];
        // Logic to mute the target user...
    }
    
    protected synchronized void processUnmuteCommand(String message, ServerThread sender) {
        // take the username from the message
        String[] parts = message.split(" ");
        if (parts.length < 2) {
            // If the message format is incorrect, send an error message back to the sender
            sender.sendMessage(Constants.DEFAULT_CLIENT_ID, "Usage: /unmute username");
            return;
        }
        @SuppressWarnings("unused")
        String targetUsername = parts[1];
        // Logic to unmute the target user...
    }
    

    // Command helper methods
    private synchronized void syncClientList(ServerThread joiner) {
        Iterator<ServerThread> iter = clients.iterator();
        while (iter.hasNext()) {
            ServerThread st = iter.next();
            if (st.getClientId() != joiner.getClientId()) {
                joiner.sendClientMapping(st.getClientId(), st.getClientName());
            }
        }
    }
    protected static void createRoom(String roomName, ServerThread client) {
        if (Server.INSTANCE.createNewRoom(roomName)) {
            // server.joinRoom(roomName, client);
            Room.joinRoom(roomName, client);
        } else {
            client.sendMessage(Constants.DEFAULT_CLIENT_ID, String.format("Room %s already exists", roomName));
        }
    }

    protected static void joinRoom(String roomName, ServerThread client) {
        if (!Server.INSTANCE.joinRoom(roomName, client)) {
            client.sendMessage(Constants.DEFAULT_CLIENT_ID, String.format("Room %s doesn't exist", roomName));
        }
    }

    protected static List<String> listRooms(String searchString, int limit) {
        return Server.INSTANCE.listRooms(searchString, limit);
    }

    protected static void disconnectClient(ServerThread client, Room room) {
        client.setCurrentRoom(null);
        client.disconnect();
        room.removeClient(client);
    }
    // end command helper methods

    /***
     * Takes a sender and a message and broadcasts the message to all clients in
     * this room. Client is mostly passed for command purposes but we can also use
     * it to extract other client info.
     * 
     * @param sender  The client sending the message
     * @param message The message to broadcast inside the room
     */
    protected synchronized void sendMessage(ServerThread sender, String message) {
        if (!isRunning) {
            return;
        }
        info("Sending message to " + clients.size() + " clients");
        if (sender != null && processCommands(message, sender)) {
            // it was a command, don't broadcast
            return;
        }

        /// String from = (sender == null ? "Room" : sender.getClientName());
        long from = (sender == null) ? Constants.DEFAULT_CLIENT_ID : sender.getClientId();
        Iterator<ServerThread> iter = clients.iterator();
        while (iter.hasNext()) {
            ServerThread client = iter.next();
            boolean messageSent = client.sendMessage(from, message);
            if (!messageSent) {
                handleDisconnect(iter, client);
            }
        }
    }

    protected synchronized void sendConnectionStatus(ServerThread sender, boolean isConnected) {
        Iterator<ServerThread> iter = clients.iterator();
        while (iter.hasNext()) {
            ServerThread client = iter.next();
            boolean messageSent = client.sendConnectionStatus(sender.getClientId(), sender.getClientName(),
                    isConnected);
            if (!messageSent) {
                handleDisconnect(iter, client);
            }
        }
    }
    //UCID: fm362 date:04/16/2024
    protected synchronized void sendPrivateMessage(ServerThread sender, String recipientUsername, String message) {
        // Iterate through the clients in the room to find the recipient by their username
        for (ServerThread client : clients) {
            if (client.getClientName().equals(recipientUsername)) {
                // Send the private message to the specfic person
                client.sendMessage(sender.getClientId(), message);
                return;
            }
        }
        // If the person is not found, or not in the room, send an error message back to the sender
        sender.sendMessage(Constants.DEFAULT_CLIENT_ID, "Recipient not found or not in the room.");
    }
    
    private void handleDisconnect(Iterator<ServerThread> iter, ServerThread client) {
        iter.remove();
        info("Removed client " + client.getClientName());
        checkClients();
        sendMessage(null, client.getClientName() + " disconnected");
    }

    public void close() {
        Server.INSTANCE.removeRoom(this);
        // server = null;
        isRunning = false;
        clients = null;
    }
    protected synchronized void processTextCommand(String message) {
        String formattedMessage = message;
        if (message.contains("**")) {
            formattedMessage = processBold(formattedMessage);
        }
        if (message.contains("*")) {
            formattedMessage = processItalic(formattedMessage);
        }
        if (message.contains("<color=")) {
            formattedMessage = processColor(formattedMessage);
        }
        if (message.contains("__")) {
            formattedMessage = processUnderline(formattedMessage);
        }
        broadcastFormattedMessage(formattedMessage);
    }

    //UCID:fm362 date:4/3/2024
    private String processBold(String message) {
        // Implement bold processing here
        return message.replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>");
    }

    //UCID:fm362 date:4/3/2024
    private String processItalic(String message) {
        // Implement italic processing here
        return message.replaceAll("\\*(.*?)\\*", "<i>$1</i>");
    }

    //UCID:fm362 date:4/3/2024
    private String processColor(String message) {
        String formattedMessage = message;
        formattedMessage = formattedMessage.replaceAll("<color=red>(.*?)</color>", "<font color=\"red\">$1</font>");
        formattedMessage = formattedMessage.replaceAll("<color=green>(.*?)</color>", "<font color=\"green\">$1</font>");
        formattedMessage = formattedMessage.replaceAll("<color=blue>(.*?)</color>", "<font color=\"blue\">$1</font>");
        return formattedMessage;
    }
   

    //UCID:fm362 date:4/3/2024
    private String processUnderline(String message) {
        // Implement underline processing here
        return message.replaceAll("__", "<u>").replaceAll("__", "</u>");
    }


    private void broadcastFormattedMessage(String formattedMessage) {
        sendMessage(null, formattedMessage);
    }
    
    
    
    protected synchronized void broadcastCommandResult(String result) {
        if (!isRunning) {
            return;
        }
        Iterator<ServerThread> iter = clients.iterator();
        while (iter.hasNext()) {
            ServerThread client = iter.next();
            boolean messageSent = client.sendMessage(Constants.DEFAULT_CLIENT_ID, result); // Adjusted here
            if (!messageSent) {
                handleDisconnect(iter, client);
            }
        }
    } 
}

