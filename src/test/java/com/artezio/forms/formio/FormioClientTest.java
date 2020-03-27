package com.artezio.forms.formio;

import com.artezio.forms.converters.FileConverter;
import com.artezio.forms.formio.exceptions.FormValidationException;
import com.artezio.forms.formio.nodejs.NodeJsExecutor;
import com.artezio.forms.resources.ResourceLoader;
import com.artezio.forms.storages.FileStorage;
import com.artezio.forms.storages.FileStorageEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import junitx.framework.ListAssert;
import org.apache.commons.io.FileUtils;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

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
import static org.mockito.Mockito.*;
import static org.mockito.internal.util.reflection.FieldSetter.setField;

@RunWith(MockitoJUnitRunner.class)
public class FormioClientTest {

    private static final String VALIDATION_OPERATION_NAME = "validate";
    private static final String CLEANUP_OPERATION_NAME = "cleanup";
    private static final Path TEST_FORMIO_TMP_DIR = Paths.get(System.getProperty("java.io.tmpdir"), ".test-formio");
    private static final NodeJsExecutor NODEJS_EXECUTOR = mock(NodeJsExecutor.class);
    private static final String PUBLIC_RESOURCES_DIRECTORY = "public";

    @Mock
    private ResourceLoader resourceLoader;
    @Mock
    private FileConverter fileConverter;
    @Mock
    private FileStorage fileStorage;
    @InjectMocks
    private FormioClient formioClient;
    private ObjectMapper jsonMapper = new ObjectMapper();

    @BeforeClass
    public static void prepareStaticFinalFields() throws NoSuchFieldException, IllegalAccessException {
        setFinalField(FormioClient.class,"FORMIO_TEMP_DIR", TEST_FORMIO_TMP_DIR);
        setFinalField(FormioClient.class,"NODEJS_EXECUTOR", NODEJS_EXECUTOR);
    }

    @Before
    public void setUp() throws Exception {
        formioClient = new FormioClient(fileConverter);
        FileConverter fileConverter = new DefaultFileConverter();
        setField(formioClient, FormioClient.class.getDeclaredField("fileConverter"), fileConverter);
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
        String formKey = "forms/formWithFile.json";
        ObjectNode currentVariables = jsonMapper.createObjectNode();
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
        String formIoBundle = toFormIoBundle(CLEANUP_OPERATION_NAME, formDefinitionJson, currentVariables.toString(), formResourcesDirPath);

        when(resourceLoader.getResource(formKey)).thenReturn(form);
        when(resourceLoader.listResourceNames()).thenReturn(asList(customComponent1RelativePath, customComponent2RelativePath));
        when(resourceLoader.getResource(customComponent1RelativePath)).thenReturn(new FileInputStream(getFile(customComponent1FullPath)));
        when(resourceLoader.getResource(customComponent2RelativePath)).thenReturn(new FileInputStream(getFile(customComponent2FullPath)));
        when(NODEJS_EXECUTOR.execute(formIoBundle)).thenReturn(cleanupResult.toString());

        String actual = formioClient.getFormWithData(formKey, currentVariables, resourceLoader, fileStorage);

        assertEquals(expected.toString(), actual);
        File formResourcesDir = new File(formResourcesDirPath);
        assertTrue(formResourcesDir.exists());
        File customComponentsDir = formResourcesDir.listFiles()[0];
        assertEquals("custom-components", customComponentsDir.getName());
        assertEquals(2, customComponentsDir.listFiles().length);
        FileUtils.deleteDirectory(formResourcesDir);
    }

