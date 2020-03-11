package com.artezio.forms.formio;

import com.artezio.bpm.resources.ResourceLoader;
import com.artezio.bpm.services.integration.Base64UrlFileStorage;
import com.artezio.bpm.services.integration.FileStorage;
import com.artezio.forms.formio.exceptions.FormValidationException;
import com.artezio.forms.formio.nodejs.NodeJsExecutor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import junitx.framework.ListAssert;
import org.apache.commons.io.FileUtils;
import org.camunda.bpm.BpmPlatform;
import org.camunda.bpm.ProcessEngineService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Arrays.asList;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.internal.util.reflection.FieldSetter.setField;

@RunWith(PowerMockRunner.class)
@PrepareForTest(BpmPlatform.class)
@PowerMockIgnore({"com.sun.org.apache.*", "javax.xml.*", "java.xml.*", "org.xml.*", "org.w3c.dom.*", "javax.management.*"})
public class FormioClientTest {

    private static final String VALIDATION_OPERATION_NAME = "validate";
    private static final String CLEANUP_OPERATION_NAME = "cleanup";
    private static final Path TEST_FORMIO_TMP_DIR = Paths.get(System.getProperty("java.io.tmpdir"), ".test-formio");
    private static final NodeJsExecutor NODEJS_EXECUTOR = mock(NodeJsExecutor.class);
    private static final String PUBLIC_RESOURCES_DIRECTORY = "public";

    @Rule
    public ProcessEngineRule processEngineRule = new ProcessEngineRule();
    
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private ProcessEngine processEngine;
    @Mock
    private ResourceLoader resourceLoader;
    @InjectMocks
    private FormioClient formioClient = new FormioClient();
    private ObjectMapper jsonMapper = new ObjectMapper();

    @BeforeClass
    public static void prepareStaticFinalFields() throws NoSuchFieldException, IllegalAccessException {
        setFinalField(FormioClient.class,"FORMIO_TEMP_DIR", TEST_FORMIO_TMP_DIR);
        setFinalField(FormioClient.class,"NODEJS_EXECUTOR", NODEJS_EXECUTOR);
    }

    @Before
    public void setUp() throws Exception {
        PowerMockito.mockStatic(BpmPlatform.class);
        processEngine = mock(ProcessEngine.class);
        FileStorage fileStorage = new Base64UrlFileStorage();
        FileAttributeConverter fileAttributeConverter = new FileAttributeConverter(fileStorage);
        setField(formioClient, FormioClient.class.getDeclaredField("fileAttributeConverter"), fileAttributeConverter);
        ProcessEngineService mockProcessEngineService = mock(ProcessEngineService.class);
        PowerMockito.doReturn(mockProcessEngineService).when(BpmPlatform.class, "getProcessEngineService");
        when(mockProcessEngineService.getProcessEngine(anyString())).thenReturn(processEngine);
        when(processEngine.getRepositoryService()).thenReturn(repositoryService);
    }

    @After
    public void tearDown() throws NoSuchFieldException, IllegalAccessException {
        Field formsCacheField = FormioClient.class.getDeclaredField("FORMS_CACHE");
        Field submitButtonsCacheField = FormioClient.class.getDeclaredField("SUBMISSION_PROCESSING_DECISIONS_CACHE");
        Field formResourcesDirCacheField = FormioClient.class.getDeclaredField("FORM_RESOURCES_DIR_CACHE");
        formsCacheField.setAccessible(true);
        submitButtonsCacheField.setAccessible(true);
        formResourcesDirCacheField.setAccessible(true);
        ((Map<String, JsonNode>) formsCacheField.get(FormioClient.class)).clear();
        ((Map<String, JsonNode>) submitButtonsCacheField.get(FormioClient.class)).clear();
        ((Map<String, JsonNode>) formResourcesDirCacheField.get(FormioClient.class)).clear();
    }

