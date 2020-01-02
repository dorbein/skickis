package com.company;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.Scanner;

public class Server extends Thread {
    private ServerSocket serverSocket, serverCommandSocket;
    private Socket chatServer, commandServer;

    private int maxConnections = 5;
    private int connections = 0;
    private String[] usernames = new String[maxConnections];

    private boolean[] activeConnections = new boolean[maxConnections];

    private Thread[] chat = new Thread[maxConnections], command = new Thread[maxConnections];
    private Thread receive, send;

    private DataInputStream[] in = new DataInputStream[maxConnections];
    private DataOutputStream[] out = new DataOutputStream[maxConnections];

    private DataInputStream[] commandIn = new DataInputStream[maxConnections];
    private DataOutputStream[] commandOut = new DataOutputStream[maxConnections];

    private Server(int chatPort, int commandPort) throws IOException {
        serverSocket = new ServerSocket(chatPort);
        serverSocket.setSoTimeout(60000);
        serverCommandSocket = new ServerSocket(commandPort);
    }

    public void run() {
        new Thread(() -> {
            while (connections < chat.length) {
                try {
                    System.out.println("Waiting for clients on port " + serverSocket.getLocalPort() + "/" + serverCommandSocket.getLocalPort() + "...");
                    chatServer = serverSocket.accept();
                    commandServer = serverCommandSocket.accept();

                    System.out.println("Connected to " + chatServer.getRemoteSocketAddress());

                    for (int i = 0; i < maxConnections; i++) {
                        if (!activeConnections[i]) {
                            initConnection(i);
                            chat[i].start();
//                            command[i].start();
                            activeConnections[i] = true;
                            break;
                        }
                    }

                    connections++;
                    System.out.println(connections + " connections");

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
                            for(int i = 0; i < activeConnections.length; i++){
                                System.out.println(activeConnections[i] + " " + usernames[i]);
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
                                if(id != -1 && activeConnections[id]){
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
                                if(id != -1 && activeConnections[id]){

                                    if(!words[2].isEmpty()){
                                        try {
                                            File fileToSend = new File("c:/javaSend/" + words[2]);
                                            long filesize = fileToSend.length();

                                            FileInputStream fileInputStream = new FileInputStream(fileToSend);

                                            System.out.println("Sending " + words[2] + "(" + filesize + " bytes)");

                                            commandOut[id].writeUTF(message + " " + filesize);

                                            int bytesRead, count = 0;
                                            long totalRead = 0;
                                            byte[] buffer = new byte[16*1024]; // or 4096, or more

                                            while ((bytesRead = fileInputStream.read(buffer)) > 0){
                                                commandOut[id].write(buffer, 0 , bytesRead);

                                                totalRead += bytesRead;
                                                count++;
                                                if(count >= 256){
                                                    System.out.print(totalRead + " bytes (" + totalRead * 100 / filesize + "%) uploaded\r");
                                                    count = 0;
                                                }

                                            }

                                            fileInputStream.close();

                                            System.out.print(words[2] + "(" + filesize + " bytes) sent\r to " + usernames[id]);

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
                        if (activeConnections[i]) {
                            out[i].writeUTF("[server]: " + message);
                        }
                    }
                }

            } catch (IOException e) {
                System.out.println("caught when sending message/command");
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        int chatPort = 25565;
        int commandPort = 25564;
        try {
            Thread thread = new Server(chatPort, commandPort);
            thread.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initConnection(int id) {
        //initialize each connection, each in a seperate thread
        chat[id] = new Thread(() -> {

            Socket connection = chatServer;
            Socket commandConnection = commandServer;
            String ip = null;
            String message;
            try {
                in[id] = new DataInputStream(connection.getInputStream());
                out[id] = new DataOutputStream(connection.getOutputStream());

                commandIn[id] = new DataInputStream(commandConnection.getInputStream());
                commandOut[id] = new DataOutputStream(commandConnection.getOutputStream());

                ip = connection.getRemoteSocketAddress().toString();
                usernames[id] = in[id].readUTF();

                //send a message to each client when a new one connects
                distributeMessage(usernames[id] + " has connected", null, id);
                command[id].start();


            } catch (IOException e) {
                e.printStackTrace();
                try {
                    usernames[id] = null;
                    activeConnections[id] = false;
                    connections--;
                    connection.close();
                    commandConnection.close();
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
                    e.printStackTrace();
                    System.out.println(usernames[id] + "(" + ip + ") has disconnected");
                    activeConnections[id] = false;
                    connections--;
                    try {
                        //send a message to each client when one discconnects
                        distributeMessage(usernames[id] + " has disconnected", null, id);
                        connection.close();
                        commandConnection.close();
                        return;
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        });

        command[id] = new Thread(() -> {

            String receivedCommand;

            while(true){
                try{
                    receivedCommand = commandIn[id].readUTF();
                    String text = receivedCommand;

                    System.out.println(receivedCommand);

                    text = text.trim().toLowerCase();

                    int receiverId = -1;
                    if(text.startsWith("/")) {
                        text = text.substring(1);
                        String[] words = text.split(" ");

                        switch (words[0]) {
                            case "sendfile":
                                try{
                                    if(words[1] != null){
                                        receiverId = Integer.parseInt(words[1]);
                                    }
                                }catch (NumberFormatException n){
                                    for(int i = 0; i < usernames.length;i++){
                                        if(usernames[i] != null && usernames[i].equals(words[1])){
                                            receiverId = i;
                                            break;
                                        }
                                    }
                                }

                                long filesize = Long.parseLong(words[3]);
                                String filename = words[2];
                                sendClientFile(filename, filesize, id, receiverId);

                                receive.join();
                                send.join();


                                break;
                            case "chats":
                                String users = "Connected users: ";
                                for(int i = 0; i < connections; i++){
                                    users += usernames[i];

                                    if(i == id){
                                        users += "(you)";
                                    }

                                    if(i != connections-1){
                                        users += ", ";
                                    }

                                }
                                out[id].writeUTF(users);
                                break;
                        }

                    }


                }catch (IOException | NullPointerException e){
                    e.printStackTrace();
                    try {
                        //send a message to each client when one discconnects
                        distributeMessage(usernames[id] + " has disconnected", null, id);
                        return;
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void sendClientFile(String filename, long filesize, int senderId, int receiverId){

        receive = new Thread(() -> {
            try {
                System.out.println("Sending file " + filename + "(" + filesize + " bytes) from " + senderId + " to " + receiverId);

                File receivedFile = new File("c:/javaTemp/" + filename);
                receivedFile.getParentFile().mkdirs();

                FileOutputStream fileOutputStream = new FileOutputStream(receivedFile);

                int read;
                long totalRead = 0;
                int count = 0;
                long remaining = filesize;

                byte[] buffer = new byte[16 * 1024]; // or 4096, or more

                Instant start = Instant.now();

                while ((read = commandIn[senderId].read(buffer, 0, (int) Math.min(buffer.length, remaining))) > 0) {
                    totalRead += read;
                    remaining -= read;
                    fileOutputStream.write(buffer, 0, read);
                    count++;
                    if (count >= 100) {
                        System.out.print(totalRead + " bytes (" + totalRead * 100 / filesize + "%) downloaded\r");
                        count = 0;
                    }
                }

                Instant finish = Instant.now();

                long timeElapsed = Duration.between(start, finish).toMillis();
                double timeElapsedDecimal = (double)timeElapsed/1000;
                String timeElapsedFormatted = String.format("%.1f",timeElapsedDecimal);
                String downloadSpeedFormatted = String.format("%.2f", (filesize/timeElapsedDecimal)/1000000);

                System.out.println("File " + filename
                        + " downloaded to "
                        + receivedFile.getAbsolutePath()
                        + "(" + totalRead + " bytes, "
                        + timeElapsedFormatted
                        + " seconds, "
                        + downloadSpeedFormatted + " MB/s");

                fileOutputStream.close();

                send.start();

            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        send = new Thread(() -> {
            try {
                File fileToSend = new File("c:/javaTemp/" + filename);

                FileInputStream fileInputStream = new FileInputStream(fileToSend);

                System.out.println("Sending " + filename + "(" + filesize + " bytes)");

                commandOut[receiverId].writeUTF("/sendfile " + receiverId + " " + filename + " " + filesize);

                int bytesRead, count = 0;
                long totalRead = 0;
                byte[] buffer = new byte[16*1024]; // or 4096, or more

                Instant start = Instant.now();

                while ((bytesRead = fileInputStream.read(buffer)) > 0){
                    commandOut[receiverId].write(buffer, 0 , bytesRead);

                    totalRead += bytesRead;
                    count++;
                    if(count >= 256){
                        System.out.print(totalRead + " bytes (" + totalRead * 100 / filesize + "%) uploaded\r");
                        count = 0;
                    }

                }
                Instant finish = Instant.now();

                long timeElapsed = Duration.between(start, finish).toMillis();
                double timeElapsedDecimal = (double)timeElapsed/1000;
                String timeElapsedFormatted = String.format("%.1f",timeElapsedDecimal);
                String downloadSpeedFormatted = String.format("%.2f", (filesize/timeElapsedDecimal)/1000000);

                System.out.println("File " + filename
                        + " sent to "
                        + usernames[receiverId]
                        + "(" + totalRead + " bytes, "
                        + timeElapsedFormatted
                        + " seconds, "
                        + downloadSpeedFormatted + " MB/s");

                fileInputStream.close();


            }catch (IOException e){
                e.printStackTrace();
            }
        });

        receive.start();

    }


    private void distributeMessage(String message, String user, int senderId) throws IOException {

        if (user != null) {
            for (int i = 0; i < maxConnections; i++) {
                if (senderId != i && activeConnections[i]) {
                    out[i].writeUTF(user + ": " + message);
                }
            }
        } else {
            for (int i = 0; i < maxConnections; i++) {
                if (senderId != i && activeConnections[i]) {
                    out[i].writeUTF(message);
                }
            }

        }
    }
}
//HJÄLP JAG FÖRSTÅR **INGET**