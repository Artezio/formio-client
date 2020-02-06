package com.artezio.forms.formio;

import com.artezio.bpm.services.integration.Base64UrlFileStorage;
import com.artezio.bpm.services.integration.FileStorage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.io.IOUtils;
import org.camunda.bpm.BpmPlatform;
import org.camunda.bpm.ProcessEngineService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.internal.util.reflection.FieldSetter;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.servlet.ServletContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(BpmPlatform.class)
public class FormioClientTest {

    private final static String DRY_VALIDATION_AND_CLEANUP_SCRIPT_NAME = "cleanUpAndValidate.js";
    private final static String CLEAN_UP_SCRIPT_NAME = "cleanUp.js";
    private static final String EMBEDDED_DEPLOYMENT_FORM_STORING_PROTOCOL = "embedded:deployment:";

    @Rule
    public ProcessEngineRule processEngineRule = new ProcessEngineRule();

    @Mock
    private NodeJsProcessor nodeJsProcessor;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private ProcessEngine processEngine;
    @Mock
    private ServletContext servletContext;
    @InjectMocks
    private FormioClient formioClient = new FormioClient();
    private ObjectMapper objectMapper = new ObjectMapper();

    @Before
    public void setUp() throws Exception {
        PowerMockito.mockStatic(BpmPlatform.class);
        processEngine = mock(ProcessEngine.class);
        FileStorage fileStorage = new Base64UrlFileStorage();
        FileAttributeConverter fileAttributeConverter = new FileAttributeConverter(fileStorage);
        Field fileConverterField = FormioClient.class.getDeclaredField("fileAttributeConverter");
        FieldSetter.setField(formioClient, fileConverterField, fileAttributeConverter);
        ProcessEngineService mockProcessEngineService = mock(ProcessEngineService.class);
        PowerMockito.doReturn(mockProcessEngineService).when(BpmPlatform.class, "getProcessEngineService");
        when(mockProcessEngineService.getProcessEngine(anyString())).thenReturn(processEngine);
        when(processEngine.getRepositoryService()).thenReturn(repositoryService);
    }

    @After
    public void tearDown() throws NoSuchFieldException, IllegalAccessException {
        Field formsCacheField = FormioClient.class.getDeclaredField("FORM_CACHE");
        Field submitButtonsCacheField = FormioClient.class.getDeclaredField("SUBMISSION_PROCESSING_DECISIONS_CACHE");
        formsCacheField.setAccessible(true);
        submitButtonsCacheField.setAccessible(true);
        ((Map<String, JsonNode>) formsCacheField.get(FormioClient.class)).clear();
        ((Map<String, JsonNode>) submitButtonsCacheField.get(FormioClient.class)).clear();
    }

