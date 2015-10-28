package com.company;

import java.io.*;
import java.net.*;

public class TCPServer {

    ServerSocket socket;
    Socket connectedSocket;
    Receiver rec;

    TCPServer(){
        try {
            socket = new ServerSocket(6789);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }
    int acceptConnection() {
        try {
            System.out.println("Waiting for connection . . .");
            connectedSocket = socket.accept();
            connectedSocket.setSoTimeout(120000);
            connectedSocket.setReceiveBufferSize(4096);
            connectedSocket.setSendBufferSize(4096);
        } catch (IOException e) {
            System.out.println(e.getMessage());
            return -1;
        }
        System.out.println("Client connected: " + connectedSocket.getInetAddress().toString());

        rec = new Receiver(connectedSocket);
        try {
            if(Receiver.operation == ' ')
                rec.outStream.writeChar('0');
            else {
                rec.outStream.writeChar(Receiver.operation);
                rec.sendAnswer(Receiver.file);
                if(Receiver.operation == 'd') {
                    System.out.println("Restore download");
                    rec.fDownload();
                }
                else {
                    System.out.println("Restore upload");
                    rec.fUpload();
                }
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }

        while(!connectedSocket.isClosed())
            if(rec.receive() == 0) closeConnection();

        return 1;
    }
    void closeConnection(){
        Receiver.prevClient = connectedSocket.getInetAddress();
        try {
            System.out.println("Socket closed");
            connectedSocket.close();
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

}
