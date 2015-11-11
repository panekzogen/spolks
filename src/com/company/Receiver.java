package com.company;

import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;

public class Receiver {
    static InetAddress prevClient = null;
    static int receivedPackages = 0;
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
        if(!sock.getInetAddress().equals(prevClient)){
            receivedPackages = 0;
            operation = ' ';
            file = "";
        }
        prevClient = sock.getInetAddress();
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
                    //fDownload();
                    fDownloadChannel();
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
    void fDownloadChannel() throws IOException{
        operation = 'd';
        file = "./" + file;
        File f = new File(file);
        SocketChannel channel = client.getChannel();

        Selector selector = Selector.open();
        channel.configureBlocking(false);
        channel.register(selector, SelectionKey.OP_ACCEPT);
        channel.register(selector, SelectionKey.OP_WRITE);

        while(true) {

            int readyChannels = selector.select(10000);
            if(readyChannels == 0) continue;

            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
            while(keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();
                if(key.isAcceptable()) {
                    // a connection was accepted by a ServerSocketChannel.

                } else if (key.isWritable()) {
                    // a channel is ready for writing
                }
                keyIterator.remove();
            }
        }
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

        int pts = (int)f.length()/1024 + 1 - receivedPackages;

        outStream.flush();
        outStream.writeInt(pts);

        System.out.print("Sended packages:\n0 of " + pts);

        byte[] buf = new byte[1024];
        for(int i = pts; i > 1; i--){
            rdFile.read(buf, 0, 1024);
            outStream.write(buf, 0, 1024);
            if(i % (pts/100) == 0)
                System.out.print("\r" + (pts - i) + " of " + pts + " [" + (((pts - i)* 100)/pts) + "%]");
        }

        if(inStream.readBoolean() == true)
            operation = ' ';
        outStream.writeInt((int)(f.length() - (f.length()/1024)*1024));
        rdFile.read(buf, 0, (int)(f.length() - (f.length()/1024)*1024));
        outStream.write(buf, 0, (int)(f.length() - (f.length()/1024)*1024));
        System.out.println("\r" + pts + " of " + pts + " [100%]");
        rdFile.close();

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
        /*File f = new File(file);
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
        }*/
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
