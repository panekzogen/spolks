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
            socket.setSoTimeout(30000);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    int acceptConnection() {
        try {
            connectedSocket = socket.accept();
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
        System.out.println("Client connected: " + connectedSocket.getInetAddress().toString());

        rec = new Receiver(connectedSocket);
        while(!connectedSocket.isClosed())
            if(rec.receive() == -1) closeConnection();
        return 1;
    }
    void closeConnection(){
        try {
            connectedSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
