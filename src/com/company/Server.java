package com.company;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Scanner;

public class Server extends Thread {
    private ServerSocket serverSocket, serverFileSocket;
    private Socket chatServer, fileServer;

    private int maxConnections = 5;
    private int connections = 0;
    private String[] usernames = new String[maxConnections];

    private boolean[] activeChatConnections = new boolean[maxConnections];

    private Thread[] chat = new Thread[maxConnections];

    private DataInputStream[] in = new DataInputStream[maxConnections];
    private DataOutputStream[] out = new DataOutputStream[maxConnections];

    private DataInputStream[] fileIn = new DataInputStream[maxConnections];
    private DataOutputStream[] fileOut = new DataOutputStream[maxConnections];

    private Server(int chatPort, int filePort) throws IOException {
        serverSocket = new ServerSocket(chatPort);
        serverSocket.setSoTimeout(60000);
        serverFileSocket = new ServerSocket(filePort);
    }

    public void run() {
        new Thread(() -> {

            while (connections < chat.length) {
                try {
//                    System.out.println("Waiting for clients on port " + serverSocket.getLocalPort() + "/" + serverFileSocket.getLocalPort() + "...");
                    chatServer = serverSocket.accept();
                    fileServer = serverFileSocket.accept();

                    System.out.println("Connected to " + chatServer.getRemoteSocketAddress());

                    for (int i = 0; i < maxConnections; i++) {
                        if (!activeChatConnections[i]) {

                            initConnection(i);
                            chat[i].start();
                            activeChatConnections[i] = true;
                            break;
                        }
                    }

                    connections++;
//                    System.out.println(connections + " connections");

                } catch (SocketTimeoutException s) {
                    System.out.println("Socket timed out!");
                } catch (IOException e) {
                    System.out.println("gick inte");
                    e.printStackTrace();
                }
            }
            System.out.println("Maxium amount of clients reached");
        }).start();

        while (true) {
            try {
                Scanner mScanner = new Scanner(System.in);
                String message = mScanner.nextLine();
                String text = message;

                text = text.trim().toLowerCase();


                if(text.startsWith("/")) {
                    text = text.substring(1);
                    String[] words = text.split(" ");
                    int id = -1;
                    switch (words[0]) {
                        case "exit":
                            System.out.println("exiting");
                            System.exit(1);
                            break;
                        case "chats":
                            System.out.println("connections: " + connections);
//                            for (boolean activeConnection : activeChatConnections) System.out.println(activeConnection);
                            for(int i = 0; i < activeChatConnections.length; i++){
                                System.out.println(activeChatConnections[i] + " " + usernames[i]);
                            }
                            break;
                        case "kick":
                            try{
                                if(words[1] != null){
                                    id = Integer.parseInt(words[1]);
                                }
                            }catch (NumberFormatException n){
                                for(int i = 0; i < usernames.length;i++){
                                    if(usernames[i] != null && usernames[i].equals(words[1])){
                                        id = i;
                                        break;
                                    }
                                }
                            }finally {
                                if(id != -1 && activeChatConnections[id]){
                                    out[id].writeUTF("You have been kicked from the server");
                                    in[id].close();
                                }else{
                                    System.out.println("Cannot find user");
                                }
                            }
                            break;
                        case "sendfile":
                            try{
                                if(words[1] != null){
                                    id = Integer.parseInt(words[1]);
                                }
                            }catch (NumberFormatException n){
                                for(int i = 0; i < usernames.length;i++){
                                    if(usernames[i] != null && usernames[i].equals(words[1])){
                                        id = i;
                                        break;
                                    }
                                }
                            }finally { //do stuff if the current client id is connected
                                if(id != -1 && activeChatConnections[id]){

                                    if(!words[2].isEmpty()){
                                        try {
                                            File fileToSend = new File("c:/javaSend/" + words[2]);
                                            byte[] bytes = new byte[(int) fileToSend.length()];
//                                            BufferedInputStream bufferedInputStream = new BufferedInputStream(new FileInputStream(fileToSend));
//                                            System.out.println(bufferedInputStream.read(bytes, 0, bytes.length));

                                            FileInputStream fileInputStream = new FileInputStream(fileToSend);

                                            System.out.println("Sending " + words[2] + "(" + bytes.length + " bytes)");

                                            fileOut[id].writeUTF(fileToSend.getName());
                                            fileOut[id].writeUTF(String.valueOf(bytes.length));


                                            int bytesRead;
                                            byte[] buffer = new byte[8*1024]; // or 4096, or more

                                            while ((bytesRead = fileInputStream.read(buffer)) > 0){
                                                fileOut[id].write(buffer, 0 , bytesRead);
//                                                System.out.println("written " + bytesRead + " bytes");
                                            }

                                            fileInputStream.close();

                                            System.out.println("post loop");

                                        }catch (IOException e){
                                            e.printStackTrace();
                                        }
                                    }

                                }else{
                                    System.out.println("Cannot find user");
                                }
                            }
                            break;
                        default:
                            System.out.println("Unknown command");
                            break;
                    }
                }else{
                    for (int i = 0; i < maxConnections; i++) {
                        if (activeChatConnections[i]) {
                            out[i].writeUTF("[server]: " + message);
                        }
                    }
                }

            } catch (IOException e) {
                System.out.println("caught when sending message/command");
                System.out.println(e.getLocalizedMessage());
            }
        }
    }

