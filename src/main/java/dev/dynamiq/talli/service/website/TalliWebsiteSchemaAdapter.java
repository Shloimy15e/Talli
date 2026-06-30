package dev.dynamiq.talli.service.website;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import dev.dynamiq.talli.service.github.GithubFileChange;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

@Component
public class TalliWebsiteSchemaAdapter implements WebsiteContentAdapter {

    public static final String TYPE = "talli_schema_v1";
    public static final String SCHEMA_PATH = "talli/editor.schema.json";
    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private final ObjectMapper objectMapper;

    public TalliWebsiteSchemaAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public List<String> expectedPaths() {
        return List.of(SCHEMA_PATH);
    }

    @Override
    public List<String> additionalPaths(Map<String, byte[]> files) {
        return contentFiles(schema(files));
    }

    @Override
    public WebsiteEditorForm toEditorForm(Map<String, byte[]> files) {
        JsonNode schema = schema(files);
        Map<String, ObjectNode> content = content(files, contentFiles(schema));
        List<WebsiteEditorSection> sections = new ArrayList<>();

        for (JsonNode section : iterable(schema.path("sections"))) {
            List<WebsiteEditorBlock> blocks = new ArrayList<>();
            for (JsonNode block : iterable(section.path("blocks"))) {
                String type = text(block, "type", "fields");
                if ("repeat".equals(type)) {
                    blocks.add(repeatBlock(block, content));
                } else {
                    blocks.add(fieldsBlock(block, content));
                }
            }
            sections.add(new WebsiteEditorSection(
                    required(section, "id"),
                    required(section, "label"),
                    text(section, "title", required(section, "label")),
                    text(section, "icon", "file-text"),
                    blocks
            ));
        }

        return new WebsiteEditorForm(sections);
    }

    @Override
    public List<GithubFileChange> apply(Long projectId,
                                        Map<String, byte[]> files,
                                        Map<String, String[]> params,
                                        MultiValueMap<String, MultipartFile> uploads) {
        JsonNode schema = schema(files);
        List<String> contentFiles = contentFiles(schema);
        Map<String, ObjectNode> content = content(files, contentFiles);
        List<GithubFileChange> imageChanges = new ArrayList<>();

        try {
            for (JsonNode section : iterable(schema.path("sections"))) {
                for (JsonNode block : iterable(section.path("blocks"))) {
                    String type = text(block, "type", "fields");
                    if ("repeat".equals(type)) {
                        applyRepeatBlock(projectId, block, content, params, uploads, imageChanges);
                    } else {
                        applyFieldsBlock(projectId, block, content, params, uploads, imageChanges);
                    }
                }
            }

            List<GithubFileChange> changes = new ArrayList<>();
            for (String path : contentFiles) {
                changes.add(new GithubFileChange(path, objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsBytes(content.get(path))));
            }
            changes.addAll(imageChanges);
            return changes;
        } catch (IOException e) {
            throw new IllegalStateException("Could not apply Talli website schema.", e);
        }
    }

    private WebsiteEditorBlock fieldsBlock(JsonNode block, Map<String, ObjectNode> content) {
        List<WebsiteEditorField> fields = new ArrayList<>();
        for (JsonNode field : iterable(block.path("fields"))) {
            fields.add(topField(field, content));
        }
        return WebsiteEditorBlock.fields(text(block, "layout", "single"), fields);
    }

