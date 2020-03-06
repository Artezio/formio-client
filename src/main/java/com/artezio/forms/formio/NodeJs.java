package com.artezio.forms.formio;

import com.artezio.forms.formio.exceptions.FormioProcessorException;

import java.io.*;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public class NodeJs {

    private Process nodeJs;

    public NodeJs(String script) {
        try {
            nodeJs = new ProcessBuilder("node", "-e", script).start();
        } catch (IOException e) {
            throw new RuntimeException("Could not start NodeJs process.", e);
        }
    }

    public String execute(String arguments) throws IOException {
        try {
            writeToStandardStream(arguments);
            StandardStreamsData standardStreamsData = readStandardStreams();
            checkErrors(standardStreamsData);
            return standardStreamsData.outData;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error while reading NodeJs process stream.", e);
        }
    }

    private StandardStreamsData readStandardStreams() throws InterruptedException, ExecutionException {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<String> stdoutData = executor.submit(() -> read(nodeJs.getInputStream()));
        Future<String> stderrData = executor.submit(() -> read(nodeJs.getErrorStream()));
        executor.shutdown();
        boolean taskExecutionCompleted = executor.awaitTermination(30, TimeUnit.SECONDS);
        if (!taskExecutionCompleted) {
            throw new FormioProcessorException("Reading from the process standard streams has timed out.");
        }
        return new StandardStreamsData(stdoutData.get(), stderrData.get());
    }

    private String read(InputStream inputStream) throws IOException {
        try(Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            StringBuilder stringBuilder = new StringBuilder();
            int bufferSize = 8192;
            CharBuffer buffer = CharBuffer.allocate(bufferSize);
            while (reader.read(buffer) > 0) {
                buffer.flip();
                stringBuilder.append(buffer);
            }
            return stringBuilder.toString();
        }
    }

    private void writeToStandardStream(String data) throws IOException {
        try (BufferedOutputStream outputStream = (BufferedOutputStream) nodeJs.getOutputStream()) {
            outputStream.write(data.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        }
    }

    private void checkErrors(StandardStreamsData standardStreamsData) {
        String errorData = standardStreamsData.errorData;
        if (!errorData.isEmpty()) {
            throw new FormioProcessorException(errorData);
        }
    }

    private void shutdown() throws InterruptedException {
        nodeJs.destroy();
        nodeJs.waitFor(5, TimeUnit.SECONDS);
        if (nodeJs.isAlive()) {
            nodeJs.destroyForcibly();
        }
    }

    private class StandardStreamsData {
        private final String outData;
        private final String errorData;

        public StandardStreamsData(String outData, String errorData) {
            this.outData = outData;
            this.errorData = errorData;
        }
    }

}
