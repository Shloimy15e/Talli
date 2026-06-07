package dev.dynamiq.talli.service.website;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dynamiq.talli.service.github.GithubFileChange;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class NorthlightWebsiteAdapter implements WebsiteContentAdapter {

    public static final String TYPE = "northlight_json_v1";

    private static final String SERVICES = "content/services.json";
    private static final String SCHEMA_RESOURCE = "website-schemas/northlight.json";

    private final ObjectMapper objectMapper;
    private final TalliWebsiteSchemaAdapter schemaAdapter;
    private final byte[] schema;
    private final List<String> contentPaths;

    public NorthlightWebsiteAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.schemaAdapter = new TalliWebsiteSchemaAdapter(objectMapper);
        this.schema = readSchema();
        this.contentPaths = schemaAdapter.additionalPaths(Map.of(TalliWebsiteSchemaAdapter.SCHEMA_PATH, schema));
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public List<String> expectedPaths() {
        return contentPaths;
    }

    @Override
    public WebsiteEditorForm toEditorForm(Map<String, byte[]> files) {
        return schemaAdapter.toEditorForm(virtualFiles(files));
    }

    @Override
    public List<GithubFileChange> apply(Long projectId,
                                        Map<String, byte[]> files,
                                        Map<String, String[]> params,
                                        MultiValueMap<String, MultipartFile> uploads) {
        return denormalizeChanges(schemaAdapter.apply(projectId, virtualFiles(files), params, uploads));
    }

    private Map<String, byte[]> virtualFiles(Map<String, byte[]> files) {
        Map<String, byte[]> virtualFiles = new LinkedHashMap<>(files);
        virtualFiles.put(TalliWebsiteSchemaAdapter.SCHEMA_PATH, schema);
        if (virtualFiles.containsKey(SERVICES)) {
            virtualFiles.put(SERVICES, normalizeServices(virtualFiles.get(SERVICES)));
        }
        return virtualFiles;
    }

    private byte[] normalizeServices(byte[] bytes) {
        try {
            ObjectNode root = object(bytes);
            ArrayNode services = array(root, "services");
            JsonNode images = root.path("serviceImages");

            for (int index = 0; index < services.size(); index++) {
                ObjectNode service = objectAtOrNew(services, index);
                JsonNode image = images.isArray() && images.size() > index
                        ? images.get(index)
                        : objectMapper.createObjectNode();
                service.set("image", image.deepCopy());
            }

            return write(root);
        } catch (IOException e) {
            throw new IllegalStateException("Could not normalize Northlight services content.", e);
        }
    }

    private List<GithubFileChange> denormalizeChanges(List<GithubFileChange> changes) {
        List<GithubFileChange> denormalized = new ArrayList<>();
        for (GithubFileChange change : changes) {
            if (SERVICES.equals(change.path())) {
                denormalized.add(new GithubFileChange(SERVICES, denormalizeServices(change.content())));
            } else {
                denormalized.add(change);
            }
        }
        return denormalized;
    }

    private byte[] denormalizeServices(byte[] bytes) {
        try {
            ObjectNode root = object(bytes);
            ArrayNode services = array(root, "services");
            ArrayNode images = objectMapper.createArrayNode();

            for (JsonNode node : services) {
                if (node instanceof ObjectNode service) {
                    JsonNode image = service.remove("image");
                    ObjectNode imageObject = image instanceof ObjectNode objectNode
                            ? objectNode.deepCopy()
                            : objectMapper.createObjectNode();
                    if (imageObject.path("src").asText("").isBlank()) {
                        imageObject.remove("src");
                    }
                    images.add(imageObject);
                } else {
                    images.add(objectMapper.createObjectNode());
                }
            }

            root.set("serviceImages", images);
            return write(root);
        } catch (IOException e) {
            throw new IllegalStateException("Could not restore Northlight services content.", e);
        }
    }

    private ObjectNode object(byte[] bytes) throws IOException {
        JsonNode json = objectMapper.readTree(new String(bytes, StandardCharsets.UTF_8));
        if (json instanceof ObjectNode objectNode) {
            return objectNode;
        }
        throw new IllegalStateException("Northlight content file must contain a JSON object.");
    }

    private ArrayNode array(ObjectNode parent, String field) {
        JsonNode child = parent.get(field);
        if (child instanceof ArrayNode arrayNode) {
            return arrayNode;
        }
        ArrayNode created = objectMapper.createArrayNode();
        parent.set(field, created);
        return created;
    }

    private ObjectNode objectAtOrNew(ArrayNode array, int index) {
        JsonNode child = array.get(index);
        if (child instanceof ObjectNode objectNode) {
            return objectNode;
        }
        ObjectNode created = objectMapper.createObjectNode();
        array.set(index, created);
        return created;
    }

    private byte[] write(ObjectNode node) throws IOException {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(node);
    }

    private byte[] readSchema() {
        ClassPathResource resource = new ClassPathResource(SCHEMA_RESOURCE);
        try (InputStream input = resource.getInputStream()) {
            return input.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read Northlight website schema.", e);
        }
    }
}