    private WebsiteEditorBlock repeatBlock(JsonNode block, Map<String, ObjectNode> content) {
        JsonNode source = block.path("source");
        ObjectNode root = content.get(required(source, "file"));
        ArrayNode array = arrayAt(root, required(source, "path"));
        List<WebsiteEditorField> templateFields = new ArrayList<>();
        List<WebsiteEditorRepeatItem> items = new ArrayList<>();

        for (JsonNode field : iterable(block.path("fields"))) {
            templateFields.add(repeatField(block, field, objectMapper.createObjectNode()));
        }
        for (JsonNode item : iterable(array)) {
            ObjectNode object = item instanceof ObjectNode objectNode ? objectNode : objectMapper.createObjectNode();
            List<WebsiteEditorField> fields = new ArrayList<>();
            for (JsonNode field : iterable(block.path("fields"))) {
                fields.add(repeatField(block, field, object));
            }
            items.add(new WebsiteEditorRepeatItem(fields));
        }

        return WebsiteEditorBlock.repeat(
                required(block, "title"),
                text(block, "help", ""),
                new WebsiteEditorRepeat(
                        text(block, "itemLabel", "Item"),
                        text(block, "addLabel", "Add item"),
                        templateFields,
                        items
                ));
    }

    private WebsiteEditorField topField(JsonNode field, Map<String, ObjectNode> content) {
        JsonNode source = field.path("source");
        ObjectNode root = content.get(required(source, "file"));
        JsonNode value = root.at(required(source, "path"));
        return editorField(field, fieldName(field), value, null, null);
    }

    private WebsiteEditorField repeatField(JsonNode block, JsonNode field, ObjectNode item) {
        String name = repeatFieldName(block, field);
        String existingName = null;
        String touchedName = null;
        List<String> values = List.of();
        String type = text(field, "type", "text");
        JsonNode value = item.at(required(field, "path"));

        if ("image".equals(type)) {
            existingName = name + "Existing";
        }
        if ("imageList".equals(type)) {
            existingName = name + "Existing";
            touchedName = name + "Touched";
            values = imageValues(value, field.path("item"));
        }

        return editorField(field, name, value, existingName, touchedName, values);
    }

    private WebsiteEditorField editorField(JsonNode field,
                                           String name,
                                           JsonNode value,
                                           String existingName,
                                           String touchedName) {
        return editorField(field, name, value, existingName, touchedName, List.of());
    }

    private WebsiteEditorField editorField(JsonNode field,
                                           String name,
                                           JsonNode value,
                                           String existingName,
                                           String touchedName,
                                           List<String> values) {
        String type = text(field, "type", "text");
        String stringValue = fieldValue(value, field);
        String width = text(field, "width", "half");
        if ("imageList".equals(type)) {
            width = "full";
        }
        return new WebsiteEditorField(
                type,
                name,
                required(field, "label"),
                stringValue,
                text(field, "placeholder", ""),
                integer(field, "rows", "textarea".equals(type) ? 4 : 0),
                width,
                text(field, "help", ""),
                existingName,
                touchedName,
                values,
                marks(field),
                nullableText(field, "required"),
                invalidMessage(field, type)
        );
    }

    private void applyFieldsBlock(Long projectId,
                                  JsonNode block,
                                  Map<String, ObjectNode> content,
                                  Map<String, String[]> params,
                                  MultiValueMap<String, MultipartFile> uploads,
                                  List<GithubFileChange> imageChanges) throws IOException {
        for (JsonNode field : iterable(block.path("fields"))) {
            JsonNode source = field.path("source");
            ObjectNode root = content.get(required(source, "file"));
            String path = required(source, "path");
            String name = fieldName(field);
            String type = text(field, "type", "text");

            if ("image".equals(type)) {
                String image = uploadedPublicPath(projectId, field, name, null, null, uploads, imageChanges);
                if (image != null) {
                    setText(root, path, image);
                }
            } else {
                applyTextValue(root, path, field, param(params, name));
            }
        }
    }