    @AfterClass
    public static void deleteTestTmpDir() throws IOException {
        FileUtils.deleteDirectory(TEST_FORMIO_TMP_DIR.toFile());
        FileUtils.deleteDirectory(Paths.get(System.getProperty("java.io.tmpdir"), ".formio").toFile());
    }

    @Test
    public void testGetFormWithData_NoDataPassed() throws Exception {
        String formKey = "forms/testForm.json";
        ObjectNode taskVariables = jsonMapper.createObjectNode();
        InputStream is = getClass().getClassLoader().getResourceAsStream(formKey);
        InputStream form = getClass().getClassLoader().getResourceAsStream(formKey);
        JsonNode formDefinition = jsonMapper.readTree(is);
        String formDefinitionJson = formDefinition.toString();
        JsonNode cleanupResult = jsonMapper.createObjectNode();
        JsonNode expected = formDefinition.deepCopy();
        ((ObjectNode) expected).set("data", cleanupResult);
        String customComponent1Name = "component.js";
        String customComponent2Name = "texteditor.js";
        String customComponent1RelativePath = Paths.get("custom-components", customComponent1Name).toString();
        String customComponent1FullPath = Paths.get(PUBLIC_RESOURCES_DIRECTORY, customComponent1RelativePath).toString();
        String customComponent2RelativePath = Paths.get("custom-components", customComponent2Name).toString();
        String customComponent2FullPath = Paths.get(PUBLIC_RESOURCES_DIRECTORY, customComponent2RelativePath).toString();
        String formResourcesDirPath = Paths.get(TEST_FORMIO_TMP_DIR.toString(), String.valueOf(formDefinitionJson.hashCode())).toString();
        String formIoBundle = toFormIoBundle(CLEANUP_OPERATION_NAME, formDefinitionJson, taskVariables.toString(), formResourcesDirPath);

        when(resourceLoader.getResource(formKey)).thenReturn(form);
        when(resourceLoader.listResourceNames()).thenReturn(asList(customComponent1RelativePath, customComponent2RelativePath));
        when(resourceLoader.getResource(customComponent1RelativePath)).thenReturn(new FileInputStream(getFile(customComponent1FullPath)));
        when(resourceLoader.getResource(customComponent2RelativePath)).thenReturn(new FileInputStream(getFile(customComponent2FullPath)));
        when(NODEJS_EXECUTOR.execute(formIoBundle)).thenReturn(cleanupResult.toString());

        String actual = formioClient.getFormWithData(formKey, taskVariables, resourceLoader);

        assertEquals(expected.toString(), actual);
        File formResourcesDir = new File(formResourcesDirPath);
        assertTrue(formResourcesDir.exists());
        File customComponentsDir = formResourcesDir.listFiles()[0];
        assertEquals("custom-components", customComponentsDir.getName());
        assertEquals(2, customComponentsDir.listFiles().length);
        FileUtils.deleteDirectory(formResourcesDir);
    }

