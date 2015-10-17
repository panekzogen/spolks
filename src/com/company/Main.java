package com.company;

public class Main {

    public static void main(String[] args) {
        TCPServer server = new TCPServer();

        if(server.acceptConnection() == 1)
            System.out.println("Closed by client");


    }
}