    @Test
    //TODO //Replace anyString() with formioBundle
    public void testGetFormWithData_DataWithFilePassed() throws Exception {
        String formKey = "forms/formWithFile.json";
        ObjectNode currentVariables = jsonMapper.createObjectNode();
        ArrayNode fileVariable = currentVariables.putArray("testFile");
        ObjectNode formioFile = jsonMapper.createObjectNode();
        formioFile.put("type", "text/plain");
        formioFile.put("name", "test.txt");
        formioFile.put("originalName", "test.txt");
        formioFile.put("size", 4);
        formioFile.put("url", "data:text/plain;base64,ZGF0YQ==");
        formioFile.put("storage", "base64");
        ObjectNode file = fileVariable.addObject();
        file.setAll(formioFile);
        ObjectNode currentVariablesWithFormioFiles = currentVariables.deepCopy();
        ((ArrayNode) currentVariablesWithFormioFiles.get("testFile")).set(0, formioFile);
        ((ObjectNode) currentVariablesWithFormioFiles.get("testFile").get(0)).remove("url");
        InputStream is = getClass().getClassLoader().getResourceAsStream(formKey);
        InputStream form = getClass().getClassLoader().getResourceAsStream(formKey);
        JsonNode formDefinition = jsonMapper.readTree(is);
        String formDefinitionJson = formDefinition.toString();
        ObjectNode cleanupResult = jsonMapper.createObjectNode();
        cleanupResult.putArray("testFile").addObject().setAll(formioFile);
        JsonNode expected = formDefinition.deepCopy();
        ((ObjectNode) expected).set("data", cleanupResult.deepCopy());
        String customComponent1Name = "component.js";
        String customComponent2Name = "texteditor.js";
        String customComponent1RelativePath = Paths.get("custom-components", customComponent1Name).toString();
        String customComponent1FullPath = Paths.get(PUBLIC_RESOURCES_DIRECTORY, customComponent1RelativePath).toString();
        String customComponent2RelativePath = Paths.get("custom-components", customComponent2Name).toString();
        String customComponent2FullPath = Paths.get(PUBLIC_RESOURCES_DIRECTORY, customComponent2RelativePath).toString();
        String formResourcesDirPath = Paths.get(TEST_FORMIO_TMP_DIR.toString(), String.valueOf(formDefinitionJson.hashCode())).toString();
        String formIoBundle = toFormIoBundle(CLEANUP_OPERATION_NAME, formDefinitionJson, currentVariablesWithFormioFiles.toString(), formResourcesDirPath);

        when(resourceLoader.getResource(formKey)).thenReturn(form);
        when(resourceLoader.listResourceNames()).thenReturn(asList(customComponent1RelativePath, customComponent2RelativePath));
        when(resourceLoader.getResource(customComponent1RelativePath)).thenReturn(new FileInputStream(getFile(customComponent1FullPath)));
        when(resourceLoader.getResource(customComponent2RelativePath)).thenReturn(new FileInputStream(getFile(customComponent2FullPath)));
        when(NODEJS_EXECUTOR.execute(anyString())).thenReturn(cleanupResult.toString());

        String actual = formioClient.getFormWithData(formKey, currentVariables, resourceLoader, fileStorage);

        assertEquals(expected.toString(), actual);
        File formResourcesDir = new File(formResourcesDirPath);
        assertTrue(formResourcesDir.exists());
        File customComponentsDir = formResourcesDir.listFiles()[0];
        assertEquals("custom-components", customComponentsDir.getName());
        assertEquals(2, customComponentsDir.listFiles().length);
        FileUtils.deleteDirectory(formResourcesDir);
    }