    @Test
    //TODO Fix
    // Instead of exact value of formIoBundle anyString() is passed into 'NODEJS_EXECUTOR.execute()' scenario
    // because of the problem with file conversion
    public void testGetFormWithData_ExistentDataPassed() throws Exception {
        String formKey = "forms/testForm.json";
        ObjectNode taskData = jsonMapper.createObjectNode();
        ArrayNode taskFile = getCamundaFileData(formKey, "application/json");
        taskData.set("testFile", taskFile);
        ObjectNode container = jsonMapper.createObjectNode();
        container.put("containerField", "123");
        taskData.set("container", container);
        ObjectNode cleanupResult = jsonMapper.createObjectNode();
        cleanupResult.set("testFile", getFormioFileData(formKey, "application/json"));
        ObjectNode cleanupResultContainer = jsonMapper.createObjectNode();
        cleanupResultContainer.put("containerField", "123");
        cleanupResult.set("container", cleanupResultContainer);
        InputStream is = getClass().getClassLoader().getResourceAsStream(formKey);
        InputStream form = getClass().getClassLoader().getResourceAsStream(formKey);
        JsonNode formDefinition = jsonMapper.readTree(is);
        JsonNode expected = formDefinition.deepCopy();
        String formDefinitionJson = formDefinition.toString();
        ((ObjectNode) expected).set("data", cleanupResult);
        String customComponent1Name = "component.js";
        String customComponent2Name = "texteditor.js";
        String customComponent1RelativePath = Paths.get("custom-components", customComponent1Name).toString();
        String customComponent1FullPath = Paths.get(PUBLIC_RESOURCES_DIRECTORY, customComponent1RelativePath).toString();
        String customComponent2RelativePath = Paths.get("custom-components", customComponent2Name).toString();
        String customComponent2FullPath = Paths.get(PUBLIC_RESOURCES_DIRECTORY, customComponent2RelativePath).toString();
        String formResourcesDirPath = Paths.get(TEST_FORMIO_TMP_DIR.toString(), String.valueOf(formDefinitionJson.hashCode())).toString();
//        String formIoBundle = toFormIoBundle(CLEANUP_OPERATION_NAME, formDefinition.toString(), submissionData.toString(), formResourcesDirPath);

        when(resourceLoader.getResource(formKey)).thenReturn(form);
        when(resourceLoader.listResourceNames()).thenReturn(asList(customComponent1RelativePath, customComponent2RelativePath));
        when(resourceLoader.getResource(customComponent1RelativePath)).thenReturn(new FileInputStream(getFile(customComponent1FullPath)));
        when(resourceLoader.getResource(customComponent2RelativePath)).thenReturn(new FileInputStream(getFile(customComponent2FullPath)));
        when(NODEJS_EXECUTOR.execute(anyString())).thenReturn(cleanupResult.toString());

        String actual = formioClient.getFormWithData(formKey, taskData, resourceLoader);
        JsonNode actualJson = jsonMapper.readTree(actual);

        assertTrue(actualJson.has("data"));
        JsonNode actualData = actualJson.get("data");
        assertTrue(actualData.has("container"));
        assertTrue(actualData.has("testFile"));
        assertFalse(actualData.at("/testFile/0/name").isMissingNode());
        assertEquals(formKey, actualData.at("/testFile/0/name").asText());
        File formResourcesDir = new File(formResourcesDirPath);
        assertTrue(formResourcesDir.exists());
        File customComponentsDir = formResourcesDir.listFiles()[0];
        assertEquals("custom-components", customComponentsDir.getName());
        assertEquals(2, customComponentsDir.listFiles().length);
        FileUtils.deleteDirectory(formResourcesDir);
    }

