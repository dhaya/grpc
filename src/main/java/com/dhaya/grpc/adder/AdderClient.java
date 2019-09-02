package com.dhaya.grpc.adder;

import com.dhaya.grpc.adder.InputRange;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import com.dhaya.grpc.adder.AdderGrpc;
import com.dhaya.grpc.adder.AdderGrpc.AdderBlockingStub;
import com.dhaya.grpc.adder.AdderGrpc.AdderStub;
import com.dhaya.grpc.adder.Response;
import io.grpc.stub.StreamObserver;

import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class AdderClient {
    private final ManagedChannel channel;
    private final AdderBlockingStub blockingStub;
    private final AdderStub asyncStub;

    public AdderClient(String host, int port) {
        this(ManagedChannelBuilder.forAddress(host, port).usePlaintext());
    }

    public AdderClient(ManagedChannelBuilder<?> channelBuilder) {
        channel = channelBuilder.build();
        blockingStub = AdderGrpc.newBlockingStub(channel);
        asyncStub = AdderGrpc.newStub(channel);
    }

    public int addInts(int start, int end) {
        InputRange range = InputRange.newBuilder()
                .setStart(start)
                .setEnd(end)
                .build();
        Iterator<Response> responses = blockingStub.addInts(range);
        int last = 0;
        while (responses.hasNext()) {
            Response resp = responses.next();
            last = resp.getResponse();
            System.out.println(resp);
        }
        return last;
    }

    public void asyncAddInts(int start, int end, StreamObserver<Response> responseObserver) {
        InputRange range = InputRange.newBuilder()
                .setStart(start)
                .setEnd(end)
                .build();
        asyncStub.addInts(range, responseObserver);
    }

    public void shutdown() {
        channel.shutdown();
    }

    public static void blockingTest(String[] args) {
        AdderClient client1 = new AdderClient("localhost", 8090);
        AdderClient client2 = new AdderClient("localhost", 8091);

        System.out.println("Adding 1..100..");
        int part1 = client1.addInts(1, 50);
        int part2 = client2.addInts(51, 100);

        System.out.println("Total output = " + (part1 + part2));
    }

    public static void main(String[] args) throws Exception {
        AdderClient client1 = new AdderClient("localhost", 8090);
        AdderClient client2 = new AdderClient("localhost", 8091);

        System.out.println("Adding 1..100..");

        final CountDownLatch finishLatch = new CountDownLatch(2);

        StreamObserver<Response> o = new StreamObserver<Response>() {
            AtomicInteger sum = new AtomicInteger(0);

            public void onNext(Response response) {
                System.out.println("Completed percentage from 1: " + response.getPercentComplete());
                sum.addAndGet(response.getResponse());
            }

            public void onError(Throwable throwable) {
                throwable.printStackTrace();
            }

            public void onCompleted() {
                System.out.println("Completed: " + sum.get());
                finishLatch.countDown();
            }
        };

        client1.asyncAddInts(1, 50, o);
        client2.asyncAddInts(51, 100, o);

        finishLatch.await(1, TimeUnit.MINUTES);

        client1.shutdown();
        client2.shutdown();
    }

}
