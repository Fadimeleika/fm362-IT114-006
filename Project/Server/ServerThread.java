package Project.Server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;
import java.util.logging.Logger;

import Project.Common.ConnectionPayload;
import Project.Common.Constants;
import Project.Common.Payload;
import Project.Common.PayloadType;
import Project.Common.RoomResultsPayload;
import Project.Common.TextFX;
import Project.Common.TextFX.Color;

/**
 * A server-side representation of a single client
 */
public class ServerThread extends Thread {
    private Socket client;
    private String clientName;
    private boolean isRunning = false;
    private long clientId = Constants.DEFAULT_CLIENT_ID;
    private ObjectOutputStream out;// exposed here for send()
    // private Server server;// ref to our server so we can call methods on it
    // more easily
    private Room currentRoom;
    private Logger logger = Logger.getLogger(ServerThread.class.getName());

    private void info(String message) {
        logger.info(String.format("Thread[%s]: %s", getClientName(), message));
    }

    public ServerThread(Socket myClient/* , Room room */) {
        info("Thread created");
        // get communication channels to single client
        this.client = myClient;
        // this.currentRoom = room;

    }

    protected void setClientId(long id) {
        clientId = id;
        if (id == Constants.DEFAULT_CLIENT_ID) {
            logger.info(TextFX.colorize("Client id reset", Color.WHITE));
        }
        sendClientId(id);
    }

    protected boolean isRunning() {
        return isRunning;
    }
    protected void setClientName(String name) {
        if (name == null || name.isBlank()) {
            logger.severe("Invalid client name being set");
            return;
        }
        clientName = name;
    }

    protected String getClientName() {
        return clientName;
    }

    protected synchronized Room getCurrentRoom() {
        return currentRoom;
    }

    protected synchronized void setCurrentRoom(Room room) {
        if (room != null) {
            currentRoom = room;
        } else {
            info("Passed in room was null, this shouldn't happen");
        }
    }

    public void disconnect() {
        info("Thread being disconnected by server");
        isRunning = false;
        cleanup();
    }

    // send methods
    protected boolean sendClientMapping(long id, String name) {
        ConnectionPayload cp = new ConnectionPayload();
        cp.setPayloadType(PayloadType.SYNC_CLIENT);
        cp.setClientId(id);
        cp.setClientName(name);
        return send(cp);
    }

    protected boolean sendJoinRoom(String roomName) {
        Payload p = new Payload();
        p.setPayloadType(PayloadType.JOIN_ROOM);
        p.setMessage(roomName);
        return send(p);
    }

    protected boolean sendClientId(long id) {
        ConnectionPayload cp = new ConnectionPayload();
        cp.setClientId(id);
        cp.setClientName(clientName);
        return send(cp);
    }
    private boolean sendListRooms(List<String> potentialRooms) {
        RoomResultsPayload rp = new RoomResultsPayload();
        rp.setRooms(potentialRooms);
        if (potentialRooms == null) {
            rp.setMessage("Invalid limit, please choose a value between 1-100");
        } else if (potentialRooms.size() == 0) {
            rp.setMessage("No rooms found matching your search criteria");
        }
        return send(rp);
    }
       // UCID: Fm362 Date: 4/3/2024
    public boolean sendMessage(long from, String message) {
        Payload payload = new Payload();
        payload.setPayloadType(PayloadType.MESSAGE);
        // p.setClientName(from);
        payload.setClientId(from);
        payload.setMessage(message);
        return send(payload);
    }

    /**
     * Used to associate client names and their ids from the server perspective
     * 
     * @param whoId       id of who is connecting/disconnecting
     * @param whoName     name of who is connecting/disconnecting
     * @param isConnected status of connection (true connecting, false,
     *                    disconnecting)
     * @return
     */
    public boolean sendConnectionStatus(long whoId, String whoName, boolean isConnected) {
        ConnectionPayload payload = new ConnectionPayload(isConnected);
        // p.setClientName(who);
        payload.setClientId(whoId);
        payload.setClientName(whoName);
        payload.setMessage(isConnected ? "connected" : "disconnected");
        return send(payload);
    }