    @Test
    //TODO Fix
    // Instead of exact value of formIoBundle anyString() is passed into 'NODEJS_EXECUTOR.execute()' scenario
    // because of the problem with file conversion
    public void testGetFormWithData_NonexistentDataPassed() throws Exception {
        String formKey = "forms/testForm.json";
        ObjectNode taskVariables = jsonMapper.createObjectNode();
        taskVariables.set("testFile", getCamundaFileData(formKey, "application/json"));
        InputStream is = getClass().getClassLoader().getResourceAsStream(formKey);
        InputStream form = getClass().getClassLoader().getResourceAsStream(formKey);
        JsonNode formDefinition = jsonMapper.readTree(is);
        String formDefinitionJson = formDefinition.toString();
        JsonNode cleanupResult = jsonMapper.createObjectNode();
        JsonNode expected = formDefinition.deepCopy();
        ((ObjectNode) expected).set("data", cleanupResult);
        String customComponent1Name = "component.js";
        String customComponent2Name = "texteditor.js";
        String customComponent1RelativePath = Paths.get("custom-components", customComponent1Name).toString();
        String customComponent1FullPath = Paths.get(PUBLIC_RESOURCES_DIRECTORY, customComponent1RelativePath).toString();
        String customComponent2RelativePath = Paths.get("custom-components", customComponent2Name).toString();
        String customComponent2FullPath = Paths.get(PUBLIC_RESOURCES_DIRECTORY, customComponent2RelativePath).toString();
        String formResourcesDirPath = Paths.get(TEST_FORMIO_TMP_DIR.toString(), String.valueOf(formDefinitionJson.hashCode())).toString();
//        String formIoBundle = toFormIoBundle(CLEANUP_OPERATION_NAME, formDefinition.toString(), submissionData.toString(), formResourcesDirPath);

        when(resourceLoader.getResource(formKey)).thenReturn(form);
        when(resourceLoader.listResourceNames()).thenReturn(asList(customComponent1RelativePath, customComponent2RelativePath));
        when(resourceLoader.getResource(customComponent1RelativePath)).thenReturn(new FileInputStream(getFile(customComponent1FullPath)));
        when(resourceLoader.getResource(customComponent2RelativePath)).thenReturn(new FileInputStream(getFile(customComponent2FullPath)));
        when(NODEJS_EXECUTOR.execute(anyString())).thenReturn(cleanupResult.toString());

        String actual = formioClient.getFormWithData(formKey, taskVariables, resourceLoader);

        assertEquals(expected.toString(), actual);
        File formResourcesDir = new File(formResourcesDirPath);
        assertTrue(formResourcesDir.exists());
        File customComponentsDir = formResourcesDir.listFiles()[0];
        assertEquals("custom-components", customComponentsDir.getName());
        assertEquals(2, customComponentsDir.listFiles().length);
        FileUtils.deleteDirectory(formResourcesDir);
    }

    @Test
    public void dryValidationAndCleanupTest_NoDataPassed() throws Exception {
        String formKey = "forms/testForm.json";
        ObjectNode submittedVariables = jsonMapper.createObjectNode();
        ObjectNode taskVariables = jsonMapper.createObjectNode();
        String formVariables = "{}";
        InputStream is = getClass().getClassLoader().getResourceAsStream(formKey);
        InputStream form = getClass().getClassLoader().getResourceAsStream(formKey);
        JsonNode formDefinition = jsonMapper.readTree(is);
        JsonNode validationResult = jsonMapper.createObjectNode();
        JsonNode expected = formDefinition.deepCopy();
        String formDefinitionJson = formDefinition.toString();
        ((ObjectNode) expected).set("data", validationResult);
        String customComponent1Name = "component.js";
        String customComponent2Name = "texteditor.js";
        String customComponent1RelativePath = Paths.get("custom-components", customComponent1Name).toString();
        String customComponent1FullPath = Paths.get(PUBLIC_RESOURCES_DIRECTORY, customComponent1RelativePath).toString();
        String customComponent2RelativePath = Paths.get("custom-components", customComponent2Name).toString();
        String customComponent2FullPath = Paths.get(PUBLIC_RESOURCES_DIRECTORY, customComponent2RelativePath).toString();
        String formResourcesDirPath = Paths.get(TEST_FORMIO_TMP_DIR.toString(), String.valueOf(formDefinitionJson.hashCode())).toString();
        String formIoBundle = toFormIoBundle(VALIDATION_OPERATION_NAME, formDefinitionJson, formVariables, formResourcesDirPath);

        when(resourceLoader.getResource(formKey)).thenReturn(form);
        when(resourceLoader.listResourceNames()).thenReturn(asList(customComponent1RelativePath, customComponent2RelativePath));
        when(resourceLoader.getResource(customComponent1RelativePath)).thenReturn(new FileInputStream(getFile(customComponent1FullPath)));
        when(resourceLoader.getResource(customComponent2RelativePath)).thenReturn(new FileInputStream(getFile(customComponent2FullPath)));
        when(NODEJS_EXECUTOR.execute(formIoBundle)).thenReturn(validationResult.toString());

        String actual = formioClient.dryValidationAndCleanup(formKey, submittedVariables, taskVariables, resourceLoader);

        assertEquals(jsonMapper.writeValueAsString(taskVariables), actual);
        File formResourcesDir = new File(formResourcesDirPath);
        assertTrue(formResourcesDir.exists());
        File customComponentsDir = formResourcesDir.listFiles()[0];
        assertEquals("custom-components", customComponentsDir.getName());
        assertEquals(2, customComponentsDir.listFiles().length);
        FileUtils.deleteDirectory(formResourcesDir);
    }

