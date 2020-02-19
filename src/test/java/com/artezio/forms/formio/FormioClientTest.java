package com.artezio.forms.formio;

import com.artezio.bpm.resources.ResourceLoader;
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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest(BpmPlatform.class)
public class FormioClientTest {

    private static final String DRY_VALIDATION_AND_CLEANUP_SCRIPT_NAME = "cleanUpAndValidate.js";
    private static final String CLEAN_UP_SCRIPT_NAME = "cleanUp.js";

    @Rule
    public ProcessEngineRule processEngineRule = new ProcessEngineRule();

    @Mock
    private NodeJsProcessor nodeJsProcessor;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private ProcessEngine processEngine;
    @Mock
    private ResourceLoader resourceLoader;
    @InjectMocks
    private FormioClient formioClient = new FormioClient();
    private ObjectMapper jsonMapper = new ObjectMapper();

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
        Field formsCacheField = FormioClient.class.getDeclaredField("FORMS_CACHE");
        Field submitButtonsCacheField = FormioClient.class.getDeclaredField("SUBMISSION_PROCESSING_DECISIONS_CACHE");
        formsCacheField.setAccessible(true);
        submitButtonsCacheField.setAccessible(true);
        ((Map<String, JsonNode>) formsCacheField.get(FormioClient.class)).clear();
        ((Map<String, JsonNode>) submitButtonsCacheField.get(FormioClient.class)).clear();
    }

    @Test
    public void testGetFormWithData_NoDataPassed() throws IOException {
        String formKey = "forms/testForm.json";
        ObjectNode data = jsonMapper.createObjectNode();
        ObjectNode submissionData = jsonMapper.createObjectNode();
        submissionData.set("data", data);
        String submissionJson = jsonMapper.writeValueAsString(submissionData);
        InputStream is = getClass().getClassLoader().getResourceAsStream(formKey);
        InputStream form = getClass().getClassLoader().getResourceAsStream(formKey);
        JsonNode formDefinition = jsonMapper.readTree(is);
        JsonNode expected = formDefinition.deepCopy();
        ((ObjectNode) expected).set("data", jsonMapper.createObjectNode());
        byte[] scriptResult = expected.toString().getBytes();

        when(resourceLoader.getResource(formKey)).thenReturn(form);
        when(nodeJsProcessor.executeScript(eq(CLEAN_UP_SCRIPT_NAME), eq(formDefinition.toString()), eq(submissionJson), any(String.class)))
                .thenReturn(scriptResult);

        String actual = formioClient.getFormWithData(formKey, submissionData, resourceLoader);

        assertEquals(expected.toString(), actual);

    }

    @Test
    //TODO Fix
    // Instead of exact value of submission any(String.class) is passed into 'nodeJsProcess.executeScript()' scenario
    // because of the problem with file conversion
    public void testGetFormWithData_ExistentDataPassed() throws IOException {
        String formKey = "forms/testForm.json";
        ObjectNode taskData = jsonMapper.createObjectNode();
        ArrayNode taskFile = getCamundaFileData(formKey, "application/json");
        taskData.set("testFile", taskFile);
        ObjectNode container = jsonMapper.createObjectNode();
        container.put("containerField", "123");
        taskData.set("container", container);
        ObjectNode submissionData = jsonMapper.createObjectNode();
        submissionData.set("data", taskData);
        ObjectNode scriptResultData = jsonMapper.createObjectNode();
        ArrayNode scriptResultFileData = getFormioFileData(formKey, "application/json");
        scriptResultData.set("testFile", scriptResultFileData);
        ObjectNode scriptResultContainer = jsonMapper.createObjectNode();
        scriptResultContainer.put("containerField", "123");
        scriptResultData.set("container", scriptResultContainer);
        ObjectNode scriptResult = jsonMapper.createObjectNode();
        scriptResult.set("data", scriptResultData);
        byte[] scriptResultBytes = scriptResult.toString().getBytes();
        InputStream is = getClass().getClassLoader().getResourceAsStream(formKey);
        InputStream form = getClass().getClassLoader().getResourceAsStream(formKey);
        JsonNode formDefinition = jsonMapper.readTree(is);
        JsonNode expected = formDefinition.deepCopy();
        ObjectNode expectedData = jsonMapper.valueToTree(taskData);
        expectedData.set("testFile", taskFile);
        ((ObjectNode) expected).set("data", expectedData);

        when(resourceLoader.getResource(formKey)).thenReturn(form);
        when(nodeJsProcessor.executeScript(eq(CLEAN_UP_SCRIPT_NAME), eq(formDefinition.toString()), any(String.class), any(String.class)))
                .thenReturn(scriptResultBytes);

        String actual = formioClient.getFormWithData(formKey, submissionData, resourceLoader);
        JsonNode actualJson = jsonMapper.readTree(actual);

        assertTrue(actualJson.has("data"));
        JsonNode actualData = actualJson.get("data");
        assertTrue(actualData.has("container"));
        assertTrue(actualData.has("testFile"));
        assertFalse(actualData.at("/testFile/0/name").isMissingNode());
        assertEquals(formKey, actualData.at("/testFile/0/name").asText());

    }

    @Test
    //TODO Fix
    // Instead of exact value of submission any(String.class) is passed into 'nodeJsProcess.executeScript()' scenario
    // because of the problem with file conversion
    public void testGetFormWithData_NonexistentDataPassed() throws IOException {
        String formKey = "forms/testForm.json";
        ObjectNode data = jsonMapper.createObjectNode();
        ArrayNode multiFileNode = getCamundaFileData(formKey, "application/json");
        data.set("testFile", multiFileNode);
        ObjectNode submissionData = jsonMapper.createObjectNode();
        submissionData.set("data", data.deepCopy());
        ((ObjectNode) submissionData.get("data")).replace("testFile", multiFileNode);
        InputStream is = getClass().getClassLoader().getResourceAsStream(formKey);
        InputStream form = getClass().getClassLoader().getResourceAsStream(formKey);
        JsonNode formDefinition = jsonMapper.readTree(is);
        JsonNode scriptResult = formDefinition.deepCopy();
        ((ObjectNode) scriptResult).set("data", jsonMapper.createObjectNode());

        when(resourceLoader.getResource(formKey)).thenReturn(form);
        when(nodeJsProcessor.executeScript(eq(CLEAN_UP_SCRIPT_NAME), eq(formDefinition.toString()), any(String.class), any(String.class)))
                .thenReturn(scriptResult.toString().getBytes());

        String actual = formioClient.getFormWithData(formKey, submissionData, resourceLoader);

        assertEquals(scriptResult.toString(), actual);
    }

    @Test
    public void dryValidationAndCleanupTest_NoDataPassed() throws IOException {
        String formKey = "forms/testForm.json";
        ObjectNode submittedVariables = jsonMapper.createObjectNode();
        ObjectNode currentVariables = jsonMapper.createObjectNode();
        ObjectNode submissionData = jsonMapper.createObjectNode();
        submissionData.set("data", submittedVariables);
        String submissionJson = submissionData.toString();
        InputStream is = getClass().getClassLoader().getResourceAsStream(formKey);
        InputStream form = getClass().getClassLoader().getResourceAsStream(formKey);
        JsonNode formDefinition = jsonMapper.readTree(is);
        JsonNode expected = formDefinition.deepCopy();
        ((ObjectNode) expected).set("data", jsonMapper.createObjectNode());
        byte[] scriptResult = expected.toString().getBytes();

        when(resourceLoader.getResource(formKey)).thenReturn(form);
        when(nodeJsProcessor.executeScript(eq(DRY_VALIDATION_AND_CLEANUP_SCRIPT_NAME), eq(formDefinition.toString()), eq(submissionJson), any(String.class)))
                .thenReturn(scriptResult);

        String actual = formioClient.dryValidationAndCleanup(formKey, submittedVariables, currentVariables, resourceLoader);

        assertEquals(jsonMapper.writeValueAsString(currentVariables), actual);

    }

    @Test
    public void dryValidationAndCleanupTest_ValidDataPassed() throws IOException {
        String formKey = "forms/test.json";
        ObjectNode currentVariables = jsonMapper.createObjectNode();
        ObjectNode submittedVariables = jsonMapper.createObjectNode();
        submittedVariables.put("text", "123");
        ObjectNode submissionData = jsonMapper.createObjectNode();
        submissionData.set("data", submittedVariables);
        String submissionJson = submissionData.toString();
        InputStream is = getClass().getClassLoader().getResourceAsStream(formKey);
        InputStream form = getClass().getClassLoader().getResourceAsStream(formKey);
        JsonNode formDefinition = jsonMapper.readTree(is);
        JsonNode expected = formDefinition.deepCopy();
        ((ObjectNode) expected).set("data", jsonMapper.valueToTree(submittedVariables));
        byte[] scriptResult = expected.toString().getBytes();

        when(resourceLoader.getResource(formKey)).thenReturn(form);
        when(nodeJsProcessor.executeScript(eq(DRY_VALIDATION_AND_CLEANUP_SCRIPT_NAME), eq(formDefinition.toString()), eq(submissionJson), any(String.class)))
                .thenReturn(scriptResult);

        String actual = formioClient.dryValidationAndCleanup(formKey, submittedVariables, currentVariables, resourceLoader);

        assertEquals(jsonMapper.writeValueAsString(submittedVariables), actual);

    }

    @Test
    public void dryValidationAndCleanupTest_ValidDataWithChangedReadOnlyVariablePassed() throws IOException {
        String formKey = "forms/test.json";
        ObjectNode currentVariables = jsonMapper.createObjectNode();
        currentVariables.put("readOnly", "test");
        ObjectNode submittedVariables = jsonMapper.createObjectNode();
        submittedVariables.put("readOnly", "test2");
        submittedVariables.put("text", "123");
        ObjectNode expected = jsonMapper.createObjectNode();
        expected.put("text", "123");
        expected.put("readOnly", "test");
        ObjectNode formVariables = jsonMapper.createObjectNode();
        formVariables.set("data", expected.deepCopy());
        String submissionJson = formVariables.toString();
        InputStream is = getClass().getClassLoader().getResourceAsStream(formKey);
        InputStream form = getClass().getClassLoader().getResourceAsStream(formKey);
        JsonNode formDefinition = jsonMapper.readTree(is);
        ObjectNode scriptResult = jsonMapper.createObjectNode();
        scriptResult.set("data", jsonMapper.valueToTree(expected));
        byte[] scriptResultBytes = formVariables.toString().getBytes();

        when(resourceLoader.getResource(formKey)).thenReturn(form);
        when(nodeJsProcessor.executeScript(eq(DRY_VALIDATION_AND_CLEANUP_SCRIPT_NAME), eq(formDefinition.toString()), eq(submissionJson), any(String.class)))
                .thenReturn(scriptResultBytes);

        String actual = formioClient.dryValidationAndCleanup(formKey, submittedVariables, currentVariables, resourceLoader);

        assertEquals(expected, jsonMapper.readTree(actual));

    }

    @Test
    public void dryValidationAndCleanupTest_InvalidDataPassed() throws IOException {
        String formKey = "forms/testForm.json";
        ObjectNode currentVariables = jsonMapper.createObjectNode();
        ObjectNode submittedVariables = jsonMapper.createObjectNode();
        ObjectNode submittedVariablesInFormioView = jsonMapper.createObjectNode();
        submittedVariablesInFormioView.set("data", submittedVariables.deepCopy());
        String submissionJson = submittedVariablesInFormioView.toString();
        InputStream is = getClass().getClassLoader().getResourceAsStream(formKey);
        InputStream form = getClass().getClassLoader().getResourceAsStream(formKey);
        JsonNode formDefinition = jsonMapper.readTree(is);
        JsonNode expected = formDefinition.deepCopy();
        ((ObjectNode) expected).set("data", jsonMapper.createObjectNode());
        byte[] scriptResult = expected.toString().getBytes();

        when(resourceLoader.getResource(formKey)).thenReturn(form);
        when(nodeJsProcessor.executeScript(eq(DRY_VALIDATION_AND_CLEANUP_SCRIPT_NAME), eq(formDefinition.toString()), eq(submissionJson), any(String.class)))
                .thenReturn(scriptResult);

        String actual = formioClient.dryValidationAndCleanup(formKey, submittedVariables, currentVariables, resourceLoader);

        assertEquals(jsonMapper.writeValueAsString(submittedVariables), actual);

    }

    @Test
    public void testWrapGridData() throws IOException, URISyntaxException {
        JsonNode definition = jsonMapper.readTree(getFile("forms/full-form-with-nested-forms.json"));
        JsonNode sourceData = jsonMapper.readTree(getFile("forms/full-form-with-nested-forms-data-submitted-wrap.json"));

        JsonNode actual = formioClient.wrapGridData(sourceData, definition);

        assertFalse(actual.at("/nested-1/nested-3-datagrid/0/container").isMissingNode());
        assertEquals("text2", actual.at("/nested-1/nested-2/nested-2-text").asText());
    }

    @Test
    public void shouldProcessSubmittedData_SubmissionStateIsSubmitted() throws IOException, URISyntaxException {
        String formKey = "forms/formWithState.json";
        String submissionState = "submitted";
        FileInputStream form = new FileInputStream(getFile(formKey));

        when(resourceLoader.getResource(formKey)).thenReturn(form);
        boolean actual = formioClient.shouldProcessSubmission(formKey, submissionState, resourceLoader);

        assertTrue(actual);

    }

    @Test
    public void shouldProcessSubmittedData_SubmissionStateIsCanceled() throws IOException, URISyntaxException {
        String formKey = "forms/formWithState.json";
        String submissionState = "canceled";
        FileInputStream form = new FileInputStream(getFile(formKey));

        when(resourceLoader.getResource(formKey)).thenReturn(form);
        boolean actual = formioClient.shouldProcessSubmission(formKey, submissionState, resourceLoader);

        assertFalse(actual);

    }

    @Test
    public void shouldProcessSubmittedData_SkipDataProcessingPropertyNotSet() throws IOException, URISyntaxException {
        String formKey = "forms/formWithState.json";
        String submissionState = "submittedWithoutProperty";
        FileInputStream form = new FileInputStream(getFile(formKey));

        when(resourceLoader.getResource(formKey)).thenReturn(form);
        boolean actual = formioClient.shouldProcessSubmission(formKey, submissionState, resourceLoader);

        assertTrue(actual);

    }

    @Test
    public void testExpandSubforms_FormHasSubform() throws IOException, URISyntaxException {
        String formKey = "forms/formWithSubform.json";
        String subformKey = "subform.json";
        JsonNode formDefinition = jsonMapper.readTree(getFile(formKey));
        InputStream subform = new FileInputStream(getFile("forms/" + subformKey));
        JsonNode expected = jsonMapper.readTree(getFile("forms/formWithTransformedSubform.json"));

        when(resourceLoader.getResource(subformKey)).thenReturn(subform);

        JsonNode actual = formioClient.expandSubforms(formDefinition, resourceLoader);

        assertEquals(sortArray(expected.get("components")), sortArray(actual.get("components")));

    }

    @Test
    public void testExpandSubforms_FormHasSubformInContainer() throws IOException, URISyntaxException {
        String formKey = "forms/formWithSubformInContainer.json";
        String subformKey = "subform.json";
        String subformPath = "forms/" + subformKey;
        JsonNode formDefinition = jsonMapper.readTree(getFile(formKey));
        JsonNode expected = jsonMapper.readTree(getFile("forms/formWithTransformedSubformInContainer.json"));
        InputStream form = new FileInputStream(getFile(formKey));
        InputStream subform = new FileInputStream(getFile(subformPath));

        when(resourceLoader.getResource(formKey)).thenReturn(form);
        when(resourceLoader.getResource(subformKey)).thenReturn(subform);

        JsonNode actual = formioClient.expandSubforms(formDefinition, resourceLoader);

        assertEquals(sortArray(expected.get("components")), sortArray(actual.get("components")));
    }

    @Test
    public void testExpandSubforms_FormHasSubformsInArrays() throws IOException, URISyntaxException {
        String formKey = "forms/formWithSubformsInArrays.json";
        String subformKey = "subform.json";
        JsonNode formDefinition = jsonMapper.readTree(getFile(formKey));
        JsonNode expected = jsonMapper.readTree(getFile("forms/formWithTransformedSubformsInArrays.json"));
        FileInputStream subform = new FileInputStream(getFile("forms/" + subformKey));

        when(resourceLoader.getResource(subformKey)).thenReturn(subform);

        JsonNode actual = formioClient.expandSubforms(formDefinition, resourceLoader);

        assertEquals(sortArray(expected.get("components")), sortArray(actual.get("components")));
    }

    @Test
    public void testExpandSubforms_FormHasSubformInAnotherSubform() throws IOException, URISyntaxException {
        String formPath = "forms/formWithSubformInAnotherSubform.json";
        String childFormKey1 = "formWithSubform.json";
        String childFormKey2 = "subform.json";
        String expectedFormPath = "forms/formWithTransformedSubformInAnotherTransformedSubform.json";
        JsonNode formDefinition = jsonMapper.readTree(getFile(formPath));
        FileInputStream subform1 = new FileInputStream(getFile("forms/" + childFormKey1));
        FileInputStream subform2 = new FileInputStream(getFile("forms/" + childFormKey2));
        JsonNode expected = jsonMapper.readTree(getFile(expectedFormPath));

        when(resourceLoader.getResource(childFormKey1)).thenReturn(subform1);
        when(resourceLoader.getResource(childFormKey2)).thenReturn(subform2);

        JsonNode actual = formioClient.expandSubforms(formDefinition, resourceLoader);

        assertEquals(sortArray(expected.get("components")), sortArray(actual.get("components")));
    }

    private ArrayNode getFormioFileData(String filename, String mimeType) {
        ArrayNode files = jsonMapper.createArrayNode();
        ObjectNode file = jsonMapper.createObjectNode();
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
        ArrayNode files = jsonMapper.createArrayNode();
        ObjectNode file = jsonMapper.createObjectNode();
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
        return jsonMapper.valueToTree(getArrayElementStream((ArrayNode) arrayNode)
                .sorted(Comparator.comparing(objectNode -> objectNode.get("key").asText()))
                .map(this::sortObject)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll));
    }

    private JsonNode sortObject(JsonNode component) {
        return jsonMapper.valueToTree(getFieldStream(component)
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