    private void applyRepeatBlock(Long projectId,
                                  JsonNode block,
                                  Map<String, ObjectNode> content,
                                  Map<String, String[]> params,
                                  MultiValueMap<String, MultipartFile> uploads,
                                  List<GithubFileChange> imageChanges) throws IOException {
        JsonNode source = block.path("source");
        ObjectNode root = content.get(required(source, "file"));
        String path = required(source, "path");
        ArrayNode existing = arrayAt(root, path);
        ArrayNode rows = objectMapper.createArrayNode();

        for (int index : repeatIndexes(block, params, uploads)) {
            ObjectNode item = objectAtOrNew(existing, index);
            boolean hasContent = false;

            for (JsonNode field : iterable(block.path("fields"))) {
                String type = text(field, "type", "text");
                String fieldPath = required(field, "path");
                String name = repeatFieldName(block, field);

                if ("image".equals(type)) {
                    String image = firstNonBlank(
                            uploadedPublicPath(projectId, field, name + "_" + index, index, null, uploads, imageChanges),
                            param(params, name + "Existing_" + index),
                            fieldValue(item.at(fieldPath), field));
                    if (!isBlank(image)) {
                        setText(item, fieldPath, image);
                        hasContent = true;
                    }
                    continue;
                }

                if ("imageList".equals(type)) {
                    ArrayNode images = imageList(projectId, field, name, index, item, params, uploads, imageChanges);
                    setNode(item, fieldPath, images);
                    hasContent = hasContent || images.size() > 0;
                    continue;
                }

                String value = param(params, name + "_" + index);
                applyTextValue(item, fieldPath, field, value);
                hasContent = hasContent || !isBlank(value);
            }

            if (!hasContent) {
                continue;
            }

            applyComputed(block, item, rows.size());
            rows.add(item);
        }

        setNode(root, path, rows);
    }

    private ArrayNode imageList(Long projectId,
                                JsonNode field,
                                String name,
                                int rowIndex,
                                ObjectNode item,
                                Map<String, String[]> params,
                                MultiValueMap<String, MultipartFile> uploads,
                                List<GithubFileChange> imageChanges) throws IOException {
        ArrayNode existing = arrayAt(item, required(field, "path"));
        ArrayNode images = objectMapper.createArrayNode();
        JsonNode itemSchema = field.path("item");
        String existingName = name + "Existing";
        for (int imageIndex : imageIndexes(params, uploads, existingName + "_" + rowIndex + "_", name + "_" + rowIndex + "_")) {
            String image = firstNonBlank(
                    uploadedPublicPath(projectId, field, name + "_" + rowIndex + "_" + imageIndex, rowIndex, imageIndex, uploads, imageChanges),
                    param(params, existingName + "_" + rowIndex + "_" + imageIndex),
                    imageValue(existing.path(imageIndex), itemSchema));
            if (isBlank(image)) {
                continue;
            }
            if (itemSchema.hasNonNull("srcPath")) {
                ObjectNode imageNode = existing.path(imageIndex) instanceof ObjectNode objectNode
                        ? objectNode.deepCopy()
                        : objectMapper.createObjectNode();
                setText(imageNode, required(itemSchema, "srcPath"), image);
                images.add(imageNode);
            } else {
                images.add(image);
            }
        }
        return images;
    }

    private void applyComputed(JsonNode block, ObjectNode item, int outputIndex) {
        for (JsonNode computed : iterable(block.path("computed"))) {
            String value = text(computed, "value", "");
            String path = required(computed, "path");
            if ("index".equals(value)) {
                int number = integer(computed, "start", 1) + outputIndex;
                String format = text(computed, "format", "plain");
                setText(item, path, "2-digit".equals(format) ? String.format("%02d", number) : String.valueOf(number));
            }
            if ("slug".equals(value)) {
                setText(item, path, sanitize(fieldValue(item.at(required(computed, "from")), computed)));
            }
        }
    }

    private void applyTextValue(ObjectNode root, String path, JsonNode field, String value) {
        if ("paragraphs".equals(text(field, "transform", ""))) {
            setNode(root, path, lines(value));
            return;
        }
        if ("color".equals(text(field, "type", "text"))) {
            setText(root, path, normalizedColor(value));
            return;
        }
        setText(root, path, value);
    }