    @Test
    public void dryValidationAndCleanupTest_ValidDataPassed() throws Exception {
        String formKey = "forms/test.json";
        ObjectNode taskVariables = jsonMapper.createObjectNode();
        ObjectNode submittedVariables = jsonMapper.createObjectNode();
        submittedVariables.put("text", "123");
        ObjectNode formVariables = jsonMapper.createObjectNode();
        formVariables.setAll(submittedVariables);
        InputStream is = getClass().getClassLoader().getResourceAsStream(formKey);
        InputStream form = getClass().getClassLoader().getResourceAsStream(formKey);
        JsonNode formDefinition = jsonMapper.readTree(is);
        String formDefinitionJson = formDefinition.toString();
        ObjectNode validationResult = jsonMapper.createObjectNode();
        ObjectNode validationResultWrapper = validationResult.putObject("data");
        validationResultWrapper.setAll(submittedVariables);
        JsonNode expected = formDefinition.deepCopy();
        ((ObjectNode) expected).set("data", jsonMapper.valueToTree(submittedVariables));
        String customComponent1Name = "component.js";
        String customComponent2Name = "texteditor.js";
        String customComponent1RelativePath = Paths.get("custom-components", customComponent1Name).toString();
        String customComponent1FullPath = Paths.get(PUBLIC_RESOURCES_DIRECTORY, customComponent1RelativePath).toString();
        String customComponent2RelativePath = Paths.get("custom-components", customComponent2Name).toString();
        String customComponent2FullPath = Paths.get(PUBLIC_RESOURCES_DIRECTORY, customComponent2RelativePath).toString();
        String formResourcesDirPath = Paths.get(TEST_FORMIO_TMP_DIR.toString(), String.valueOf(formDefinitionJson.hashCode())).toString();
        String formIoBundle = toFormIoBundle(VALIDATION_OPERATION_NAME, formDefinitionJson, formVariables.toString(), formResourcesDirPath);

        when(resourceLoader.getResource(formKey)).thenReturn(form);
        when(resourceLoader.listResourceNames()).thenReturn(asList(customComponent1RelativePath, customComponent2RelativePath));
        when(resourceLoader.getResource(customComponent1RelativePath)).thenReturn(new FileInputStream(getFile(customComponent1FullPath)));
        when(resourceLoader.getResource(customComponent2RelativePath)).thenReturn(new FileInputStream(getFile(customComponent2FullPath)));
        when(NODEJS_EXECUTOR.execute(formIoBundle)).thenReturn(validationResult.toString());

        String actual = formioClient.dryValidationAndCleanup(formKey, submittedVariables, taskVariables, resourceLoader);

        assertEquals(jsonMapper.writeValueAsString(submittedVariables), actual);
        File formResourcesDir = new File(formResourcesDirPath);
        assertTrue(formResourcesDir.exists());
        File customComponentsDir = formResourcesDir.listFiles()[0];
        assertEquals("custom-components", customComponentsDir.getName());
        assertEquals(2, customComponentsDir.listFiles().length);
        FileUtils.deleteDirectory(formResourcesDir);
    }

