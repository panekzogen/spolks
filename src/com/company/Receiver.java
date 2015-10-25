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
            return -1;
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
                fDownload();
                break;
            case "upload":
                System.out.println("File upload start");
                file = command[1];
                fUpload();
                break;
            case "exit":case "quit":case "close":
                return -1;
            default: sendAnswer("Command not found");
        }
        return 0;
    }
    void fDownload(){
        try {
            client.setSoTimeout(30000);
        } catch (SocketException e) {
            e.printStackTrace();
        }
        operation = 'd';
        file = "./" + file;
        File f = new File(file);
        DataInputStream rdFile = null;
        try {
            rdFile = new DataInputStream(new DataInputStream(new FileInputStream(f)));
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }

        if(transLength <= 0)
            transLength = f.length();
        else try {
            rdFile.skip(transLength);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        try {
            outStream.flush();
            outStream.writeLong(transLength);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }

        byte[] buf = new byte[1024];
        try {
            while(rdFile.read(buf, 0, 1024) != -1){
                if(transLength >= 1024) outStream.write(buf, 0, 1024);
                else outStream.write(buf, 0, (int)transLength);
                transLength -= 1024;
            }
            transLength = 0;
            rdFile.close();
            if(inStream.readBoolean() == true)
                operation = ' ';
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
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
