package com.artezio.forms.formio;

import com.artezio.forms.FormClient;
import com.artezio.forms.converters.FileConverter;
import com.artezio.forms.formio.exceptions.FormValidationException;
import com.artezio.forms.formio.nodejs.NodeJsExecutor;
import com.artezio.forms.resources.ResourceLoader;
import com.artezio.forms.storages.FileStorage;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.jayway.jsonpath.*;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import net.minidev.json.JSONArray;

import org.apache.commons.collections4.map.LRUMap;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap.SimpleEntry;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.stream.*;

import static java.lang.Boolean.TRUE;
import static java.util.Arrays.asList;

@Named
public class FormioClient implements FormClient {
    
    private static final int DEPLOYMENT_RESOURCES_CACHE_SIZE = Integer.parseInt(System.getProperty("DEPLOYMENT_RESOURCES_CACHE_SIZE", "100"));

    private static final Map<String, JSONArray> FILE_FIELDS_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> SUBMISSION_PROCESSING_DECISIONS_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String> FORM_RESOURCES_DIR_CACHE = new ConcurrentHashMap<>();
    
    private static final String VALIDATION_OPERATION_NAME = "validate";
    private static final String CLEANUP_OPERATION_NAME = "cleanup";
    private static final String GRID_NO_ROW_WRAPPING_PROPERTY = "noRowWrapping";
    private static final String NODEJS_SCRIPT_PATH = "formio-scripts/formio.js";
    
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setDefaultMergeable(false);
    private static final ParseContext JAYWAY_PARSER = JsonPath.using(Configuration.builder()
            .jsonProvider(new JacksonJsonNodeJsonProvider())
            .build());
    private static final Path FORMIO_TEMP_DIR;
    
    static {
        try {
            Path path = Paths.get(System.getProperty("java.io.tmpdir"), ".formio");
            FORMIO_TEMP_DIR = path.toFile().exists()
                    ? path
                    : Files.createDirectory(path);
        } catch (IOException e) {
            throw new RuntimeException("Error while creating formio temp directory", e);
        }
    }
    
    private static final int NODEJS_EXECUTOR_POOL_SIZE = Integer.parseInt(System.getProperty("NODEJS_EXECUTOR_POOL_SIZE", "100"));
    private static final Map<String, NodeJsExecutor> NODEJS_EXECUTORS = Collections.synchronizedMap(new LRUMap<>(NODEJS_EXECUTOR_POOL_SIZE));

    static NodeJsExecutor createNodeJsExecutor() {
        try (InputStream resource = FormioClient.class.getClassLoader().getResourceAsStream(NODEJS_SCRIPT_PATH)) {
            String formioScript = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
            return new NodeJsExecutor(formioScript);
        } catch (IOException ex) {
            throw new RuntimeException("Could not load script: '" + NODEJS_SCRIPT_PATH + "'", ex);
        }
    }

    private FileConverter fileConverter;
    private ResourceLoader defaultResourceLoader;

    @Inject
    public FormioClient(FileConverter fileConverter, ResourceLoader defaultResourceLoader) {
        this.fileConverter = fileConverter;
        this.defaultResourceLoader = defaultResourceLoader;
    }

    @Override
    public String getFormWithData(String formKey, ObjectNode currentVariables) {
        return getFormWithData(formKey, currentVariables, defaultResourceLoader, new FormioBase64FileStorage());
    }

    @Override
    public String getFormWithData(String formKey, ObjectNode currentVariables, ResourceLoader resourceLoader) {
        return getFormWithData(formKey, currentVariables, resourceLoader, new FormioBase64FileStorage());
    }

    @Override
    public String getFormWithData(String formKey, ObjectNode currentVariables, FileStorage fileStorage) {
        return getFormWithData(formKey, currentVariables, defaultResourceLoader, fileStorage);
    }

    @Override
    public String getFormWithData(String formKey, ObjectNode currentVariables, ResourceLoader resourceLoader, FileStorage fileStorage) {
        try {
            JsonNode formDefinition = getForm(formKey, resourceLoader);
            JsonNode cleanData = cleanUnusedData(formDefinition.toString(), currentVariables, resourceLoader, fileStorage);
            JsonNode data = wrapGridData(cleanData, formDefinition);
            ((ObjectNode) formDefinition).set("data", data);
            return formDefinition.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get form: '" + formKey + "'", e);
        }
    }

