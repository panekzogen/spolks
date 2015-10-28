package com.company;

import java.io.*;
import java.net.*;

public class Receiver {
    static InetAddress prevClient = null;
    static long transLength = 0;
    static char operation = ' ';
    static String file = "";

    DataInputStream inStream;
    DataOutputStream outStream;
    Socket client;

    String[] command;

    Receiver(Socket sock){
        client = sock;
        try {
            inStream = new DataInputStream(sock.getInputStream());
            outStream = new DataOutputStream(sock.getOutputStream());
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        if(!sock.getInetAddress().equals(prevClient))
            transLength = 0;
    }
    int receive(){
        try {
            command = readCommand().split(" ", 2);
        } catch (IOException e) {
            System.out.println(e.getMessage());
            return 0;
        }
        switch(command[0].toLowerCase()){
            case "echo":
                sendAnswer(command[1]);
                break;
            case "time":
                sendAnswer(java.util.Calendar.getInstance().getTime().toString());
                break;
            case "download":
                System.out.println("File download start");
                file = command[1];
                try {
                    fDownload();
                } catch (SocketTimeoutException ste) {
                    ste.printStackTrace();
                    return -1;
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            case "upload":
                System.out.println("File upload start");
                file = command[1];
                fUpload();
                break;
            case "exit":case "quit":case "close":
                return 0;
            default: sendAnswer("Command not found");
        }
        return 1;
    }
    void fDownload() throws IOException{
        try {
            client.setSoTimeout(30000);
        } catch (SocketException e) {
            e.printStackTrace();
        }

        operation = 'd';
        file = "./" + file;
        File f = new File(file);
        DataInputStream rdFile = new DataInputStream(new FileInputStream(f));

        outStream.flush();
        outStream.writeLong(f.length());

        int packLength = 0;
        byte[] buf = new byte[1024];
        while(true){
            packLength = rdFile.read(buf, 0, 1024);
            if( packLength == -1 ) break;
            outStream.write(buf, 0, packLength);
        }
        rdFile.close();

        if(inStream.readBoolean() == true)
            operation = ' ';

        try {
            client.setSoTimeout(120000);
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }
    void fUpload(){
        try {
            client.setSoTimeout(30000);
        } catch (SocketException e) {
            e.printStackTrace();
        }
        operation = 'u';
        File f = new File(file);
        BufferedWriter wrToFile = null;
        try {
            wrToFile = new BufferedWriter(new FileWriter(file));
        } catch (IOException e) {
            e.printStackTrace();
        }


        try {
            outStream.writeLong(f.length());
            transLength = inStream.readLong();
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        if(transLength == 0) return;

        byte[] buf = new byte[1024];
        while(transLength > 1024){
            try {
                inStream.read(buf, 0, 1024);
                wrToFile.append(new String(buf));
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
            transLength -= 1024;
        }
        try {
            inStream.read(buf, 0, (int)transLength);
            wrToFile.append(new String(buf), 0, (int)transLength);
            transLength = 0;
            wrToFile.close();
            outStream.writeBoolean(true);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        try {
            client.setSoTimeout(120000);
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }
    String readCommand() throws IOException {
        String s = null;
        s = inStream.readUTF();
        return s;
    }

    void sendAnswer(String s){
        try {
            outStream.writeUTF(s);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

}