    @Test
    public void dryValidationAndCleanupTest_ValidDataWithChangedReadOnlyVariablePassed() throws Exception {
        String formKey = "forms/test.json";
        ObjectNode taskVariables = jsonMapper.createObjectNode();
        String textVarName = "text";
        String readOnlyVarName = "readOnly";
        taskVariables.put(readOnlyVarName, "test");
        ObjectNode submittedVariables = jsonMapper.createObjectNode();
        submittedVariables.put(readOnlyVarName, "test2");
        submittedVariables.put(textVarName, "123");
        ObjectNode expected = jsonMapper.createObjectNode();
        expected.put(textVarName, "123");
        expected.put(readOnlyVarName, "test");
        ObjectNode validationResult = jsonMapper.createObjectNode();
        ObjectNode validationResultWrapper = validationResult.putObject("data");
        validationResultWrapper.set(textVarName, submittedVariables.get(textVarName));
        validationResultWrapper.set(readOnlyVarName, taskVariables.get(readOnlyVarName));
        ObjectNode data = jsonMapper.createObjectNode();
        data.setAll(expected);
        InputStream is = getClass().getClassLoader().getResourceAsStream(formKey);
        InputStream form = getClass().getClassLoader().getResourceAsStream(formKey);
        JsonNode formDefinition = jsonMapper.readTree(is);
        String formDefinitionJson = formDefinition.toString();
        String customComponent1Name = "component.js";
        String customComponent2Name = "texteditor.js";
        String customComponent1RelativePath = Paths.get("custom-components", customComponent1Name).toString();
        String customComponent1FullPath = Paths.get(PUBLIC_RESOURCES_DIRECTORY, customComponent1RelativePath).toString();
        String customComponent2RelativePath = Paths.get("custom-components", customComponent2Name).toString();
        String customComponent2FullPath = Paths.get(PUBLIC_RESOURCES_DIRECTORY, customComponent2RelativePath).toString();
        String formResourcesDirPath = Paths.get(TEST_FORMIO_TMP_DIR.toString(), String.valueOf(formDefinitionJson.hashCode())).toString();
        String formIoBundle = toFormIoBundle(VALIDATION_OPERATION_NAME, formDefinition.toString(), data.toString(), formResourcesDirPath);

        when(resourceLoader.getResource(formKey)).thenReturn(form);
        when(resourceLoader.listResourceNames()).thenReturn(asList(customComponent1RelativePath, customComponent2RelativePath));
        when(resourceLoader.getResource(customComponent1RelativePath)).thenReturn(new FileInputStream(getFile(customComponent1FullPath)));
        when(resourceLoader.getResource(customComponent2RelativePath)).thenReturn(new FileInputStream(getFile(customComponent2FullPath)));
        when(NODEJS_EXECUTOR.execute(formIoBundle)).thenReturn(validationResult.toString());

        String actual = formioClient.dryValidationAndCleanup(formKey, submittedVariables, taskVariables, resourceLoader);

        assertEquals(expected, jsonMapper.readTree(actual));
        File formResourcesDir = new File(formResourcesDirPath);
        assertTrue(formResourcesDir.exists());
        File customComponentsDir = formResourcesDir.listFiles()[0];
        assertEquals("custom-components", customComponentsDir.getName());
        assertEquals(2, customComponentsDir.listFiles().length);
        FileUtils.deleteDirectory(formResourcesDir);
    }

