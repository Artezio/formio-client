package com.artezio.forms.formio;

import com.artezio.bpm.resources.ResourceLoader;
import com.artezio.forms.FormClient;
import com.artezio.forms.formio.exceptions.FormValidationException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.*;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import net.minidev.json.JSONArray;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.ServletContext;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.AbstractMap.SimpleEntry;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.lang.Boolean.TRUE;
import static java.util.Arrays.asList;

@Named
public class FormioClient implements FormClient {

    private static final Map<String, JsonNode> FORMS_CACHE = new ConcurrentHashMap<>();
    private final static Map<String, JSONArray> FILE_FIELDS_CACHE = new ConcurrentHashMap<>();
    private final static Map<String, Boolean> SUBMISSION_PROCESSING_DECISIONS_CACHE = new ConcurrentHashMap<>();
    private final static String DRY_VALIDATION_AND_CLEANUP_SCRIPT_NAME = "cleanUpAndValidate.js";
    private final static String CLEAN_UP_SCRIPT_NAME = "cleanUp.js";
    private final static String GRID_NO_ROW_WRAPPING_PROPERTY = "noRowWrapping";
    private final static ObjectMapper JSON_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setDefaultMergeable(false);
    private final static ParseContext JAYWAY_PARSER = JsonPath.using(Configuration.builder()
            .jsonProvider(new JacksonJsonNodeJsonProvider())
            .build());

    @Inject
    private NodeJsProcessor nodeJsProcessor;
    @Inject
    private FileAttributeConverter fileAttributeConverter;
    @Inject
    private ServletContext servletContext;

    @Override
    public String getFormWithData(String formKey, ObjectNode taskVariables, ResourceLoader resourceLoader) {
        try {
            JsonNode formDefinition = getForm(formKey, resourceLoader);
            JsonNode cleanData = cleanUnusedData(formDefinition.toString(), taskVariables);
            JsonNode data = wrapGridData(cleanData, formDefinition);
            ((ObjectNode) formDefinition).set("data", data);
            return formDefinition.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to get a form.", e);
        }
    }