    private boolean send(Payload payload) {
        // added a boolean so we can see if the send was successful
        try {
            out.writeObject(payload);
            return true;
        } catch (IOException e) {
            info("Error sending message to client (most likely disconnected)");
            // comment this out to inspect the stack trace
            // e.printStackTrace();
            cleanup();
            return false;
        } catch (NullPointerException ne) {
            info("Message was attempted to be sent before outbound stream was opened");
            return true;// true since it's likely pending being opened
        }
    }

    // end send methods
    @Override
    public void run() {
        info("Thread starting");
        try (ObjectOutputStream out = new ObjectOutputStream(client.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(client.getInputStream());) {
            this.out = out;
            isRunning = true;
            Payload fromClient;
            while (isRunning && // flag to let us easily control the loop
                    (fromClient = (Payload) in.readObject()) != null // reads an object from inputStream (null would
                                                                     // likely mean a disconnect)
            ) {

                info("Received from client: " + fromClient);
                processPayload(fromClient);

            } // close while loop
        } catch (Exception e) {
            // happens when client disconnects
            e.printStackTrace();
            info("Client disconnected");
        } finally {
            isRunning = false;
            info("Exited thread loop. Cleaning up connection");
            cleanup();
        }
    }

    /**
     * Used to process payloads from the client and handle their data
     * 
     * @param p
     */
   
   
     private void processPayload(Payload p) {
        switch (p.getPayloadType()) {
            case CONNECT:
                try {
                    ConnectionPayload cp = (ConnectionPayload) p;
                    setClientName(cp.getClientName());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            case DISCONNECT:
                if (currentRoom != null) {
                    Room.disconnectClient(this, currentRoom);
                }
                break;
                //UCID: fm362 Date: 4/3/2024
            case MESSAGE:
                String message = p.getMessage();
                if (message.startsWith("/roll")) {
                    if (currentRoom != null) {
                        currentRoom.processRollCommand(message, this);
                    } else {
                        Room.joinRoom(Constants.LOBBY, this);
                        
                    }
                } else if (message.startsWith("/flip")) {
                    if (currentRoom != null) {
                        currentRoom.processFlipCommand(this);
                    } else {
                        Room.joinRoom(Constants.LOBBY, this);
                        
                    }
                //UCID: fm362 Date:04/16/2024
                } else if (message.startsWith("@")) { // Private message handling
                    String[] parts = message.split(" ", 2); // Split the message into username and actual message
                    if (parts.length == 2) {
                        String username = parts[0].substring(1); // Remove the "@" symbol
                        String privateMessage = parts[1];
                        if (currentRoom != null) {
                            currentRoom.sendPrivateMessage(this, username, privateMessage);
                        } else {
                            Room.joinRoom(Constants.LOBBY, this);
                        }
                    } else {
                        sendMessage(Constants.DEFAULT_CLIENT_ID, "Invalid private message format.");
                    }
                } else {
                    if (currentRoom != null) {
                        currentRoom.sendMessage(this, p.getMessage());
                    } else {
                        Room.joinRoom(Constants.LOBBY, this);
                        
                    }
                }
                break;
            case CREATE_ROOM:
                Room.createRoom(p.getMessage(), this);
                break;
            case JOIN_ROOM:
                Room.joinRoom(p.getMessage(), this);
                break;
            case LIST_ROOMS:
                String searchString = p.getMessage() == null ? "" : p.getMessage();
                int limit = 10;
                try {
                    RoomResultsPayload rp = ((RoomResultsPayload) p);
                    limit = rp.getLimit();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                List<String> potentialRooms = Room.listRooms(searchString, limit);
                this.sendListRooms(potentialRooms);
                break; 
            default:
                break;
        }
    }

    private void cleanup() {
        info("Thread cleanup() start");
        try {
            client.close();
        } catch (IOException e) {
            info("Client already closed");
        }
        info("Thread cleanup() complete");
    }

    public long getClientId() {
        return clientId;
    }
}