    private JsonNode schema(Map<String, byte[]> files) {
        byte[] bytes = files.get(SCHEMA_PATH);
        if (bytes == null) {
            throw new IllegalStateException("Talli website schema is missing: " + SCHEMA_PATH);
        }
        try {
            JsonNode schema = objectMapper.readTree(new String(bytes, StandardCharsets.UTF_8));
            if (!"talli-editor/v1".equals(text(schema, "version", ""))) {
                throw new IllegalStateException("Unsupported Talli editor schema version.");
            }
            return schema;
        } catch (IOException e) {
            throw new IllegalStateException("Could not parse Talli website schema.", e);
        }
    }

    private List<String> contentFiles(JsonNode schema) {
        List<String> paths = new ArrayList<>();
        for (JsonNode path : iterable(schema.path("contentFiles"))) {
            if (!path.asText("").isBlank()) {
                paths.add(path.asText());
            }
        }
        return List.copyOf(paths);
    }

    private Map<String, ObjectNode> content(Map<String, byte[]> files, List<String> paths) {
        Map<String, ObjectNode> content = new LinkedHashMap<>();
        for (String path : paths) {
            byte[] bytes = files.get(path);
            if (bytes == null) {
                throw new IllegalStateException("Website content file is missing: " + path);
            }
            try {
                JsonNode json = objectMapper.readTree(new String(bytes, StandardCharsets.UTF_8));
                if (!(json instanceof ObjectNode object)) {
                    throw new IllegalStateException("Website content file must contain a JSON object: " + path);
                }
                content.put(path, object);
            } catch (IOException e) {
                throw new IllegalStateException("Could not parse website content file: " + path, e);
            }
        }
        return content;
    }

    private String uploadedPublicPath(Long projectId,
                                      JsonNode field,
                                      String uploadName,
                                      Integer rowIndex,
                                      Integer imageIndex,
                                      MultiValueMap<String, MultipartFile> uploads,
                                      List<GithubFileChange> imageChanges) throws IOException {
        if (uploads == null) {
            return null;
        }
        MultipartFile file = firstPresent(uploads.get(uploadName));
        if (file == null) {
            return null;
        }

        JsonNode upload = field.path("upload");
        String extension = extension(file);
        String baseName = resolveTemplate(text(upload, "filename", uploadName), projectId, rowIndex, imageIndex)
                .replace("{extension}", extension);
        baseName = sanitize(baseName);
        String directory = resolveTemplate(text(upload, "directory", "public/images/cms/{projectId}"), projectId, rowIndex, imageIndex);
        String publicPath = resolveTemplate(text(upload, "publicPath", "/images/cms/{projectId}/{filename}.{extension}"),
                projectId, rowIndex, imageIndex)
                .replace("{filename}", baseName)
                .replace("{extension}", extension);
        String repoPath = trimSlashes(directory) + "/" + baseName + "." + extension;
        imageChanges.add(new GithubFileChange(repoPath, file.getBytes()));
        return publicPath;
    }

    private String resolveTemplate(String template, Long projectId, Integer rowIndex, Integer imageIndex) {
        return template
                .replace("{projectId}", String.valueOf(projectId))
                .replace("{rowNumber}", rowIndex == null ? "" : String.valueOf(rowIndex + 1))
                .replace("{rowIndex}", rowIndex == null ? "" : String.valueOf(rowIndex))
                .replace("{imageNumber}", imageIndex == null ? "" : String.valueOf(imageIndex + 1))
                .replace("{imageIndex}", imageIndex == null ? "" : String.valueOf(imageIndex));
    }

    private MultipartFile firstPresent(List<MultipartFile> files) {
        if (files == null) {
            return null;
        }
        return files.stream().filter(f -> f != null && !f.isEmpty()).findFirst().orElse(null);
    }