    @Override
    public String getFormWithData(String formKey, ObjectNode taskVariables) {
        try {
            ResourceLoader resourceLoader = new DefaultResourceLoader();
            JsonNode formDefinition = getForm(formKey, resourceLoader);
            JsonNode cleanData = cleanUnusedData(formDefinition.toString(), taskVariables);
            JsonNode data = wrapGridData(cleanData, formDefinition);
            ((ObjectNode) formDefinition).set("data", data);
            return formDefinition.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to get a form.", e);
        }
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
    public String dryValidationAndCleanup(String formKey, ObjectNode submittedVariables,
                                          ObjectNode taskVariables) {
        ResourceLoader resourceLoader = new DefaultResourceLoader();
        return dryValidationAndCleanup(formKey, submittedVariables, taskVariables, resourceLoader);
    }

    @Override
    public String dryValidationAndCleanup(String formKey, ObjectNode submittedVariables,
                                          ObjectNode taskVariables, ResourceLoader resourceLoader) {
        try {
            JsonNode formDefinition = getForm(formKey, resourceLoader);
            String formDefinitionJson = formDefinition.toString();
            String customComponentsDir = getCustomComponentsDir();
            taskVariables = convertFilesInData(formDefinitionJson, taskVariables, fileAttributeConverter::toFormioFile);
            ObjectNode formVariables = (ObjectNode) getFormVariables(formDefinition, submittedVariables, taskVariables);
            String submissionData = JSON_MAPPER.writeValueAsString(toFormIoSubmissionData(formVariables));
            byte[] validationResult = nodeJsProcessor.executeScript(DRY_VALIDATION_AND_CLEANUP_SCRIPT_NAME, formDefinitionJson, submissionData, customComponentsDir);
            JsonNode cleanData = getDataFromScriptExecutionResult(validationResult);
            JsonNode unwrappedCleanData = unwrapGridData(cleanData, formDefinition);
            return convertFilesInData(formDefinitionJson, (ObjectNode) unwrappedCleanData, fileAttributeConverter::toCamundaFile).toString();
        } catch (Exception ex) {
            throw new FormValidationException(ex);
        }
    }

    @Override
    public List<String> getFormVariableNames(String formKey) {
        DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
        return getFormVariableNames(formKey, resourceLoader);
    }

    @Override
    public List<String> getFormVariableNames(String formKey, ResourceLoader resourceLoader) {
        JsonNode formDefinition = getForm(formKey, resourceLoader);
        return Optional.ofNullable(getChildComponents(formDefinition))
                .map(Collection::stream)
                .orElse(Stream.empty())
                .filter(component -> component.path("input").asBoolean())
                .filter(component -> StringUtils.isNotBlank(component.path("key").asText()))
                .map(component -> component.get("key").asText())
                .collect(Collectors.toList());
    }

    private JsonNode getForm(String formKey, ResourceLoader resourceLoader) {
        try(InputStream resource = resourceLoader.getResource(formKey)) {
            return FORMS_CACHE.computeIfAbsent(formKey, key -> {
                try {
                    return expandSubforms(JSON_MAPPER.readTree(resource), resourceLoader);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private JsonNode getSubform(String formKey, ResourceLoader resourceLoader) {
        formKey = formKey + ".json";
        return getForm(formKey, resourceLoader);
    }

    private JsonNode cleanUnusedData(String formDefinition, ObjectNode taskVariables) throws IOException {
        ObjectNode formioSubmissionData = toFormIoSubmissionData(taskVariables);
        formioSubmissionData = convertFilesInData(formDefinition, formioSubmissionData, fileAttributeConverter::toFormioFile);
        String submissionData = JSON_MAPPER.writeValueAsString(formioSubmissionData);
        String customComponentsDir = getCustomComponentsDir();
        byte[] cleanUpResult = nodeJsProcessor.executeScript(CLEAN_UP_SCRIPT_NAME, formDefinition, submissionData, customComponentsDir);
        return getDataFromScriptExecutionResult(cleanUpResult);
    }

    private JsonNode getDataFromScriptExecutionResult(byte[] scriptExecutionResult) throws IOException {
        JsonNode json = JSON_MAPPER.readTree(scriptExecutionResult);
        return json.has("data")
                ? json.get("data")
                : JSON_MAPPER.createObjectNode();
    }

    protected JsonNode expandSubforms(JsonNode form, ResourceLoader resourceLoader) {
        Collector<JsonNode, ArrayNode, ArrayNode> arrayNodeCollector = Collector
                .of(JSON_MAPPER::createArrayNode, ArrayNode::add, ArrayNode::addAll);
        Function<JsonNode, JsonNode> expandSubformsFunction = getExpandSubformsFunction(resourceLoader);
        JsonNode components = toStream(form.get("components"))
                .map(expandSubformsFunction)
                .collect(arrayNodeCollector);
        return ((ObjectNode) form).set("components", components);
    }

    private Function<JsonNode, JsonNode> getExpandSubformsFunction(ResourceLoader resourceLoader) {
        return component -> {
            if (hasTypeOf(component, "container") || isArrayComponent(component)) {
                return expandSubforms(component, resourceLoader);
            } else if (hasTypeOf(component, "form")) {
                return convertToContainer(component, resourceLoader);
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
            List<JsonNode> childComponents = getChildComponents(definition);
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

    private List<JsonNode> getChildComponents(JsonNode definition) {
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

    private ObjectNode toFormIoSubmissionData(ObjectNode data) {
        if (!data.has("data")) {
            ObjectNode formioData = JSON_MAPPER.createObjectNode();
            formioData.set("data", data);
            return formioData;
        } else {
            return data;
        }
    }

    private JsonNode unwrapGridData(JsonNode data, JsonNode definition) {
        if (hasChildComponents(definition)) {
            List<JsonNode> childComponents = getChildComponents(definition);
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
        return hasTypeOf(componentDefinition, "form")
                || hasTypeOf(componentDefinition, "container")
                || hasTypeOf(componentDefinition, "survey");
    }

    private boolean isArrayComponent(JsonNode componentDefinition) {
        return hasTypeOf(componentDefinition, "datagrid")
                || hasTypeOf(componentDefinition, "editgrid");
    }

    private boolean hasTypeOf(JsonNode component, String type) {
        JsonNode typeField = component.get("type");
        String componentType = typeField != null ? typeField.asText() : "";
        return componentType.equals(type);
    }

    private Stream<JsonNode> toStream(JsonNode node) {
        return StreamSupport.stream(node.spliterator(), false);
    }

    private Stream<Map.Entry<String, JsonNode>> toFieldStream(JsonNode node) {
        return StreamSupport.stream(Spliterators
                .spliteratorUnknownSize(node.fields(), Spliterator.ORDERED), false);
    }

    private JsonNode getFormVariables(JsonNode formDefinition, ObjectNode submittedVariables,
                                      ObjectNode taskVariables) {
        return getFormVariables(getChildComponents(formDefinition), submittedVariables, taskVariables);
    }

    private JsonNode getFormVariables(List<JsonNode> formComponents, JsonNode submittedVariables,
                                      JsonNode taskVariables) {
        return Optional.ofNullable(formComponents)
                .map(Collection::stream)
                .orElse(Stream.empty())
                .filter(component -> component.get("input").asBoolean())
                .filter(component -> StringUtils.isNotBlank(component.get("key").asText()))
                .map(component -> getFormVariable(component, submittedVariables, taskVariables))
                .filter(Objects::nonNull)
                .collect(
                        JSON_MAPPER::createObjectNode,
                        (resultData, cleanDataEntry) -> resultData.set(cleanDataEntry.getKey(), cleanDataEntry.getValue()),
                        ObjectNode::setAll
                );
    }

    private Map.Entry<String, ? extends JsonNode> getFormVariable(JsonNode component, JsonNode submittedVariables,
                                                                  JsonNode taskVariables) {
        if (isContainerComponent(component)) {
            return getContainerVariable(component, submittedVariables, taskVariables);
        } else if (isArrayComponent(component)) {
            return getArrayComponentVariable(component, submittedVariables, taskVariables);
        } else {
            return getSimpleComponentVariable(component, submittedVariables, taskVariables);
        }
    }

    private Map.Entry<String, ? extends JsonNode> getContainerVariable(JsonNode component, JsonNode submittedVariables,
                                                                       JsonNode taskVariables) {
        String componentKey = component.get("key").asText();
        submittedVariables = submittedVariables.has(componentKey) ? submittedVariables.get(componentKey) : JSON_MAPPER.createObjectNode();
        taskVariables = taskVariables.has(componentKey) ? taskVariables.get(componentKey) : JSON_MAPPER.createObjectNode();
        JsonNode containerValue = getFormVariables(getChildComponents(component), submittedVariables, taskVariables);
        return containerValue.size() == 0 ? null : new SimpleEntry<>(componentKey, containerValue);
    }

    private Map.Entry<String, ArrayNode> getArrayComponentVariable(JsonNode component, JsonNode submittedVariables,
                                                                   JsonNode taskVariables) {
        String componentKey = component.get("key").asText();
        ArrayNode containerValue = JSON_MAPPER.createArrayNode();
        JsonNode editableArrayData = submittedVariables.has(componentKey) ? submittedVariables.get(componentKey) : JSON_MAPPER.createObjectNode();
        JsonNode readOnlyArrayData = taskVariables.has(componentKey) ? taskVariables.get(componentKey) : JSON_MAPPER.createArrayNode();
        if (editableArrayData != null) {
            for (int i = 0; i < editableArrayData.size(); i++) {
                JsonNode editableArrayItemData = editableArrayData.get(i);
                JsonNode readOnlyDataArrayItemData = readOnlyArrayData.has(i) ? readOnlyArrayData.get(i) : JSON_MAPPER.createObjectNode();
                JsonNode containerItemValue = getFormVariables(getChildComponents(component), editableArrayItemData, readOnlyDataArrayItemData);
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

    private ObjectNode convertFilesInData(String formDefinition, ObjectNode data, Function<ObjectNode, ObjectNode> converter) {
        String rootObjectAttributePath = "";
        return convertFilesInData(rootObjectAttributePath, data, formDefinition, converter);
    }

    private ObjectNode convertFilesInData(
            String objectAttributePath,
            ObjectNode objectVariableAttributes,
            String formDefinition,
            Function<ObjectNode, ObjectNode> converter) {
        ObjectNode convertedObject = JSON_MAPPER.createObjectNode();
        objectVariableAttributes.fields().forEachRemaining(
                attribute -> {
                    JsonNode attributeValue = attribute.getValue();
                    String attributeName = attribute.getKey();
                    String attributePath = !objectAttributePath.isEmpty()
                            ? objectAttributePath + "/" + attributeName
                            : attributeName;
                    if (isFileVariable(attributeName, formDefinition)) {
                        attributeValue = convertFilesInData((ArrayNode) attributeValue, converter);
                    } else if (attributeValue.isObject()) {
                        attributeValue = convertFilesInData(attributePath, (ObjectNode) attributeValue, formDefinition, converter);
                    } else if (attributeValue.isArray()) {
                        attributeValue = convertFilesInData(attributePath, (ArrayNode) attributeValue, formDefinition, converter);
                    }
                    convertedObject.set(attributeName, attributeValue);
                });
        return convertedObject;
    }

    private ArrayNode convertFilesInData(
            String attributePath,
            ArrayNode array,
            String formDefinition,
            Function<ObjectNode, ObjectNode> converter) {
        ArrayNode convertedArray = JSON_MAPPER.createArrayNode();
        StreamSupport.stream(array.spliterator(), false)
                .map(element -> {
                    if (element.isObject()) {
                        return convertFilesInData(attributePath, (ObjectNode) element, formDefinition, converter);
                    } else if (element.isArray()) {
                        return convertFilesInData(attributePath + "[*]", (ArrayNode) element, formDefinition, converter);
                    } else {
                        return element;
                    }
                })
                .forEach(convertedArray::add);
        return convertedArray;
    }

    private ArrayNode convertFilesInData(ArrayNode fileData, Function<ObjectNode, ObjectNode> converter) {
        ArrayNode convertedFileData = JSON_MAPPER.createArrayNode();
        StreamSupport.stream(fileData.spliterator(), false)
                .map(jsonNode -> (ObjectNode) jsonNode)
                .map(converter)
                .forEach(convertedFileData::add);
        return convertedFileData;
    }

    private boolean isFileVariable(String variableName, String formDefinition) {
        Function<String, JSONArray> fileFieldSearch = key -> JsonPath.read(formDefinition, String.format("$..[?(@.type == 'file' && @.key == '%s')]", variableName));
        JSONArray fileField = FILE_FIELDS_CACHE.computeIfAbsent(formDefinition + "-" + variableName, fileFieldSearch);
        return !fileField.isEmpty();
    }

    //TODO implement
    private String getCustomComponentsDir() {
        return "";
    }

    private class DefaultResourceLoader implements ResourceLoader {
        @Override
        public InputStream getResource(String resourceKey) {
            return servletContext.getResourceAsStream(resourceKey);
        }

        @Override
        public List<String> listResourceNames(String initialPath) {
            try {
                String resourcePath = initialPath.startsWith("/") ? initialPath : "/" + initialPath;
                URL url = servletContext.getResource(resourcePath);
                if (url == null) return Collections.emptyList();
                File resource = new File(url.toURI());
                return Arrays.stream(resource.listFiles())
                        .flatMap(file -> {
                            String resourceName = initialPath + "/" + file.getName();
                            return file.isDirectory()
                                    ? listResourceNames(resourceName).stream()
                                    : Arrays.asList(resourceName).stream();
                        })
                        .collect(Collectors.toList());
            } catch (MalformedURLException | URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