    @Test(expected = FormValidationException.class)
    public void dryValidationAndCleanupTest_InvalidDataPassed() throws Exception {
        String formKey = "forms/testForm.json";
        ObjectNode currentVariables = jsonMapper.createObjectNode();
        ObjectNode submittedVariables = jsonMapper.createObjectNode();
        ObjectNode formVariables = jsonMapper.createObjectNode();
        InputStream is = getClass().getClassLoader().getResourceAsStream(formKey);
        InputStream form = getClass().getClassLoader().getResourceAsStream(formKey);
        JsonNode formDefinition = jsonMapper.readTree(is);
        JsonNode validationResult = jsonMapper.createObjectNode();
        JsonNode expected = formDefinition.deepCopy();
        String formDefinitionJson = formDefinition.toString();
        ((ObjectNode) expected).set("data", validationResult);
        String customComponent1Name = "component.js";
        String customComponent2Name = "texteditor.js";
        String customComponent1RelativePath = Paths.get("custom-components", customComponent1Name).toString();
        String customComponent1FullPath = Paths.get(PUBLIC_RESOURCES_DIRECTORY, customComponent1RelativePath).toString();
        String customComponent2RelativePath = Paths.get("custom-components", customComponent2Name).toString();
        String customComponent2FullPath = Paths.get(PUBLIC_RESOURCES_DIRECTORY, customComponent2RelativePath).toString();
        String formResourcesDirPath = Paths.get(TEST_FORMIO_TMP_DIR.toString(), String.valueOf(formDefinitionJson.hashCode())).toString();
        String formIoBundle = toFormIoBundle(VALIDATION_OPERATION_NAME, formDefinitionJson, formVariables.toString(), formResourcesDirPath);

        when(resourceLoader.getResource(formKey)).thenReturn(form);
        when(resourceLoader.listResourceNames()).thenReturn(asList(customComponent1RelativePath, customComponent2RelativePath));
        when(resourceLoader.getResource(customComponent1RelativePath)).thenReturn(new FileInputStream(getFile(customComponent1FullPath)));
        when(resourceLoader.getResource(customComponent2RelativePath)).thenReturn(new FileInputStream(getFile(customComponent2FullPath)));
        when(NODEJS_EXECUTOR.execute(formIoBundle)).thenThrow(FormValidationException.class);

        String actual = formioClient.dryValidationAndCleanup(formKey, submittedVariables, currentVariables, resourceLoader);

        assertEquals(jsonMapper.writeValueAsString(submittedVariables), actual);
        File formResourcesDir = new File(formResourcesDirPath);
        assertTrue(formResourcesDir.exists());
        File customComponentsDir = formResourcesDir.listFiles()[0];
        assertEquals("custom-components", customComponentsDir.getName());
        assertEquals(2, customComponentsDir.listFiles().length);
        FileUtils.deleteDirectory(formResourcesDir);
    }

    @Test
    public void testGetFormVariableNames() throws URISyntaxException, FileNotFoundException {
        String formKey = "forms/formWithMultilevelVariable.json";
        FileInputStream form = new FileInputStream(getFile(PUBLIC_RESOURCES_DIRECTORY + "/" + formKey));
        List<String> expected = asList("level1", "submit");

        when(resourceLoader.getResource(formKey)).thenReturn(form);

        List<String> actual = formioClient.getFormVariableNames(formKey, resourceLoader);

        ListAssert.assertEquals(expected, actual);
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

    private static <T> void setFinalField(Class<T> target, String name, Object value)
            throws NoSuchFieldException, IllegalAccessException {
        Field field = target.getDeclaredField(name);
        field.setAccessible(true);
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        setField(target, field, value);
    }

    private ArrayNode getFormioFileData(String filename, String mimeType) {
        ArrayNode files = jsonMapper.createArrayNode();
        ObjectNode file = jsonMapper.createObjectNode();
        file.put("storage", "base64");
        file.put("name", filename);
        file.put("originalName", filename);
        byte[] data;
        try {
            data = new FileInputStream(getFile(filename)).readAllBytes();
        } catch (IOException | URISyntaxException e) {
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
            data = new FileInputStream(getFile(filename)).readAllBytes();
        } catch (IOException | URISyntaxException e) {
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

    private String toFormIoBundle(String operation, String formDefinition, String data, String customComponentsDir)
            throws IOException {
        ObjectNode command = jsonMapper.createObjectNode();
        command.set("form", jsonMapper.readTree(formDefinition));
        command.set("data", jsonMapper.readTree(data));
        command.put("operation", operation);
        command.put("resourcePath", toSafePath(customComponentsDir));
        return command.toString();
    }

    private String toSafePath(String customComponentsDir) {
        return customComponentsDir.replaceAll("\\\\", "\\\\\\\\");
    }

}
