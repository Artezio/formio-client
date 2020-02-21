package com.artezio.forms.formio;

import com.artezio.forms.formio.exceptions.FormioProcessorException;
import net.minidev.json.JSONObject;
import org.apache.commons.io.IOUtils;

import javax.inject.Named;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.*;

@Named
public class NodeJsProcessor {

    private static final Map<String, String> SCRIPTS_CACHE = new ConcurrentHashMap<>();

    public synchronized byte[] executeScript(String scriptName, String formDefinition, String submissionData, String customComponentsDir) throws IOException {
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
        byte[] bytes = IOUtils.toByteArray(inputStream);
        inputStream.close();
        return bytes;
    }

    private String loadScript(String scriptName) {
        return SCRIPTS_CACHE.computeIfAbsent(scriptName, name -> {
            try (InputStream scriptResource = getClass().getClassLoader().getResourceAsStream("formio-scripts/" + scriptName)) {
                return IOUtils.toString(scriptResource, StandardCharsets.UTF_8);
            } catch (IOException ex) {
                throw new RuntimeException("Could not load script " + scriptName, ex);
            }
        });
    }

    private Process runNodeJs(String script, String formDefinition, String submissionData, String customComponentsDir) throws IOException {
        Process process = new ProcessBuilder("node").start();
        String command = createCommand(script, formDefinition, submissionData, customComponentsDir);
        try (BufferedWriter outputStream = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {
            outputStream.write(command);
            outputStream.newLine();
            outputStream.flush();
            return process;
        }
    }

    private String createCommand(String script, String formDefinition, String submissionData, String customComponentsDir) {
        formDefinition = JSONObject.escape(formDefinition);
        submissionData = JSONObject.escape(submissionData);
        return String.format(script, formDefinition, submissionData, customComponentsDir);
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