    private String extension(MultipartFile file) {
        String type = file.getContentType();
        if ("image/jpeg".equalsIgnoreCase(type)) return "jpg";
        if ("image/png".equalsIgnoreCase(type)) return "png";
        if ("image/webp".equalsIgnoreCase(type)) return "webp";

        String name = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "jpg";
        if (name.endsWith(".png")) return "png";
        if (name.endsWith(".webp")) return "webp";
        throw new IllegalArgumentException("Only JPG, PNG, and WEBP images are supported.");
    }

    private ArrayNode lines(String value) {
        ArrayNode array = objectMapper.createArrayNode();
        if (value == null || value.isBlank()) {
            return array;
        }
        for (String part : value.split("\\R\\s*\\R|\\R")) {
            if (!part.isBlank()) {
                array.add(part.trim());
            }
        }
        return array;
    }

    private ArrayNode arrayAt(ObjectNode root, String pointer) {
        JsonNode node = root.at(pointer);
        return node instanceof ArrayNode array ? array : objectMapper.createArrayNode();
    }

    private ObjectNode objectAtOrNew(ArrayNode array, int index) {
        JsonNode child = array.get(index);
        if (child instanceof ObjectNode objectNode) {
            return objectNode.deepCopy();
        }
        return objectMapper.createObjectNode();
    }

    private void setText(ObjectNode root, String pointer, String value) {
        setNode(root, pointer, TextNode.valueOf(value == null ? "" : value));
    }