    @Test
    //TODO Replace anyString() with formioBundle
    public void testGetFormWithData_ExistentDataPassed() throws Exception {
        String formKey = "forms/formWithFile.json";
        ObjectNode currentVariables = jsonMapper.createObjectNode();
        ArrayNode file = getFormioFileData(formKey, "application/json");
        currentVariables.set("testFile", file);
        ObjectNode container = jsonMapper.createObjectNode();
        container.put("containerField", "123");
        currentVariables.set("container", container);
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
        String formIoBundle = toFormIoBundle(CLEANUP_OPERATION_NAME, formDefinition.toString(), currentVariables.toString(), formResourcesDirPath);

        when(resourceLoader.getResource(formKey)).thenReturn(form);
        when(resourceLoader.listResourceNames()).thenReturn(asList(customComponent1RelativePath, customComponent2RelativePath));
        when(resourceLoader.getResource(customComponent1RelativePath)).thenReturn(new FileInputStream(getFile(customComponent1FullPath)));
        when(resourceLoader.getResource(customComponent2RelativePath)).thenReturn(new FileInputStream(getFile(customComponent2FullPath)));
//        when(fileConverter.toFormioFile(file)).thenReturn(file);
        when(NODEJS_EXECUTOR.execute(anyString())).thenReturn(cleanupResult.toString());

        String actual = formioClient.getFormWithData(formKey, currentVariables, resourceLoader, fileStorage);
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
    //TODO Replace anyString() with formioBundle
    public void testGetFormWithData_NonexistentDataPassed() throws Exception {
        String formKey = "forms/formWithFile.json";
        ObjectNode currentVariables = jsonMapper.createObjectNode();
        currentVariables.set("testFile", getFormioFileData(formKey, "application/json"));
        ((ObjectNode) currentVariables.get("testFile").get(0)).remove("url");
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
        String formIoBundle = toFormIoBundle(CLEANUP_OPERATION_NAME, formDefinition.toString(), currentVariables.toString(), formResourcesDirPath);

        when(resourceLoader.getResource(formKey)).thenReturn(form);
        when(resourceLoader.listResourceNames()).thenReturn(asList(customComponent1RelativePath, customComponent2RelativePath));
        when(resourceLoader.getResource(customComponent1RelativePath)).thenReturn(new FileInputStream(getFile(customComponent1FullPath)));
        when(resourceLoader.getResource(customComponent2RelativePath)).thenReturn(new FileInputStream(getFile(customComponent2FullPath)));
        when(NODEJS_EXECUTOR.execute(anyString())).thenReturn(cleanupResult.toString());

        String actual = formioClient.getFormWithData(formKey, currentVariables, resourceLoader, fileStorage);

        assertEquals(expected.toString(), actual);
        File formResourcesDir = new File(formResourcesDirPath);
        assertTrue(formResourcesDir.exists());
        File customComponentsDir = formResourcesDir.listFiles()[0];
        assertEquals("custom-components", customComponentsDir.getName());
        assertEquals(2, customComponentsDir.listFiles().length);
        FileUtils.deleteDirectory(formResourcesDir);
    }

    @Test
    public void testDryValidationAndCleanup_NoDataPassed() throws Exception {
        String formKey = "forms/formWithFile.json";
        ObjectNode submittedVariables = jsonMapper.createObjectNode();
        ObjectNode currentVariables = jsonMapper.createObjectNode();
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

        String actual = formioClient.dryValidationAndCleanup(formKey, submittedVariables, currentVariables, resourceLoader, fileStorage);

        assertEquals(jsonMapper.writeValueAsString(currentVariables), actual);
        File formResourcesDir = new File(formResourcesDirPath);
        assertTrue(formResourcesDir.exists());
        File customComponentsDir = formResourcesDir.listFiles()[0];
        assertEquals("custom-components", customComponentsDir.getName());
        assertEquals(2, customComponentsDir.listFiles().length);
        FileUtils.deleteDirectory(formResourcesDir);
    }

    @Test
    public void testDryValidationAndCleanup_ValidDataPassed() throws Exception {
        String formKey = "forms/test.json";
        ObjectNode currentVariables = jsonMapper.createObjectNode();
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

        String actual = formioClient.dryValidationAndCleanup(formKey, submittedVariables, currentVariables, resourceLoader, fileStorage);

        assertEquals(jsonMapper.writeValueAsString(submittedVariables), actual);
        File formResourcesDir = new File(formResourcesDirPath);
        assertTrue(formResourcesDir.exists());
        File customComponentsDir = formResourcesDir.listFiles()[0];
        assertEquals("custom-components", customComponentsDir.getName());
        assertEquals(2, customComponentsDir.listFiles().length);
        FileUtils.deleteDirectory(formResourcesDir);
    }

    @Test
    public void testDryValidationAndCleanup_ValidDataWithChangedReadOnlyVariablePassed() throws Exception {
        String formKey = "forms/test.json";
        ObjectNode currentVariables = jsonMapper.createObjectNode();
        String textVarName = "text";
        String readOnlyVarName = "readOnly";
        currentVariables.put(readOnlyVarName, "test");
        ObjectNode submittedVariables = jsonMapper.createObjectNode();
        submittedVariables.put(readOnlyVarName, "test2");
        submittedVariables.put(textVarName, "123");
        ObjectNode expected = jsonMapper.createObjectNode();
        expected.put(textVarName, "123");
        expected.put(readOnlyVarName, "test");
        ObjectNode validationResult = jsonMapper.createObjectNode();
        ObjectNode validationResultWrapper = validationResult.putObject("data");
        validationResultWrapper.set(textVarName, submittedVariables.get(textVarName));
        validationResultWrapper.set(readOnlyVarName, currentVariables.get(readOnlyVarName));
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

        String actual = formioClient.dryValidationAndCleanup(formKey, submittedVariables, currentVariables, resourceLoader, fileStorage);

        assertEquals(expected, jsonMapper.readTree(actual));
        File formResourcesDir = new File(formResourcesDirPath);
        assertTrue(formResourcesDir.exists());
        File customComponentsDir = formResourcesDir.listFiles()[0];
        assertEquals("custom-components", customComponentsDir.getName());
        assertEquals(2, customComponentsDir.listFiles().length);
        FileUtils.deleteDirectory(formResourcesDir);
    }

    @Test
    public void testDryValidationAndCleanup_DataWithFilePassed() throws Exception {
        String formKey = "forms/formWithFile.json";
        ObjectNode currentVariables = jsonMapper.createObjectNode();
        ObjectNode submittedVariables = jsonMapper.createObjectNode();
        ArrayNode fileVariable = submittedVariables.putArray("testFile");
        ObjectNode formioFile = fileVariable.addObject();
        formioFile.put("name", "test.txt");
        formioFile.put("originalName", "test.txt");
        formioFile.put("size", 4);
        formioFile.put("type", "text/plain");
        formioFile.put("storage", "base64");
        formioFile.put("url", "data:text/plain;base64,ZGF0YQ==");
        ObjectNode expectedFile = formioFile.deepCopy();
        ObjectNode formVariables = jsonMapper.createObjectNode();
        formVariables.putArray("testFile").addObject().setAll(formioFile.deepCopy());
        ObjectNode formVariablesWithoutFileUrls = formVariables.deepCopy();
        ((ObjectNode) formVariablesWithoutFileUrls.get("testFile").get(0)).remove("url");
        InputStream is = getClass().getClassLoader().getResourceAsStream(formKey);
        InputStream form = getClass().getClassLoader().getResourceAsStream(formKey);
        JsonNode formDefinition = jsonMapper.readTree(is);
        String formDefinitionJson = formDefinition.toString();
        ObjectNode validationResult = jsonMapper.createObjectNode();
        ArrayNode validationResultFile = validationResult.putObject("data").putArray("testFile");
        validationResultFile.addObject().setAll(formioFile);
        ((ObjectNode) validationResultFile.get(0)).remove("url");
        ObjectNode expected = jsonMapper.createObjectNode();
        expected.putArray("testFile").add(expectedFile);
        String customComponent1Name = "component.js";
        String customComponent2Name = "texteditor.js";
        String customComponent1RelativePath = Paths.get("custom-components", customComponent1Name).toString();
        String customComponent1FullPath = Paths.get(PUBLIC_RESOURCES_DIRECTORY, customComponent1RelativePath).toString();
        String customComponent2RelativePath = Paths.get("custom-components", customComponent2Name).toString();
        String customComponent2FullPath = Paths.get(PUBLIC_RESOURCES_DIRECTORY, customComponent2RelativePath).toString();
        String formResourcesDirPath = Paths.get(TEST_FORMIO_TMP_DIR.toString(), String.valueOf(formDefinitionJson.hashCode())).toString();
        String formIoBundle = toFormIoBundle(VALIDATION_OPERATION_NAME, formDefinitionJson, formVariablesWithoutFileUrls.toString(), formResourcesDirPath);
        String fileVariablePath = "testFile[0]";
        FormioFileToFileStorageEntityAdapter fileStorageEntity = new FormioFileToFileStorageEntityAdapter(fileVariablePath, formVariables.get("testFile").get(0));
        Map<String, FileStorageEntity> storage = new HashMap<>();

        when(resourceLoader.getResource(formKey)).thenReturn(form);
        when(resourceLoader.listResourceNames()).thenReturn(asList(customComponent1RelativePath, customComponent2RelativePath));
        when(resourceLoader.getResource(customComponent1RelativePath)).thenReturn(new FileInputStream(getFile(customComponent1FullPath)));
        when(resourceLoader.getResource(customComponent2RelativePath)).thenReturn(new FileInputStream(getFile(customComponent2FullPath)));
        doAnswer(answer -> {
            storage.put(((FileStorageEntity)answer.getArgument(0)).getId(), answer.getArgument(0));
            return null;
        }).when(fileStorage).store(any(FileStorageEntity.class));
        when(NODEJS_EXECUTOR.execute(formIoBundle)).thenReturn(validationResult.toString());

        String actual = formioClient.dryValidationAndCleanup(formKey, submittedVariables, currentVariables, resourceLoader, fileStorage);

        assertEquals(sortObject(expected), sortObject(jsonMapper.readTree(actual)));
        assertFalse(storage.isEmpty());
        assertFileStorageEntitiesEquals(fileStorageEntity, storage.get(fileVariablePath));
        File formResourcesDir = new File(formResourcesDirPath);
        assertTrue(formResourcesDir.exists());
        File customComponentsDir = formResourcesDir.listFiles()[0];
        assertEquals("custom-components", customComponentsDir.getName());
        assertEquals(2, customComponentsDir.listFiles().length);
        FileUtils.deleteDirectory(formResourcesDir);
    }

    @Test(expected = FormValidationException.class)
    public void testDryValidationAndCleanup_InvalidDataPassed() throws Exception {
        String formKey = "forms/formWithFile.json";
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

        String actual = formioClient.dryValidationAndCleanup(formKey, submittedVariables, currentVariables, resourceLoader, fileStorage);

        assertEquals(sortObject(submittedVariables), sortObject(jsonMapper.readTree(actual)));
        File formResourcesDir = new File(formResourcesDirPath);
        assertTrue(formResourcesDir.exists());
        File customComponentsDir = formResourcesDir.listFiles()[0];
        assertEquals("custom-components", customComponentsDir.getName());
        assertEquals(2, customComponentsDir.listFiles().length);
        FileUtils.deleteDirectory(formResourcesDir);
    }

    @Test
    public void testGetRootFormVariableNames() throws URISyntaxException, FileNotFoundException {
        String formKey = "forms/formWithMultilevelVariable.json";
        FileInputStream form = new FileInputStream(getFile(PUBLIC_RESOURCES_DIRECTORY + "/" + formKey));
        List<String> expected = asList("variable1", "variable2", "submit");

        when(resourceLoader.getResource(formKey)).thenReturn(form);

        List<String> actual = formioClient.getRootFormFieldNames(formKey, resourceLoader);

        ListAssert.assertEquals(expected, actual);
    }

    @Test
    public void testGetFormVariableNames() throws URISyntaxException, FileNotFoundException {
        String formKey = "forms/formWithMultilevelVariable.json";
        FileInputStream form = new FileInputStream(getFile(PUBLIC_RESOURCES_DIRECTORY + "/" + formKey));
        List<String> expected = asList("variable1", "variable1.variable11", "variable1.variable11.variable111",
                "variable1.variable11.variable112", "variable2", "variable2.variable21", "submit");

        when(resourceLoader.getResource(formKey)).thenReturn(form);

        List<String> actual = formioClient.getFormFieldPaths(formKey, resourceLoader);

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
    public void testShouldProcessSubmittedData_SubmissionStateIsSubmitted() throws IOException, URISyntaxException {
        String formKey = "forms/formWithState.json";
        String submissionState = "submitted";
        FileInputStream form = new FileInputStream(getFile(formKey));

        when(resourceLoader.getResource(formKey)).thenReturn(form);
        boolean actual = formioClient.shouldProcessSubmission(formKey, submissionState, resourceLoader);

        assertTrue(actual);
    }

    @Test
    public void testShouldProcessSubmittedData_SubmissionStateIsCanceled() throws IOException, URISyntaxException {
        String formKey = "forms/formWithState.json";
        String submissionState = "canceled";
        FileInputStream form = new FileInputStream(getFile(formKey));

        when(resourceLoader.getResource(formKey)).thenReturn(form);
        boolean actual = formioClient.shouldProcessSubmission(formKey, submissionState, resourceLoader);

        assertFalse(actual);
    }

    @Test
    public void testShouldProcessSubmittedData_SkipDataProcessingPropertyNotSet() throws IOException, URISyntaxException {
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

    private void assertFileStorageEntitiesEquals(FileStorageEntity entity1, FileStorageEntity entity2) throws IOException {
        assertEquals(entity1.getId(), entity2.getId());
        assertEquals(entity1.getName(), entity2.getName());
        assertEquals(entity1.getMimeType(), entity2.getMimeType());
        assertEquals(entity1.getStorage(), entity2.getStorage());
        assertEquals(entity1.getUrl(), entity2.getUrl());
        assertArrayEquals(entity1.getContent().readAllBytes(), entity2.getContent().readAllBytes());
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
        ObjectNode file = files.addObject();
        file.put("type", mimeType);
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
        file.put("storage", "base64");
        file.put("url", String.format("data:%s;base64,%s", mimeType, Base64.getMimeEncoder().encodeToString(data)));
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