    @Override
    public boolean shouldProcessSubmission(String formKey, String submissionState) {
        return shouldProcessSubmission(formKey, submissionState, defaultResourceLoader);
    }

    @Override
    public boolean shouldProcessSubmission(String formKey, String submissionState, ResourceLoader resourceLoader) {
        JsonNode formDefinition = getForm(formKey, resourceLoader);
        String cacheKey = String.format("%s-%s", formDefinition.toString(), submissionState);
        return SUBMISSION_PROCESSING_DECISIONS_CACHE.computeIfAbsent(
                cacheKey,
                key -> shouldProcessSubmission(formDefinition, submissionState));
    }

    @Override
    public String dryValidationAndCleanup(String formKey, ObjectNode submittedVariables, ObjectNode currentVariables) {
        return dryValidationAndCleanup(formKey, submittedVariables, currentVariables, defaultResourceLoader, new FormioBase64FileStorage());
    }

    @Override
    public String dryValidationAndCleanup(String formKey, ObjectNode submittedVariables, ObjectNode currentVariables, FileStorage fileStorage) {
        return dryValidationAndCleanup(formKey, submittedVariables, currentVariables, defaultResourceLoader, fileStorage);
    }

    @Override
    public String dryValidationAndCleanup(String formKey, ObjectNode submittedVariables, ObjectNode currentVariables,
                                          ResourceLoader resourceLoader) {
        return dryValidationAndCleanup(formKey, submittedVariables, currentVariables, resourceLoader, new FormioBase64FileStorage());
    }

    @Override
    public String dryValidationAndCleanup(String formKey, ObjectNode submittedVariables, ObjectNode currentVariables,
                                          ResourceLoader resourceLoader, FileStorage fileStorage) {
        try {
            JsonNode formDefinition = getForm(formKey, resourceLoader);
            String formDefinitionJson = formDefinition.toString();
            String formResourcesDirPath = getFormResourcesDirPath(formDefinitionJson, resourceLoader);
            ObjectNode dataInUrlBuffer = JSON_MAPPER.createObjectNode();
            FileOperationExecutor fileOperationExecutor = new FileOperationExecutor(formDefinitionJson);
            currentVariables = fileOperationExecutor
                    .convertToFormioFile()
                    .execute(currentVariables);
            ObjectNode formVariables = (ObjectNode) getFormVariables(formDefinition, submittedVariables, currentVariables);
            formVariables = fileOperationExecutor
                    .extractFormioDataInUrl(dataInUrlBuffer)
                    .execute(formVariables);
            String formIoValidateCommand = getFormIoCommand(VALIDATION_OPERATION_NAME, formDefinitionJson, formVariables.toString(), formResourcesDirPath);
            String formIoValidateResult = executeNodeJS(resourceLoader, formIoValidateCommand);
            JsonNode validationResult = getDataFromScriptExecutionResult(formIoValidateResult, formDefinition);
            return fileOperationExecutor
                    .addFormioDataInUrl(dataInUrlBuffer)
                    .convertFromFormioFile()
                    .storeFile(fileStorage)
                    .execute(validationResult).toString();
        } catch (Exception ex) {
            throw new FormValidationException(ex);
        }
    }

    @Override
    public List<String> getRootFormFieldNames(String formKey) {
        return getRootFormFieldNames(formKey, defaultResourceLoader);
    }