    public static void main(String[] args) {
        int chatPort = 25565;
        int filePort = 25564;
        try {
            Thread thread = new Server(chatPort, filePort);
            thread.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initConnection(int id) {
        //initialize each connection, each in a seperate thread
        chat[id] = new Thread(() -> {

            Socket connection = chatServer;
            Socket fileConnection = fileServer;
            String ip = null;
//            String username = null;
            String message;
            try {
                in[id] = new DataInputStream(connection.getInputStream());
                out[id] = new DataOutputStream(connection.getOutputStream());

                fileIn[id] = new DataInputStream(fileConnection.getInputStream());
                fileOut[id] = new DataOutputStream(fileConnection.getOutputStream());

                ip = connection.getRemoteSocketAddress().toString();
                usernames[id] = in[id].readUTF();

                //send a message to each client when a new one connects
                distributeMessage(usernames[id] + " has connected", null, id);


            } catch (IOException e) {
                e.printStackTrace();
                try {
                    usernames[id] = null;
                    activeChatConnections[id] = false;
                    connections--;
                    connection.close();
                    System.out.println("closing connection");
                    return;
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }

            //set client username to its IP if no username is selected
            if (usernames[id] != null && usernames[id].isEmpty()) {
                usernames[id] = ip;
            }

            //constantly read messages from each active connection
            while (true) {
                try {
                    message = in[id].readUTF();
                    System.out.println(usernames[id] + ": " + message);
                    distributeMessage(message, usernames[id], id);

                } catch (IOException e) {
//                    e.printStackTrace();
                    System.out.println(usernames[id] + "(" + ip + ") has disconnected");
                    activeChatConnections[id] = false;
                    connections--;
                    try {
                        //send a message to each client when one discconnects

                        distributeMessage(usernames[id] + " has disconnected", null, id);

                        connection.close();
                        return;
                    } catch (IOException ex) {
//                        ex.printStackTrace();
                    }
                }
            }
        });
    }


    private void distributeMessage(String message, String user, int senderId) throws IOException {

        if (user != null) {
            for (int i = 0; i < maxConnections; i++) {
                if (senderId != i && activeChatConnections[i]) {
                    out[i].writeUTF(user + ": " + message);
                }
            }
        } else {
            for (int i = 0; i < maxConnections; i++) {
                if (senderId != i && activeChatConnections[i]) {
                    out[i].writeUTF(message);
                }
            }

        }
    }
}
