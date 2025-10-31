package edu.pucmm.eict.clientes.grpc;

import edu.pucmm.eict.services.UrlService;
import edu.pucmm.eict.services.UserService;
import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;

public class GrpcServer {
    public static void main(String[] args) {
        int port = 50051;
        UrlService urlService = new UrlService();
        UserService userService = new UserService(); // Se asume que se implementa al menos el método getUserByUsername

        // Configura el servidor para aceptar mensajes de hasta 10 MB
        Server server = ServerBuilder.forPort(port)
                .maxInboundMessageSize(10 * 1024 * 1024)
                .addService((BindableService) new UrlShortenerServiceImpl(urlService, userService))
                .build();

        try {
            server.start();
            System.out.println("gRPC Server started, listening on port " + port);
            server.awaitTermination();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