    @Override
    public List<String> getRootFormFieldNames(String formKey, ResourceLoader resourceLoader) {
        JsonNode formDefinition = getForm(formKey, resourceLoader);
        return Optional.of(listChildComponents(formDefinition)).stream()
                .flatMap(Collection::stream)
                .filter(component -> component.path("input").asBoolean())
                .filter(component -> StringUtils.isNotBlank(component.path("key").asText()))
                .map(component -> component.get("key").asText())
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getFormFieldPaths(String formKey) {
        return getFormFieldPaths(formKey, defaultResourceLoader);
    }

    @Override
    public List<String> getFormFieldPaths(String formKey, ResourceLoader resourceLoader) {
        JsonNode formDefinition = getForm(formKey, resourceLoader);
        return Optional.of(listChildComponents(formDefinition)).stream()
                .flatMap(Collection::stream)
                .filter(component -> component.path("input").asBoolean())
                .filter(component -> StringUtils.isNotBlank(component.path("key").asText()))
                .map(this::getComponentTreeNames)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    private List<String> getComponentTreeNames(JsonNode component) {
        List<String> result = new ArrayList<>();
        String componentName = component.path("key").asText();
        result.add(componentName);
        if (!isContainerComponent(component) && !isArrayComponent(component))
            return result;
        Optional.of(listChildComponents(component)).stream()
                .flatMap(Collection::stream)
                .filter(childComponent -> childComponent.path("input").asBoolean())
                .filter(childComponent -> StringUtils.isNotBlank(childComponent.path("key").asText()))
                .map(this::getComponentTreeNames)
                .flatMap(Collection::stream)
                .forEach(childComponent -> result.add(String.format("%s.%s", componentName, childComponent)));
        return result;
    }

    private JsonNode getDataFromScriptExecutionResult(String scriptExecutionResult, JsonNode formDefinition) throws IOException {
        JsonNode json = JSON_MAPPER.readTree(scriptExecutionResult);
        json = json.has("data")
                ? json.get("data")
                : JSON_MAPPER.createObjectNode();
        return unwrapGridData(json, formDefinition);
    }

    private JsonNode getForm(String formKey, ResourceLoader resourceLoader) {
        try(InputStream resource = resourceLoader.getResource(formKey)) {
            return expandSubforms(JSON_MAPPER.readTree(resource), resourceLoader);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private JsonNode getSubform(String formKey, ResourceLoader resourceLoader) {
        formKey = formKey + ".json";
        return getForm(formKey, resourceLoader);
    }

    private JsonNode cleanUnusedData(String formDefinition, ObjectNode currentVariables, ResourceLoader resourceLoader,
                                     FileStorage fileStorage) throws Exception {
        FileOperationExecutor fileOperationExecutor = new FileOperationExecutor(formDefinition);
        currentVariables = fileOperationExecutor
                .convertToFormioFile()
                .addDownloadUrlPrefix(fileStorage)
                .execute(currentVariables);
        String formResourcesDirPath = getFormResourcesDirPath(formDefinition, resourceLoader);
        String formIoCleanupCommand = getFormIoCommand(CLEANUP_OPERATION_NAME, formDefinition, currentVariables.toString(), formResourcesDirPath);
        String formIoCleanupResult = executeNodeJS(resourceLoader, formIoCleanupCommand);
        return JSON_MAPPER.readTree(formIoCleanupResult);
    }

    private String executeNodeJS(ResourceLoader resourceLoader, String command) throws Exception {
        return NODEJS_EXECUTORS.computeIfAbsent(resourceLoader.getGroupId(), key -> createNodeJsExecutor()).execute(command);
    }

    String getFormIoCommand(String operation, String formDefinition, String data, String customComponentsDir)
            throws IOException {
        ObjectNode command = JSON_MAPPER.createObjectNode();
        command.set("form", JSON_MAPPER.readTree(formDefinition));
        command.set("data", JSON_MAPPER.readTree(data));
        command.put("operation", operation);
        command.put("resourcePath", toSafePath(customComponentsDir));
        return command.toString();
    }

    private String toSafePath(String customComponentsDir) {
        return customComponentsDir.replaceAll("\\\\", "\\\\\\\\");
    }

    protected JsonNode expandSubforms(JsonNode component, ResourceLoader resourceLoader) {
        Collector<JsonNode, ArrayNode, ArrayNode> arrayNodeCollector = Collector
                .of(JSON_MAPPER::createArrayNode, ArrayNode::add, ArrayNode::addAll);
        Function<JsonNode, JsonNode> expandSubformsFunction = getExpandSubformsFunction(resourceLoader);
        JsonNode childComponents = !component.isArray() ? getChildComponents(component) : component;
        JsonNode components = toStream(childComponents)
                .map(expandSubformsFunction)
                .collect(arrayNodeCollector);
        return !component.isArray()
                ? ((ObjectNode) component).set("components", components)
                : components;
    }

    private Function<JsonNode, JsonNode> getExpandSubformsFunction(ResourceLoader resourceLoader) {
        String[] componentsWithChildren = {"container", "tree", "datagrid", "editgrid", "well", "columns", "fieldset",
                "panel", "table", "tabs"};
        return component -> {
            if (hasTypeOf(component, componentsWithChildren) || component.isArray()) {
                return expandSubforms(component, resourceLoader);
            } else if (hasTypeOf(component, "form")) {
                return convertToContainer(component, resourceLoader);
            } else if (component.has("components")) {
                return expandSubforms(component, resourceLoader);
            } else {
                return component;
            }
        };
    }

    private JsonNode convertToContainer(JsonNode formDefinition, ResourceLoader resourceLoader) {
        String formKey = formDefinition.get("key").asText();
        JsonNode container = convertToContainer(formDefinition);
        JsonNode components = getSubform(formKey, resourceLoader).get("components").deepCopy();
        ((ObjectNode) container).put("type", "container");
        ((ObjectNode) container).put("tree", true);
        ((ObjectNode) container).replace("components", components);
        return container;
    }

    private JsonNode convertToContainer(JsonNode formDefinition) {
        List<String> formAttributes = asList("src", "reference", "form", "unique", "project", "path");
        Predicate<Map.Entry<String, JsonNode>> nonFormAttributesPredicate = field -> !formAttributes.contains(field.getKey());
        return JSON_MAPPER.valueToTree(toFieldStream(formDefinition)
                .filter(nonFormAttributesPredicate)
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));
    }

    private boolean shouldProcessSubmission(JsonNode form, String submissionState) {
        Filter saveStateComponentsFilter = Filter.filter((Criteria.where("action").eq("saveState").and("state").eq(submissionState)));
        return toStream(JAYWAY_PARSER.parse(form).read("$..components[?]", saveStateComponentsFilter))
                .map(component -> component.at("/properties/isSubmissionProcessed").asBoolean(true))
                .findFirst()
                .orElse(true);
    }

    protected JsonNode wrapGridData(JsonNode data, JsonNode definition) {
        if (data.isObject()) {
            return wrapGridDataInObject(data, definition);
        }
        if (data.isArray()) {
            return wrapGridDataInArray((ArrayNode) data, definition);
        }
        return data;
    }

    private JsonNode wrapGridDataInObject(JsonNode data, JsonNode definition) {
        ObjectNode dataWithWrappedChildren = data.deepCopy();
        if (hasChildComponents(definition)) {
            List<JsonNode> childComponents = listChildComponents(definition);
            for (JsonNode child : childComponents) {
                String key = child.get("key").asText();
                if (dataWithWrappedChildren.has(key)) {
                    dataWithWrappedChildren.set(key, wrapGridData(dataWithWrappedChildren.get(key), child));
                }
            }
        }
        return dataWithWrappedChildren;
    }

    private JsonNode wrapGridDataInArray(ArrayNode data, JsonNode definition) {
        ArrayNode wrappedData = data.deepCopy();
        if (isGridUnwrapped(definition)) {
            String wrapperName = definition.at("/components/0/key").asText();
            for (int index = 0; index < data.size(); index++) {
                JsonNode arrayElement = data.get(index);
                ObjectNode wrapper = JsonNodeFactory.instance.objectNode();
                wrapper.set(wrapperName, arrayElement);
                wrappedData.set(index, wrapper);
            }
        }
        for (int index = 0; index < data.size(); index++) {
            JsonNode wrappedElement = wrapGridData(wrappedData.get(index), definition);
            wrappedData.set(index, wrappedElement);
        }
        return wrappedData;
    }

    private boolean isGridUnwrapped(JsonNode definition) {
        JsonNode noRowWrappingProperty = definition.at(String.format("/properties/%s", GRID_NO_ROW_WRAPPING_PROPERTY));
        return isArrayComponent(definition)
                && !noRowWrappingProperty.isMissingNode()
                && TRUE.equals(noRowWrappingProperty.asBoolean());
    }

    private boolean hasChildComponents(JsonNode definition) {
        return !definition.at("/components").isMissingNode();
    }

    private JsonNode getChildComponents(JsonNode component) {
        if (hasTypeOf(component, "columns"))
            return component.get("columns");
        if (hasTypeOf(component, "table"))
            return component.get("rows");
        return component.get("components");
    }

    private List<JsonNode> listChildComponents(JsonNode definition) {
        final Set<String> layoutComponentTypes = new HashSet<>(asList("well", "table", "columns", "fieldset", "panel"));
        final Set<String> containerComponentTypes = new HashSet<>(asList("well", "fieldset", "panel"));
        List<JsonNode> nodes = new ArrayList<>();
        toStream(definition.get("components"))
                .filter(component -> !layoutComponentTypes.contains(component.get("type").asText()))
                .forEach(nodes::add);
        toStream(definition.get("components"))
                .filter(component -> containerComponentTypes.contains(component.get("type").asText()))
                .flatMap(component -> toStream(component.get("components")))
                .forEach(nodes::add);
        toStream(definition.get("components"))
                .filter(component -> "columns".equals(component.get("type").asText()))
                .flatMap(component -> toStream(component.get("columns")))
                .flatMap(component -> toStream(component.get("components")))
                .forEach(nodes::add);
        toStream(definition.get("components"))
                .filter(component -> "table".equals(component.get("type").asText()))
                .flatMap(component -> toStream(component.get("rows")))
                .flatMap(this::toStream)
                .flatMap(component -> toStream(component.get("components")))
                .forEach(nodes::add);
        return nodes;
    }

    private JsonNode unwrapGridData(JsonNode data, JsonNode definition) {
        if (hasChildComponents(definition)) {
            List<JsonNode> childComponents = listChildComponents(definition);
            if (data.isObject()) {
                return unwrapGridDataFromObject(data, childComponents);
            }
            if (data.isArray()) {
                return unwrapGridDataFromArray(data, childComponents);
            }
        }
        return data;
    }

    private JsonNode unwrapGridDataFromObject(JsonNode data, List<JsonNode> childComponents) {
        ObjectNode unwrappedData = JsonNodeFactory.instance.objectNode();
        for (JsonNode childDefinition : childComponents) {
            String key = childDefinition.get("key").asText();
            if (data.has(key)) {
                unwrappedData.set(key, unwrapGridData(data, childDefinition, key));
            }
        }
        return unwrappedData;
    }

    private JsonNode unwrapGridDataFromArray(JsonNode data, List<JsonNode> childComponents) {
        ArrayNode unwrappedArray = data.deepCopy();
        for (int index = 0; index < data.size(); index++) {
            ObjectNode currentNode = JsonNodeFactory.instance.objectNode();
            for (JsonNode childDefinition : childComponents) {
                String key = childDefinition.get("key").asText();
                JsonNode unwrappedData = unwrapGridData(data.get(index), childDefinition, key);
                currentNode.set(key, unwrappedData);
            }
            unwrappedArray.set(index, currentNode);
        }
        return unwrappedArray;
    }

    private JsonNode unwrapGridData(JsonNode data, JsonNode childDefinition, String key) {
        if (!data.has(key)) {
            return data;
        }
        data = unwrapGridData(data.get(key), childDefinition);
        if (isArrayComponent(childDefinition)) {
            data = unwrapGridData(childDefinition, (ArrayNode) data);
        }
        return data;
    }

    private JsonNode unwrapGridData(JsonNode gridDefinition, ArrayNode data) {
        ArrayNode components = (ArrayNode) gridDefinition.get("components");
        ArrayNode unwrappedData = JsonNodeFactory.instance.arrayNode();
        JsonNode noRowWrappingProperty = gridDefinition.at(String.format("/properties/%s", GRID_NO_ROW_WRAPPING_PROPERTY));
        if (!noRowWrappingProperty.isMissingNode() && TRUE.equals(noRowWrappingProperty.asBoolean()) && (components.size() == 1)) {
            data.forEach(node -> unwrappedData.add(node.elements().next()));
        } else {
            unwrappedData.addAll(data);
        }
        return unwrappedData;
    }

    private boolean isContainerComponent(JsonNode componentDefinition) {
        return hasTypeOf(componentDefinition, "form", "container", "survey");
    }

    private boolean isArrayComponent(JsonNode componentDefinition) {
        return hasTypeOf(componentDefinition, "datagrid", "editgrid");
    }

    private boolean isLayoutComponent(JsonNode component) {
        return hasTypeOf(component, "well", "columns", "fieldset", "panel", "table", "tabs");
    }

    private boolean hasTypeOf(JsonNode component, String... types) {
        JsonNode typeField = component.get("type");
        String componentType = typeField != null ? typeField.asText() : "";
        boolean result = false;
        for (String type : types)
            result |= componentType.equals(type);
        return result;
    }

    private Stream<JsonNode> toStream(JsonNode node) {
        return StreamSupport.stream(node.spliterator(), false);
    }

    private Stream<Map.Entry<String, JsonNode>> toFieldStream(JsonNode node) {
        return StreamSupport.stream(Spliterators
                .spliteratorUnknownSize(node.fields(), Spliterator.ORDERED), false);
    }

    private JsonNode getFormVariables(JsonNode formDefinition, ObjectNode submittedVariables,
                                      ObjectNode currentVariables) {
        return getFormVariables(listChildComponents(formDefinition), submittedVariables, currentVariables);
    }

    private JsonNode getFormVariables(List<JsonNode> formComponents, JsonNode submittedVariables,
                                      JsonNode currentVariables) {
        return Optional.ofNullable(formComponents)
                .map(Collection::stream)
                .orElse(Stream.empty())
                .filter(component -> component.get("input").asBoolean())
                .filter(component -> StringUtils.isNotBlank(component.get("key").asText()))
                .map(component -> getFormVariable(component, submittedVariables, currentVariables))
                .filter(Objects::nonNull)
                .collect(
                        JSON_MAPPER::createObjectNode,
                        (resultData, cleanDataEntry) -> resultData.set(cleanDataEntry.getKey(), cleanDataEntry.getValue()),
                        ObjectNode::setAll
                );
    }

    private Map.Entry<String, ? extends JsonNode> getFormVariable(JsonNode component, JsonNode submittedVariables,
                                                                  JsonNode currentVariables) {
        if (isContainerComponent(component)) {
            return getContainerVariable(component, submittedVariables, currentVariables);
        } else if (isArrayComponent(component)) {
            return getArrayComponentVariable(component, submittedVariables, currentVariables);
        } else {
            return getSimpleComponentVariable(component, submittedVariables, currentVariables);
        }
    }

    private Map.Entry<String, ? extends JsonNode> getContainerVariable(JsonNode component, JsonNode submittedVariables,
                                                                       JsonNode currentVariables) {
        String componentKey = component.get("key").asText();
        submittedVariables = submittedVariables.has(componentKey) ? submittedVariables.get(componentKey) : JSON_MAPPER.createObjectNode();
        currentVariables = currentVariables.has(componentKey) ? currentVariables.get(componentKey) : JSON_MAPPER.createObjectNode();
        JsonNode containerValue = getFormVariables(listChildComponents(component), submittedVariables, currentVariables);
        return containerValue.size() == 0 ? null : new SimpleEntry<>(componentKey, containerValue);
    }

    private Map.Entry<String, ArrayNode> getArrayComponentVariable(JsonNode component, JsonNode submittedVariables,
                                                                   JsonNode currentVariables) {
        String componentKey = component.get("key").asText();
        ArrayNode containerValue = JSON_MAPPER.createArrayNode();
        JsonNode editableArrayData = submittedVariables.has(componentKey) ? submittedVariables.get(componentKey) : JSON_MAPPER.createObjectNode();
        JsonNode readOnlyArrayData = currentVariables.has(componentKey) ? currentVariables.get(componentKey) : JSON_MAPPER.createArrayNode();
        if (editableArrayData != null) {
            for (int i = 0; i < editableArrayData.size(); i++) {
                JsonNode editableArrayItemData = editableArrayData.get(i);
                JsonNode readOnlyDataArrayItemData = readOnlyArrayData.has(i) ? readOnlyArrayData.get(i) : JSON_MAPPER.createObjectNode();
                JsonNode containerItemValue = getFormVariables(listChildComponents(component), editableArrayItemData, readOnlyDataArrayItemData);
                containerValue.add(containerItemValue);
            }
        }
        return containerValue.size() == 0
                ? null
                : new SimpleEntry<>(componentKey, containerValue);
    }

    private Map.Entry<String, ? extends JsonNode> getSimpleComponentVariable(JsonNode component, JsonNode editableData,
                                                                             JsonNode readOnlyData) {
        String componentKey = component.get("key").asText();
        Entry<String, JsonNode> editableDataEntry = editableData != null && editableData.has(componentKey)
                ? new SimpleEntry<>(componentKey, editableData.get(componentKey))
                : null;
        Entry<String, JsonNode> readOnlyDataEntry = readOnlyData != null && readOnlyData.has(componentKey)
                ? new SimpleEntry<>(componentKey, readOnlyData.get(componentKey))
                : null;
        return !component.path("disabled").asBoolean() ? editableDataEntry : readOnlyDataEntry;
    }

    private boolean isFileVariable(String variableName, String formDefinition) {
        Function<String, JSONArray> fileFieldSearch = key -> JsonPath.read(formDefinition, String.format("$..[?(@.type == 'file' && @.key == '%s')]", variableName));
        JSONArray fileField = FILE_FIELDS_CACHE.computeIfAbsent(formDefinition + "-" + variableName, fileFieldSearch);
        return !fileField.isEmpty();
    }

    private String getFormResourcesDirPath(String formDefinitionJson, ResourceLoader resourceLoader) {
        String cacheKey = resourceLoader.getGroupId() != null ? 
                resourceLoader.getGroupId()
                : String.valueOf(formDefinitionJson.hashCode());
        
        return FORM_RESOURCES_DIR_CACHE.computeIfAbsent(cacheKey, key -> {
            try {
                Path formResourcesDir = createFormResourcesDir(key);
                populateFormResourcesDir(formResourcesDir, resourceLoader);
                return formResourcesDir.toString();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
    
    
    private Path createFormResourcesDir(String dirName) throws IOException {
        dirName = replaceIllegalPathNameCharacters(dirName);
        Path dir = Paths.get(FORMIO_TEMP_DIR.toString(), dirName);
        return dir.toFile().exists()
                ? dir
                : Files.createDirectories(dir);
    }

    private String replaceIllegalPathNameCharacters(String dirName) {
        return dirName.replaceAll("[^a-zA-Z0-9\\.\\-]", "_");
    }

    private void populateFormResourcesDir(Path formReourcesDir, ResourceLoader resourceLoader) {
        resourceLoader.listResourceNames()
                .forEach(resourceKey -> {
                    try (InputStream resource = resourceLoader.getResource(resourceKey)) {
                        Path destFile = Paths.get(formReourcesDir.toString(), resourceKey);
                        copyToFile(resource, destFile);
                    } catch (IOException e) {
                        throw new RuntimeException("Could not populate form resources directory.", e);
                    }
                });
    }

    private void copyToFile(InputStream source, Path destination) throws IOException {
        if (!destination.toFile().exists()) {
            Files.createDirectories(destination.getParent());
            Files.createFile(destination);
        }
        byte[] bytes = source.readAllBytes();
        Files.write(destination, bytes);
    }

    private class FileOperationExecutor {

        private List<BiFunction<String, JsonNode, JsonNode>> operations = new ArrayList<>();
        private String formDefinition;

        public FileOperationExecutor(String formDefinition) {
            this.formDefinition = formDefinition;
        }

        private FileOperationExecutor addDownloadUrlPrefix(FileStorage fileStorage) {
            return add((fileVariablePath, fileVariableValue) -> {
                String downloadUrlPrefix = fileStorage.getDownloadUrlPrefix();
                StreamSupport.stream(fileVariableValue.spliterator(), false)
                        .map(file -> (ObjectNode) file)
                        .forEach(file -> file.replace("url", new TextNode(String.format("%s/%s", downloadUrlPrefix, file.get("url").asText()))));
                return fileVariableValue;
            });
        }

        private FileOperationExecutor storeFile(FileStorage fileStorage) {
            return add((fileVariablePath, fileVariableValue) -> {
                IntPredicate notStoredFilesPredicate = index -> fileVariableValue.get(index).get("storage").asText().equals("base64");
                IntStream.range(0, fileVariableValue.size())
                        .filter(notStoredFilesPredicate)
                        .forEach(index -> {
                            String fileId = String.format("%s[%d]", fileVariablePath, index);
                            FormioFileToFileStorageEntityAdapter fileStorageEntity =
                                    new FormioFileToFileStorageEntityAdapter(fileId, fileVariableValue.get(index));
                            fileStorage.store(fileStorageEntity);
                        });
                return fileVariableValue;
            });
        }

        private FileOperationExecutor extractFormioDataInUrl(ObjectNode dataInUrlBuffer) {
            return add((fileVariablePath, fileVariableValue) -> {
                ObjectNode dataInUrl = extractDataInUrl(fileVariableValue);
                dataInUrlBuffer.set(fileVariablePath, dataInUrl);
                return fileVariableValue;
            });
        }

        private FileOperationExecutor addFormioDataInUrl(ObjectNode dataInUrlBuffer) {
            return add((fileVariablePath, fileVariableValue) -> {
                JsonNode dataInUrl = dataInUrlBuffer.get(fileVariablePath);
                addDataInUrl(dataInUrl, fileVariableValue);
                return fileVariableValue;
            });
        }

        private FileOperationExecutor convertToFormioFile() {
            return add((fileVariablePath, fileVariableValue) ->
                    convertFiles(fileVariableValue, fileConverter::toFormioFile));
        }

        private FileOperationExecutor convertFromFormioFile() {
            return add((fileVariableName, fileVariableValue) ->
                    convertFiles(fileVariableValue, fileConverter::fromFormioFile));
        }

        private void addDataInUrl(JsonNode source, JsonNode destination) {
            StreamSupport.stream(destination.spliterator(), false)
                    .map(file -> (ObjectNode) file)
                    .filter(file -> file.get("storage").asText().equals("base64"))
                    .forEach(file -> file.set("url", source.get(file.get("name").asText())));
        }

        private JsonNode convertFiles(JsonNode files, Function<JsonNode, JsonNode> converter) {
            ArrayNode convertedFileData = JSON_MAPPER.createArrayNode();
            StreamSupport.stream(files.spliterator(), false)
                    .map(converter)
                    .forEach(convertedFileData::add);
            return convertedFileData;
        }

        private ObjectNode extractDataInUrl(JsonNode files) {
            ObjectNode filesContentBuffer = JSON_MAPPER.createObjectNode();
            StreamSupport.stream(files.spliterator(), false)
                    .filter(file -> file.get("storage").asText().equals("base64"))
                    .forEach(file ->
                            filesContentBuffer.put(file.get("name").asText(), ((ObjectNode) file).remove("url").asText()));
            return filesContentBuffer;
        }

        public FileOperationExecutor add(BiFunction<String, JsonNode, JsonNode> operation) {
            operations.add(operation);
            return this;
        }

        public ObjectNode execute(JsonNode variables) {
            String rootPath = "";
            ObjectNode result = execute(rootPath, variables);
            operations.clear();
            return result;
        }

        private ObjectNode execute(String variablePath, JsonNode objectVariable) {
            ObjectNode result = JSON_MAPPER.createObjectNode();
            objectVariable.fields().forEachRemaining(
                    field -> {
                        String fieldName = field.getKey();
                        JsonNode fieldValue = field.getValue();
                        String fieldPath = !variablePath.isEmpty()
                                ? variablePath + "." + fieldName
                                : fieldName;
                        if (isFileVariable(fieldName, formDefinition)) {
                            fieldValue = executeOperations(fieldPath, fieldValue);
                        } else if (fieldValue.isObject()) {
                            fieldValue = execute(fieldPath, fieldValue);
                        } else if (fieldValue.isArray()) {
                            fieldValue = execute(fieldPath, (ArrayNode) fieldValue);
                        }
                        result.set(fieldName, fieldValue);
                    });
            return result;
        }

        private ArrayNode execute(String variablePath, ArrayNode arrayVariable) {
            ArrayNode result = JSON_MAPPER.createArrayNode();
            for (int i = 0; i < arrayVariable.size(); i++) {
                JsonNode element = arrayVariable.get(i);
                String pathSuffix = String.format("[%s]", i);
                if (element.isObject()) {
                    result.add(execute(variablePath + pathSuffix, element));
                } else if (element.isArray()) {
                    result.add(execute(variablePath + pathSuffix, (ArrayNode) element));
                } else {
                    result.add(element);
                }
            }
            return result;
        }

        private JsonNode executeOperations(String fileVariablePath, JsonNode fileVariableValue) {
            for (var operation : operations) {
                fileVariableValue = operation.apply(fileVariablePath, fileVariableValue);
            }
            return fileVariableValue;
        }

    }

}
