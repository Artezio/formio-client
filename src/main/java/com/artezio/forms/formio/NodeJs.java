package com.artezio.forms.formio;

import com.artezio.forms.formio.exceptions.NodeJsException;

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
            throw new NodeJsException("Could not start NodeJs process", e);
        }
    }

    public String execute(String arguments) throws IOException {
        try {
            writeToStandardStream(arguments);
            StandardStreamsData standardStreamsData = readStandardStreams();
            checkErrors(standardStreamsData);
            return standardStreamsData.outData;
        } catch (InterruptedException | ExecutionException e) {
            throw new NodeJsException("Error while reading standard streams", e);
        }
    }

    private StandardStreamsData readStandardStreams() throws InterruptedException, ExecutionException {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<String> stdoutData = executor.submit(() -> read(nodeJs.getInputStream()));
        Future<String> stderrData = executor.submit(() -> read(nodeJs.getErrorStream()));
        executor.shutdown();
        boolean taskExecutionCompleted = executor.awaitTermination(30, TimeUnit.SECONDS);
        if (!taskExecutionCompleted) {
            throw new NodeJsException("Reading from standard streams has timed out");
        }
        return new StandardStreamsData(stdoutData.get(), stderrData.get());
    }

    private String read(InputStream inputStream) throws IOException {
        final char EOT = '\u0004';
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        StringBuilder stringBuilder = new StringBuilder();
        int bufferSize = 8192;
        CharBuffer buffer = CharBuffer.allocate(bufferSize);
        char lastChar = 0;
        while (lastChar != EOT) {
            reader.read(buffer);
            lastChar = getLastChar(buffer);
            if (!buffer.hasRemaining() || lastChar == EOT) {
                stringBuilder.append(readData(buffer));
            }
        }
        return stringBuilder.substring(0, stringBuilder.length() - 1);
    }

    private String readData(CharBuffer buffer) {
        buffer.flip();
        String result = buffer.toString();
        buffer.clear();
        return result;
    }

    private char getLastChar(CharBuffer buffer) {
        int bufferPosition = buffer.position();
        int bufferLimit = buffer.limit();
        char lastChar = buffer.flip().get(bufferPosition - 1);
        buffer.position(bufferPosition);
        buffer.limit(bufferLimit);
        return lastChar;
    }

    private void writeToStandardStream(String data) throws IOException {
        BufferedOutputStream outputStream = (BufferedOutputStream) nodeJs.getOutputStream();
        outputStream.write(data.getBytes());
        outputStream.flush();
    }

    private void checkErrors(StandardStreamsData standardStreamsData) {
        String errorData = standardStreamsData.errorData;
        if (!errorData.isEmpty()) {
            throw new NodeJsException(errorData);
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