    @Test
    public void testGetFormWithData_FormIsInApplication_FormKeyHasPrefix() throws IOException {
        String deploymentId = "deploymentId";
        String formPath = "testForm.json";
        String formKey = "embedded:app:" + formPath;
        ObjectNode data = objectMapper.createObjectNode();
        ObjectNode submissionData = objectMapper.createObjectNode();
        submissionData.set("data", data);

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(formPath);
             InputStream webResource = getClass().getClassLoader().getResourceAsStream(formPath)) {
            JsonNode formDefinition = objectMapper.readTree(is);
            JsonNode expected = formDefinition.deepCopy();
            ((ObjectNode) expected).set("data", objectMapper.createObjectNode());
            byte[] scriptResult = expected.toString().getBytes();
            when(servletContext.getResourceAsStream(formPath)).thenReturn(webResource);
            when(nodeJsProcessor.executeScript(CLEAN_UP_SCRIPT_NAME, formDefinition.toString(), objectMapper.writeValueAsString(submissionData))).thenReturn(scriptResult);

            String actual = formioClient.getFormWithData(formKey, deploymentId, data);

            assertEquals(expected.toString(), actual);
        }
    }

    @Test
    public void testGetFormWithData_FormIsInApplication_FormKeyHasNoPrefix() throws IOException {
        String deploymentId = "deploymentId";
        String formPath = "testForm.json";
        ObjectNode data = objectMapper.createObjectNode();
        ObjectNode submissionData = objectMapper.createObjectNode();
        submissionData.set("data", data);

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(formPath);
             InputStream webResource = getClass().getClassLoader().getResourceAsStream(formPath)) {
            JsonNode formDefinition = objectMapper.readTree(is);
            JsonNode expected = formDefinition.deepCopy();
            ((ObjectNode) expected).set("data", objectMapper.createObjectNode());
            byte[] scriptResult = expected.toString().getBytes();
            when(servletContext.getResourceAsStream(formPath)).thenReturn(webResource);
            when(nodeJsProcessor.executeScript(CLEAN_UP_SCRIPT_NAME, formDefinition.toString(), objectMapper.writeValueAsString(submissionData))).thenReturn(scriptResult);

            String actual = formioClient.getFormWithData(formPath, deploymentId, data);

            assertEquals(expected.toString(), actual);
        }
    }

    @Test
    public void testGetFormWithData_FormIsInDeployment_FormKeyHasPrefix() throws IOException {
        String deploymentId = "deploymentId";
        String formPath = "testForm.json";
        String formKey = "embedded:deployment:" + formPath;
        ObjectNode data = objectMapper.createObjectNode();
        ObjectNode submissionData = objectMapper.createObjectNode();
        submissionData.set("data", data);

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(formPath);
             InputStream deploymentResource = getClass().getClassLoader().getResourceAsStream(formPath)) {
            JsonNode formDefinition = objectMapper.readTree(is);
            JsonNode expected = formDefinition.deepCopy();
            ((ObjectNode) expected).set("data", objectMapper.createObjectNode());
            byte[] scriptResult = expected.toString().getBytes();
            when(repositoryService.getResourceAsStream(deploymentId, formPath)).thenReturn(deploymentResource);
            when(nodeJsProcessor.executeScript(CLEAN_UP_SCRIPT_NAME, formDefinition.toString(), objectMapper.writeValueAsString(submissionData))).thenReturn(scriptResult);

            String actual = formioClient.getFormWithData(formKey, deploymentId, data);

            assertEquals(expected.toString(), actual);
        }
    }

    @Test
    public void testGetFormWithData_NoDataPassed() throws IOException {
        String deploymentId = "deploymentId";
        String formPath = "testForm.json";
        String formKey = EMBEDDED_DEPLOYMENT_FORM_STORING_PROTOCOL + formPath;
        ObjectNode data = objectMapper.createObjectNode();
        ObjectNode submissionData = objectMapper.createObjectNode();
        submissionData.set("data", data);

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(formPath);
             InputStream deploymentResource = getClass().getClassLoader().getResourceAsStream(formPath)) {
            JsonNode formDefinition = objectMapper.readTree(is);
            JsonNode expected = formDefinition.deepCopy();
            ((ObjectNode) expected).set("data", objectMapper.createObjectNode());
            byte[] scriptResult = expected.toString().getBytes();
            when(repositoryService.getResourceAsStream(deploymentId, formPath)).thenReturn(deploymentResource);
            when(nodeJsProcessor.executeScript(CLEAN_UP_SCRIPT_NAME, formDefinition.toString(), objectMapper.writeValueAsString(submissionData))).thenReturn(scriptResult);

            String actual = formioClient.getFormWithData(formKey, deploymentId, data);

            assertEquals(expected.toString(), actual);
        }
    }

    @Test
    public void testGetFormWithData_ExistentDataPassed() throws IOException {
        String deploymentId = "deploymentId";
        String formPath = "testForm.json";
        String formKey = EMBEDDED_DEPLOYMENT_FORM_STORING_PROTOCOL + formPath;

        ObjectNode taskData = objectMapper.createObjectNode();
        ArrayNode taskFile = getCamundaFileData("testForm.json", "application/json");
        taskData.set("testFile", taskFile);
        ObjectNode container = objectMapper.createObjectNode();
        container.put("containerField", "123");
        taskData.set("container", container);
        ObjectNode submissionData = objectMapper.createObjectNode();
        submissionData.set("data", taskData);

        ObjectNode scriptResultData = objectMapper.createObjectNode();
        ArrayNode scriptResultFileData = getFormioFileData("testForm.json", "application/json");
        scriptResultData.set("testFile", scriptResultFileData);
        ObjectNode scriptResultContainer = objectMapper.createObjectNode();
        scriptResultContainer.put("containerField", "123");
        scriptResultData.set("container", scriptResultContainer);
        ObjectNode scriptResult = objectMapper.createObjectNode();
        scriptResult.set("data", scriptResultData);

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(formPath);
             InputStream deploymentResource = getClass().getClassLoader().getResourceAsStream(formPath)) {
            JsonNode formDefinition = objectMapper.readTree(is);
            JsonNode expected = formDefinition.deepCopy();
            ObjectNode expectedData = objectMapper.valueToTree(taskData);
            expectedData.set("testFile", taskFile);
            ((ObjectNode) expected).set("data", expectedData);
            byte[] scriptResultBytes = scriptResult.toString().getBytes();
            when(repositoryService.getResourceAsStream(deploymentId, formPath)).thenReturn(deploymentResource);
            when(nodeJsProcessor.executeScript(eq(CLEAN_UP_SCRIPT_NAME), eq(formDefinition.toString()), anyString())).thenReturn(scriptResultBytes);

            String actual = formioClient.getFormWithData(formKey, deploymentId, taskData);
            JsonNode actualJson = objectMapper.readTree(actual);

            assertTrue(actualJson.has("data"));
            JsonNode actualData = actualJson.get("data");
            assertTrue(actualData.has("container"));
            assertTrue(actualData.has("testFile"));
            assertFalse(actualData.at("/testFile/0/name").isMissingNode());
            assertEquals("testForm.json", actualData.at("/testFile/0/name").asText());
        }
    }

    @Test
    public void testGetFormWithData_NonexistentDataPassed() throws IOException {
        String deploymentId = "deploymentId";
        String formPath = "forms/testForm.json";
        String formKey = EMBEDDED_DEPLOYMENT_FORM_STORING_PROTOCOL + formPath;

        ObjectNode data = objectMapper.createObjectNode();
        ArrayNode multiFileNode = getCamundaFileData("testForm.json", "application/json");
        data.set("testFile", multiFileNode);
        ObjectNode submissionData = objectMapper.createObjectNode();
        submissionData.set("data", data);

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(formPath);
             InputStream deploymentResource = getClass().getClassLoader().getResourceAsStream(formPath)) {
            JsonNode formDefinition = objectMapper.readTree(is);
            JsonNode expected = formDefinition.deepCopy();
            ((ObjectNode) expected).set("data", objectMapper.createObjectNode());
            byte[] scriptResult = expected.toString().getBytes();
            when(repositoryService.getResourceAsStream(deploymentId, formPath)).thenReturn(deploymentResource);
            when(nodeJsProcessor.executeScript(eq(CLEAN_UP_SCRIPT_NAME), eq(formDefinition.toString()), anyString())).thenReturn(scriptResult);

            String actual = formioClient.getFormWithData(formKey, deploymentId, data);

            assertEquals(expected.toString(), actual);
        }
    }

    @Test
    public void dryValidationAndCleanupTest_NoDataPassed() throws IOException {
        String deploymentId = "deploymentId";
        String formPath = "forms/testForm.json";
        String formKey = EMBEDDED_DEPLOYMENT_FORM_STORING_PROTOCOL + formPath;
        ObjectNode submittedVariables = objectMapper.createObjectNode();
        ObjectNode currentVariables = objectMapper.createObjectNode();
        ObjectNode submissionData = objectMapper.createObjectNode();
        submissionData.set("data", submittedVariables);

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(formPath);
             InputStream deploymentResource = getClass().getClassLoader().getResourceAsStream(formPath)) {
            JsonNode formDefinition = objectMapper.readTree(is);
            JsonNode expected = formDefinition.deepCopy();
            ((ObjectNode) expected).set("data", objectMapper.createObjectNode());
            byte[] scriptResult = expected.toString().getBytes();
            when(repositoryService.getResourceAsStream(deploymentId, formPath)).thenReturn(deploymentResource);
            when(nodeJsProcessor.executeScript(DRY_VALIDATION_AND_CLEANUP_SCRIPT_NAME, formDefinition.toString(), objectMapper.writeValueAsString(submissionData))).thenReturn(scriptResult);

            String actual = formioClient.dryValidationAndCleanup(formKey, deploymentId, submittedVariables, currentVariables);

            assertEquals(objectMapper.writeValueAsString(currentVariables), actual);
        }
    }

    @Test
    public void dryValidationAndCleanupTest_ValidDataPassed() throws IOException {
        String deploymentId = "deploymentId";
        String formPath = "forms/test.json";
        String formKey = EMBEDDED_DEPLOYMENT_FORM_STORING_PROTOCOL + formPath;
        ObjectNode currentVariables = objectMapper.createObjectNode();
        ObjectNode submittedVariables = objectMapper.createObjectNode();
        submittedVariables.put("text", "123");
        ObjectNode submissionData = objectMapper.createObjectNode();
        submissionData.set("data", submittedVariables);
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(formPath);
             InputStream deploymentResource = getClass().getClassLoader().getResourceAsStream(formPath)) {
            JsonNode formDefinition = objectMapper.readTree(is);
            JsonNode expected = formDefinition.deepCopy();
            ((ObjectNode) expected).set("data", objectMapper.valueToTree(submittedVariables));
            byte[] scriptResult = expected.toString().getBytes();
            when(repositoryService.getResourceAsStream(deploymentId, formPath)).thenReturn(deploymentResource);
            when(nodeJsProcessor.executeScript(DRY_VALIDATION_AND_CLEANUP_SCRIPT_NAME, formDefinition.toString(), objectMapper.writeValueAsString(submissionData))).thenReturn(scriptResult);

            String actual = formioClient.dryValidationAndCleanup(formKey, deploymentId, submittedVariables, currentVariables);

            assertEquals(objectMapper.writeValueAsString(submittedVariables), actual);
        }
    }

    @Test
    public void dryValidationAndCleanupTest_ValidDataWithChangedReadOnlyVariablePassed() throws IOException {
        String deploymentId = "deploymentId";
        String formPath = "forms/test.json";
        String formKey = EMBEDDED_DEPLOYMENT_FORM_STORING_PROTOCOL + formPath;
        ObjectNode currentVariables = objectMapper.createObjectNode();
        currentVariables.put("readOnly", "test");
        ObjectNode submittedVariables = objectMapper.createObjectNode();
        submittedVariables.put("readOnly", "test2");
        submittedVariables.put("text", "123");
        ObjectNode expected = objectMapper.createObjectNode();
        expected.put("text", "123");
        expected.put("readOnly", "test");
        ObjectNode formVariables = objectMapper.createObjectNode();
        formVariables.set("data", expected.deepCopy());
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(formPath);
             InputStream deploymentResource = getClass().getClassLoader().getResourceAsStream(formPath)) {
            JsonNode formDefinition = objectMapper.readTree(is);
            ObjectNode scriptResult = objectMapper.createObjectNode();
            scriptResult.set("data", objectMapper.valueToTree(expected));
            byte[] scriptResultBytes = formVariables.toString().getBytes();
            when(repositoryService.getResourceAsStream(deploymentId, formPath)).thenReturn(deploymentResource);
            when(nodeJsProcessor.executeScript(DRY_VALIDATION_AND_CLEANUP_SCRIPT_NAME, formDefinition.toString(), objectMapper.writeValueAsString(formVariables))).thenReturn(scriptResultBytes);

            String actual = formioClient.dryValidationAndCleanup(formKey, deploymentId, submittedVariables, currentVariables);

            assertEquals(expected, objectMapper.readTree(actual));
        }
    }

    @Test
    public void dryValidationAndCleanupTest_InvalidDataPassed() throws IOException {
        String deploymentId = "deploymentId";
        String formPath = "forms/testForm.json";
        String formKey = EMBEDDED_DEPLOYMENT_FORM_STORING_PROTOCOL + formPath;

        ObjectNode currentVariables = objectMapper.createObjectNode();
        ObjectNode submittedVariables = objectMapper.createObjectNode();
        ObjectNode submittedVariablesInFormioView = objectMapper.createObjectNode();
        submittedVariablesInFormioView.set("data", submittedVariables.deepCopy());

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(formPath);
             InputStream deploymentResource = getClass().getClassLoader().getResourceAsStream(formPath)) {
            JsonNode formDefinition = objectMapper.readTree(is);
            JsonNode expected = formDefinition.deepCopy();
            ((ObjectNode) expected).set("data", objectMapper.createObjectNode());
            byte[] scriptResult = expected.toString().getBytes();
            when(repositoryService.getResourceAsStream(deploymentId, formPath)).thenReturn(deploymentResource);
            when(nodeJsProcessor.executeScript(DRY_VALIDATION_AND_CLEANUP_SCRIPT_NAME, formDefinition.toString(), objectMapper.writeValueAsString(submittedVariablesInFormioView)))
                    .thenReturn(scriptResult);

            String actual = formioClient.dryValidationAndCleanup(formKey, deploymentId, submittedVariables, currentVariables);

            assertEquals(objectMapper.writeValueAsString(submittedVariables), actual);
        }
    }

    @Test
    public void testUnwrapData() throws IOException {
        JsonNode definition = new ObjectMapper().readTree(new String(Files.readAllBytes(Paths.get("./src/test/resources/full-form-with-nested-forms.json"))));
        JsonNode submittedData = new ObjectMapper().readTree(new String(Files.readAllBytes(Paths.get("./src/test/resources/full-form-with-nested-forms-data-submitted-unwrap.json"))));
        JsonNode expectedData = new ObjectMapper().readTree(new String(Files.readAllBytes(Paths.get("./src/test/resources/full-form-with-nested-forms-data-expected-unwrap.json"))));
        JsonNode actual = formioClient.unwrapGridData(submittedData, definition);
        assertEquals(expectedData, actual);
    }

    @Test
    public void testWrapData() throws IOException {
        JsonNode definition = new ObjectMapper().readTree(new String(Files.readAllBytes(Paths.get("./src/test/resources/full-form-with-nested-forms.json"))));
        JsonNode sourceData = new ObjectMapper().readTree(new String(Files.readAllBytes(Paths.get("./src/test/resources/full-form-with-nested-forms-data-submitted-wrap.json"))));
        JsonNode actual = formioClient.wrapGridData(sourceData, definition);
        assertFalse(actual.at("/nested-1/nested-3-datagrid/0/container").isMissingNode());
        assertEquals("text2", actual.at("/nested-1/nested-2/nested-2-text").asText());
    }

    @Test
    public void shouldProcessSubmittedData_SubmissionStateIsSubmitted() throws IOException, URISyntaxException {
        String deploymentId = "deploymentId";
        String formPath = "forms/form-with-state";
        String formKey = EMBEDDED_DEPLOYMENT_FORM_STORING_PROTOCOL + formPath;
        String submissionState = "submitted";

        when(repositoryService.getResourceAsStream(deploymentId, formPath + ".json")).thenReturn(new FileInputStream(getFile("forms/formWithState.json")));

        boolean actual = formioClient.shouldProcessSubmission(formKey, deploymentId, submissionState);

        assertTrue(actual);
    }

    @Test
    public void shouldProcessSubmittedData_SubmissionStateIsCanceled() throws IOException, URISyntaxException {
        String deploymentId = "deploymentId";
        String formPath = "forms/form-with-state";
        String formKey = EMBEDDED_DEPLOYMENT_FORM_STORING_PROTOCOL + formPath;
        String submissionState = "canceled";

        when(repositoryService.getResourceAsStream(deploymentId, formPath + ".json")).thenReturn(new FileInputStream(getFile("forms/formWithState.json")));

        boolean actual = formioClient.shouldProcessSubmission(formKey, deploymentId, submissionState);

        assertFalse(actual);
    }

    @Test
    public void shouldProcessSubmittedData_SkipDataProcessingPropertyNotSet() throws IOException, URISyntaxException {
        String deploymentId = "deploymentId";
        String formPath = "forms/form-with-state";
        String formKey = EMBEDDED_DEPLOYMENT_FORM_STORING_PROTOCOL + formPath;
        String submissionState = "submittedWithoutProperty";

        when(repositoryService.getResourceAsStream(deploymentId, formPath + ".json")).thenReturn(new FileInputStream(getFile("forms/formWithState.json")));

        boolean actual = formioClient.shouldProcessSubmission(formKey, deploymentId, submissionState);

        assertTrue(actual);
    }

    @Test
    public void testExpandSubforms_FormHasSubform() throws IOException, URISyntaxException {
        String formPath = "forms/formWithSubform.json";
        String childFormPath = "subform.json";
        String deploymentId = "1";
        JsonNode formDefinition = objectMapper.readTree(getFile(formPath));
        JsonNode expected = objectMapper.readTree(getFile("forms/formWithTransformedSubform.json"));

        when(repositoryService.getResourceAsStream(deploymentId, childFormPath)).thenReturn(new FileInputStream(getFile("forms/" + childFormPath)));

        JsonNode actual = formioClient.expandSubforms(formDefinition, deploymentId, EMBEDDED_DEPLOYMENT_FORM_STORING_PROTOCOL);

        assertEquals(sortArray(expected.get("components")), sortArray(actual.get("components")));
    }

    @Test
    public void testExpandSubforms_FormHasSubformInContainer() throws IOException, URISyntaxException {
        String formPath = "forms/formWithSubformInContainer.json";
        String subformPath = "subform.json";
        String deploymentId = "1";
        JsonNode formDefinition = objectMapper.readTree(getFile(formPath));
        JsonNode expected = objectMapper.readTree(getFile("forms/formWithTransformedSubformInContainer.json"));

        when(repositoryService.getResourceAsStream("1", subformPath)).thenReturn(new FileInputStream(getFile("forms/" + subformPath)));

        JsonNode actual = formioClient.expandSubforms(formDefinition, deploymentId, EMBEDDED_DEPLOYMENT_FORM_STORING_PROTOCOL);

        assertEquals(sortArray(expected.get("components")), sortArray(actual.get("components")));
    }

    @Test
    public void testExpandSubforms_FormHasSubformsInArrays() throws IOException, URISyntaxException {
        String formPath = "forms/formWithSubformsInArrays.json";
        String childFormPath = "subform.json";
        String deploymentId = "1";
        JsonNode formDefinition = objectMapper.readTree(getFile(formPath));
        JsonNode expected = objectMapper.readTree(getFile("forms/formWithTransformedSubformsInArrays.json"));

        try (FileInputStream formResource1Call = new FileInputStream(getFile("forms/" + childFormPath));
             FileInputStream formResource2Call = new FileInputStream(getFile("forms/" + childFormPath))) {
            when(repositoryService.getResourceAsStream("1", childFormPath)).thenReturn(formResource1Call, formResource2Call);

            JsonNode actual = formioClient.expandSubforms(formDefinition, deploymentId, EMBEDDED_DEPLOYMENT_FORM_STORING_PROTOCOL);

            assertEquals(sortArray(expected.get("components")), sortArray(actual.get("components")));
        }
    }

    @Test
    public void testExpandSubforms_FormHasSubformInOtherSubform() throws IOException, URISyntaxException {
        String formPath = "forms/formWithSubformInAnotherSubform.json";
        String childFormPath1 = "formWithSubform.json";
        String childFormPath2 = "subform.json";
        String deploymentId = "1";
        JsonNode formDefinition = objectMapper.readTree(getFile(formPath));
        JsonNode expected = objectMapper.readTree(getFile("forms/formWithTransformedSubformInAnotherTransformedSubform.json"));

        when(repositoryService.getResourceAsStream("1", childFormPath1)).thenReturn(new FileInputStream(getFile("forms/" + childFormPath1)));
        when(repositoryService.getResourceAsStream("1", childFormPath2)).thenReturn(new FileInputStream(getFile("forms/" + childFormPath2)));

        JsonNode actual = formioClient.expandSubforms(formDefinition, deploymentId, EMBEDDED_DEPLOYMENT_FORM_STORING_PROTOCOL);

        assertEquals(sortArray(expected.get("components")), sortArray(actual.get("components")));
    }

    private ArrayNode getFormioFileData(String filename, String mimeType) {
        ArrayNode files = objectMapper.createArrayNode();
        ObjectNode file = objectMapper.createObjectNode();
        file.put("storage", "base64");
        file.put("name", filename);
        file.put("originalName", filename);
        byte[] data;
        try {
            data = IOUtils.toByteArray(FormioClientTest.class.getClassLoader().getResourceAsStream(filename));
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Could not load file");
        }
        file.put("size", data.length);
        file.put("type", mimeType);
        file.put("url", String.format("data:%s;base64,%s", mimeType, Base64.getMimeEncoder().encodeToString(data)));
        files.add(file);
        return files;
    }

    private ArrayNode getCamundaFileData(String filename, String mimeType) {
        ArrayNode files = objectMapper.createArrayNode();
        ObjectNode file = objectMapper.createObjectNode();
        file.put("name", filename);
        file.put("filename", filename);
        byte[] data;
        try {
            data = IOUtils.toByteArray(FormioClientTest.class.getClassLoader().getResourceAsStream(filename));
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Could not load file");
        }
        file.put("size", data.length);
        file.put("mimeType", mimeType);
        file.put("url", String.format("data:%s;base64,%s", mimeType, Base64.getMimeEncoder().encodeToString(data)));
        files.add(file);
        return files;
    }

    private JsonNode sortArray(JsonNode arrayNode) {
        return objectMapper.valueToTree(getArrayElementStream((ArrayNode) arrayNode)
                .sorted(Comparator.comparing(objectNode -> objectNode.get("key").asText()))
                .map(this::sortObject)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll));
    }

    private JsonNode sortObject(JsonNode component) {
        return objectMapper.valueToTree(getFieldStream(component)
                .sorted(Map.Entry.comparingByKey())
                .map(field -> {
                    JsonNode fieldValue = field.getValue();
                    if (fieldValue instanceof ObjectNode) {
                        return new AbstractMap.SimpleEntry<>(field.getKey(), sortObject(fieldValue));
                    } else if (fieldValue instanceof ArrayNode) {
                        return new AbstractMap.SimpleEntry<>(field.getKey(), sortArray(fieldValue));
                    } else {
                        return field;
                    }
                })
                .collect(
                        LinkedHashMap::new,
                        (map, entry) -> map.put(entry.getKey(), entry.getValue()),
                        LinkedHashMap::putAll
                )
        );
    }

    private Stream<Map.Entry<String, JsonNode>> getFieldStream(JsonNode element) {
        return StreamSupport.stream(Spliterators
                .spliteratorUnknownSize(element.fields(), Spliterator.ORDERED), false);
    }

    private Stream<JsonNode> getArrayElementStream(ArrayNode arrayNode) {
        return StreamSupport.stream(Spliterators
                .spliteratorUnknownSize(arrayNode.elements(), Spliterator.ORDERED), false);
    }

    private File getFile(String fileName) throws URISyntaxException {
        return new File(getClass().getClassLoader().getResource(fileName).toURI());
    }

}