    private void setNode(ObjectNode root, String pointer, JsonNode value) {
        List<String> parts = pointerParts(pointer);
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("JSON pointer must target a field.");
        }
        ObjectNode cursor = root;
        for (int i = 0; i < parts.size() - 1; i++) {
            String part = parts.get(i);
            JsonNode child = cursor.get(part);
            ObjectNode next;
            if (child instanceof ObjectNode objectNode) {
                next = objectNode;
            } else {
                next = objectMapper.createObjectNode();
                cursor.set(part, next);
            }
            cursor = next;
        }
        cursor.set(parts.get(parts.size() - 1), value);
    }

    private List<String> pointerParts(String pointer) {
        if (pointer == null || pointer.isBlank() || !pointer.startsWith("/")) {
            throw new IllegalArgumentException("Use JSON Pointer paths like /hero/headline.");
        }
        return Arrays.stream(pointer.substring(1).split("/"))
                .map(part -> part.replace("~1", "/").replace("~0", "~"))
                .toList();
    }

    private List<Integer> repeatIndexes(JsonNode block,
                                        Map<String, String[]> params,
                                        MultiValueMap<String, MultipartFile> uploads) {
        SortedSet<Integer> indexes = new TreeSet<>();
        for (JsonNode field : iterable(block.path("fields"))) {
            String name = repeatFieldName(block, field);
            indexes.addAll(indexes(params, name + "_", name + "Existing_", name + "Touched_"));
            if (uploads != null) {
                uploads.keySet().stream()
                        .filter(key -> key.startsWith(name + "_"))
                        .map(key -> leadingInteger(key.substring((name + "_").length())))
                        .filter(Objects::nonNull)
                        .forEach(indexes::add);
            }
        }
        return List.copyOf(indexes);
    }

    private List<Integer> imageIndexes(Map<String, String[]> params,
                                       MultiValueMap<String, MultipartFile> uploads,
                                       String existingPrefix,
                                       String uploadPrefix) {
        SortedSet<Integer> indexes = new TreeSet<>();
        params.keySet().stream()
                .filter(key -> key.startsWith(existingPrefix))
                .map(key -> leadingInteger(key.substring(existingPrefix.length())))
                .filter(Objects::nonNull)
                .forEach(indexes::add);
        if (uploads != null) {
            uploads.keySet().stream()
                    .filter(key -> key.startsWith(uploadPrefix))
                    .map(key -> leadingInteger(key.substring(uploadPrefix.length())))
                    .filter(Objects::nonNull)
                    .forEach(indexes::add);
        }
        return List.copyOf(indexes);
    }

    private List<Integer> indexes(Map<String, String[]> params, String... prefixes) {
        SortedSet<Integer> indexes = new TreeSet<>();
        for (String key : params.keySet()) {
            for (String prefix : prefixes) {
                if (key.startsWith(prefix)) {
                    Integer index = leadingInteger(key.substring(prefix.length()));
                    if (index != null) {
                        indexes.add(index);
                    }
                }
            }
        }
        return List.copyOf(indexes);
    }

    private Integer leadingInteger(String value) {
        if (value == null || value.isBlank() || !Character.isDigit(value.charAt(0))) {
            return null;
        }
        int end = 0;
        while (end < value.length() && Character.isDigit(value.charAt(end))) {
            end++;
        }
        return Integer.parseInt(value.substring(0, end));
    }

    private String fieldValue(JsonNode value, JsonNode field) {
        if ("paragraphs".equals(text(field, "transform", "")) && value.isArray()) {
            List<String> paragraphs = new ArrayList<>();
            value.forEach(paragraph -> paragraphs.add(paragraph.asText("")));
            return String.join("\n\n", paragraphs);
        }
        if (value.isMissingNode() || value.isNull()) {
            return "";
        }
        return value.asText("");
    }

    private List<String> marks(JsonNode field) {
        List<String> marks = new ArrayList<>();
        for (JsonNode mark : iterable(field.path("marks"))) {
            String value = mark.asText("");
            if (!value.isBlank()) {
                marks.add(value);
            }
        }
        return List.copyOf(marks);
    }

    private String invalidMessage(JsonNode field, String type) {
        String invalid = nullableText(field, "invalid");
        if (invalid != null) {
            return invalid;
        }
        String label = text(field, "label", "This field");
        if ("email".equals(type)) {
            return label + " is not a valid email address.";
        }
        if ("color".equals(type)) {
            return label + " must be a hex color like #D2A84F.";
        }
        return null;
    }

    private String normalizedColor(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String color = value.trim();
        if (!HEX_COLOR.matcher(color).matches()) {
            throw new IllegalArgumentException("Color fields must use a hex color like #D2A84F.");
        }
        return color.toUpperCase(Locale.ROOT);
    }

    private List<String> imageValues(JsonNode value, JsonNode itemSchema) {
        if (!value.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode image : iterable(value)) {
            String src = imageValue(image, itemSchema);
            if (!src.isBlank()) {
                values.add(src);
            }
        }
        return List.copyOf(values);
    }

    private String imageValue(JsonNode image, JsonNode itemSchema) {
        if (itemSchema.hasNonNull("srcPath")) {
            return fieldValue(image.at(required(itemSchema, "srcPath")), itemSchema);
        }
        return image.asText("");
    }

    private String fieldName(JsonNode field) {
        return required(field, "id");
    }

    private String repeatFieldName(JsonNode block, JsonNode field) {
        return required(block, "id") + "__" + required(field, "id");
    }

    private String required(JsonNode node, String field) {
        String value = text(node, field, "");
        if (value.isBlank()) {
            throw new IllegalStateException("Talli website schema is missing required field: " + field);
        }
        return value;
    }

    private String nullableText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? fallback : value.asText(fallback);
    }

    private int integer(JsonNode node, String field, int fallback) {
        JsonNode value = node.get(field);
        return value == null || !value.canConvertToInt() ? fallback : value.asInt();
    }

    private Iterable<JsonNode> iterable(JsonNode node) {
        return node::elements;
    }

    private String param(Map<String, String[]> params, String name) {
        String[] values = params.get(name);
        return values == null || values.length == 0 ? "" : values[0];
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String sanitize(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return normalized.isBlank() ? "image" : normalized;
    }

    private String trimSlashes(String value) {
        return value.replaceAll("^/+", "").replaceAll("/+$", "");
    }
}
