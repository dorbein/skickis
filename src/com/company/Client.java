package com.company;

import java.io.*;
import java.net.ConnectException;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.Scanner;


public class Client extends Thread{
    private Socket chatClient, fileClient = null;
    private DataInputStream in, fileIn = null;
    private DataOutputStream out = null;
    private Thread receieveMessages, receiveFiles, connect;
    private boolean running = false;
    private boolean isConnectionActive = false;
    private String username;

    private Client(){
    }

    public void run(){
        String serverName = "kimpi.ddns.net";
        int chatPort = 25565;
        int filePort = 25564;

        System.out.println("Enter username");
        Scanner input;// = new Scanner(System.in);
//        username = input.nextLine();

        username = "ok";

        connectToHost(serverName, chatPort, filePort);
        connect.start();


        receieveMessages = new Thread(() -> {
            while (running) {
                try {
                    String message = in.readUTF();
                    System.out.println(message);
                } catch (IOException e) {
                    System.out.println(e.getLocalizedMessage());
                    running = false;
                    break;
                }
            }

            try {
                chatClient.close();
            } catch (IOException e) {
                System.out.println("socket already closed?");
            }

            if(isConnectionActive){
                connectToHost(serverName, chatPort, filePort);
                connect.start();

            }
        });

        receiveFiles = new Thread(() -> {
            while(running) {
                try {
                    String filename = fileIn.readUTF();
                    int filesize = Integer.parseInt(fileIn.readUTF());

                    System.out.println("Downloading file " + filename + "(" + filesize + " bytes)");

                    File receivedFile = new File("c:/skickis/" + filename);
                    receivedFile.getParentFile().mkdirs();

//                BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(receivedFile));

                    FileOutputStream fileOutputStream = new FileOutputStream(receivedFile);

                    int read;
                    int totalRead = 0;
                    int remaining = filesize;

                    byte[] buffer = new byte[8*1024]; // or 4096, or more

                    Instant start = Instant.now();
                    while ((read = fileIn.read(buffer, 0, Math.min(buffer.length, remaining))) > 0) {
                        totalRead += read;
                        remaining -= read;
                        fileOutputStream.write(buffer, 0, read);
                    }

                    Instant finish = Instant.now();

                    long timeElapsed = Duration.between(start, finish).toMillis();
                    double timeElapsedDecimal = (double)timeElapsed/1000;

                    System.out.println("File " + filename + " downloaded to " + receivedFile.getAbsolutePath() + "(" + totalRead + " bytes, " + timeElapsedDecimal + " seconds, " + (filesize/timeElapsedDecimal)/1000000 + " MB/s");
                    fileOutputStream.close();

                } catch (IOException e) {
                    e.printStackTrace();
                    running = false;
                    break;
                }
            }
        });



        while (true) {
            try {
                input = new Scanner(System.in);
                String message = input.nextLine();
                String text = message;

                text = text.trim().toLowerCase();


                if(text.startsWith("/")){
                    text = text.substring(1);

                    switch (text){
                        case "exit":
                            System.exit(1);
                            break;
                        case "reconnect":
                            in.close();
                            connectToHost(serverName, chatPort, filePort);
                            connect.start();
                            break;
                        case "disconnect":
                            System.out.println("Disconnecting from " + chatClient.getRemoteSocketAddress());
                            in.close();
//                            chatClient.close();
//                            fileClient.close();
                            break;

                        default:
                            System.out.println("Unknown command");
                    }
                }else{
                    out.writeUTF(message);
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void connectToHost(String serverName, int chatPort, int filePort){
        connect = new Thread(() -> {
                try {
                    System.out.println("Connecting to " + serverName + " on port " + chatPort + "/" + filePort);

                    chatClient = new Socket(serverName, chatPort);
                    System.out.println("Just connected to " + chatClient.getRemoteSocketAddress());

                    fileClient = new Socket(serverName, filePort);
                    System.out.println("Just connected to " + fileClient.getRemoteSocketAddress());


                    fileIn = new DataInputStream(fileClient.getInputStream());

                    in = new DataInputStream(chatClient.getInputStream());
                    out = new DataOutputStream(chatClient.getOutputStream());

                    out.writeUTF(username);

//                    running = true;
                    receieveMessages.start();
                    receiveFiles.start();

                } catch (ConnectException s) {
                    System.out.println("Connection timed out!");
                    connect.run();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
    }

    public static void main(String[] args) {
        Thread thread = new Client();
        thread.start();
    }
}

