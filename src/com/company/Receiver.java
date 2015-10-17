package com.company;

import com.sun.xml.internal.fastinfoset.util.CharArray;

import javax.print.DocFlavor;
import java.io.*;
import java.net.*;
import java.nio.charset.Charset;

public class Receiver {
    static String prevClient = null;

    BufferedReader inStream;
    DataOutputStream outStream;

    String[] command;

    Receiver(Socket sock){
        try {
            inStream = new BufferedReader(new InputStreamReader(sock.getInputStream()));
            outStream = new DataOutputStream(sock.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    int receive(){
        command = readCommand().split(" ", 2);
        switch(command[0].toLowerCase()){
            case "echo":
                sendAnswer(command[1]);
                break;
            case "time":
                sendAnswer(java.util.Calendar.getInstance().getTime().toString());
                break;
            case "download":
                fDownload(command[1]);
                break;
            case "upload":
                fUpload();
                break;
            case "exit":case "quit":case "close":
                return -1;
            default: sendAnswer("Command not found");
        }
        return 0;
    }
    void fDownload(String file){
        file = "/home/" + file;
        File f = new File(file);
        DataInputStream rdFile = null;
        try {
            rdFile = new DataInputStream(new DataInputStream(new FileInputStream(f)));
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            outStream.flush();
            outStream.writeLong(f.length());
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("reading . . .");
        byte[] buf = new byte[10];
        try {
            while(rdFile.read(buf, 0, 10) != -1)
                outStream.write(buf, 0, 10);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("end writing");
    }
    void fUpload(){

    }
    String readCommand(){
        String s = null;
        try {
             s = inStream.readLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return s;
    }

    void sendAnswer(String s){
        try {
            outStream.writeBytes(s + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
