package com.artezio.forms.formio;

import com.artezio.forms.formio.exceptions.FormioProcessorException;
import net.minidev.json.JSONObject;

import javax.inject.Named;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.*;

@Named
public class NodeJsProcessor {

    private static final Map<String, String> SCRIPTS_CACHE = new ConcurrentHashMap<>();

    public synchronized byte[] executeScript(String scriptName, String formDefinition, String submissionData, String customComponentsDir)
            throws IOException {
        try {
            String script = loadScript(scriptName);
            Process nodeJs = runNodeJs(script, formDefinition, submissionData, customComponentsDir);
            StandardStreamsData standardStreamsData = readStandardStreams(nodeJs);
            checkErrors(standardStreamsData.stderrData);
            return standardStreamsData.stdoutData;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error while reading NodeJs process stream.", e);
        }
    }

    private StandardStreamsData readStandardStreams(Process process) throws InterruptedException, ExecutionException {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<byte[]> stdoutData = executor.submit(() -> toByteArray(process.getInputStream()));
        Future<byte[]> stderrData = executor.submit(() -> toByteArray(process.getErrorStream()));
        executor.shutdown();
        boolean taskExecutionCompleted = executor.awaitTermination(30, TimeUnit.SECONDS);
        if (!taskExecutionCompleted) {
            throw new RuntimeException("Reading from the process standard streams has timed out.");
        }
        return new StandardStreamsData(stdoutData.get(), stderrData.get());
    }

    private byte[] toByteArray(InputStream inputStream) throws IOException {
        byte[] bytes = inputStream.readAllBytes();
        inputStream.close();
        return bytes;
    }

    private String loadScript(String scriptName) {
        return SCRIPTS_CACHE.computeIfAbsent(scriptName, name -> {
            try (InputStream scriptResource = getClass().getClassLoader().getResourceAsStream("formio-scripts/" + scriptName)) {
                return new String(scriptResource.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                throw new RuntimeException("Could not load script " + scriptName, ex);
            }
        });
    }

    private Process runNodeJs(String script, String formDefinition, String submissionData, String customComponentsDir) throws IOException {
        Process process = new ProcessBuilder("node").start();
        String command = createCommand(script, formDefinition, submissionData, customComponentsDir);
        ByteBuffer byteBuffer = ByteBuffer.wrap(command.getBytes());
        try (BufferedOutputStream outputStream = (BufferedOutputStream) process.getOutputStream();
             WritableByteChannel byteChannel = Channels.newChannel(outputStream)) {
            byteChannel.write(byteBuffer);
            return process;
        }
    }

    private String createCommand(String script, String formDefinition, String submissionData, String customComponentsDir) {
        formDefinition = JSONObject.escape(formDefinition);
        submissionData = JSONObject.escape(submissionData);
        customComponentsDir = toSafePath(customComponentsDir);
        return String.format(script, formDefinition, submissionData, customComponentsDir) + System.lineSeparator();
    }

    private String toSafePath(String customComponentsDir) {
        return customComponentsDir.replaceAll("\\\\", "\\\\\\\\");
    }

    private void checkErrors(byte[] stderrContent) {
        String stderrMessage = new String(stderrContent, StandardCharsets.UTF_8);
        if (!stderrMessage.isEmpty()) {
            throw new FormioProcessorException(stderrMessage);
        }
    }

    private class StandardStreamsData {
        private final byte[] stdoutData;
        private final byte[] stderrData;

        public StandardStreamsData(byte[] stdoutData, byte[] stderrData) {
            this.stdoutData = stdoutData;
            this.stderrData = stderrData;
        }
    }

}
