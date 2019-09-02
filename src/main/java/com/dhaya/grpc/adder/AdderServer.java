package com.dhaya.grpc.adder;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import com.dhaya.grpc.adder.AdderGrpc.AdderImplBase;
import com.dhaya.grpc.adder.InputRange;
import com.dhaya.grpc.adder.Response;

import java.io.IOException;

public class AdderServer {
    private final int port;
    private final Server server;

    public AdderServer(int port) {
        this.port = port;
        server = ServerBuilder.forPort(port).addService(new AdderServiceImpl()).build();
    }

    public void start() throws IOException {
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                System.err.println("*** shutting down gRPC server since JVM is shutting down");
                AdderServer.this.stop();
                System.err.println("*** server shut down");
            }
        });
    }

    /** Stop serving requests and shutdown resources. */
    public void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    /**
     * Main method.  This comment makes the linter happy.
     */
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(args[0]);
        AdderServer server = new AdderServer(port);
        server.start();
        server.blockUntilShutdown();
    }

    private static class AdderServiceImpl extends AdderImplBase {

        @Override
        public void addInts(InputRange request, StreamObserver<Response> responseObserver) {
            int start = request.getStart();
            int end = request.getEnd();

            int sum = 0;
            for (int i = start; i <= end; i++) {
                sum += i;

                if (i % 10 == 0) {
                    Response resp = Response.newBuilder()
                            .setResponse(sum)
                            .setPercentComplete(((float)(i - start + 1))/(end - start + 1))
                            .build();
                    System.out.println("Emitting output: " + resp.getResponse() + " after completion percentage " + resp.getPercentComplete() * 100);
                    responseObserver.onNext(resp);

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            responseObserver.onNext(Response.newBuilder().setResponse(sum).setPercentComplete(1.0f).build());
            responseObserver.onCompleted();
        }
    }

}
