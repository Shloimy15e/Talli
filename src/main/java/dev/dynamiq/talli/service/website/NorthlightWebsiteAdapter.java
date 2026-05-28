package dev.dynamiq.talli.service.website;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.Map;

@Component
public class NorthlightWebsiteAdapter {

    public static final String TYPE = "northlight_json_v1";

    private static final String HOME = "content/home.json";
    private static final String ABOUT = "content/about.json";
    private static final String SERVICES = "content/services.json";
    private static final String TRANSACTIONS = "content/transactions.json";
    private static final String CONTACT = "content/contact.json";

    private final ObjectMapper objectMapper;

    public NorthlightWebsiteAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<String> expectedPaths() {
        return List.of(HOME, ABOUT, SERVICES, TRANSACTIONS, CONTACT);
    }

    public NorthlightWebsiteForm toForm(Map<String, byte[]> files) {
        try {
            ObjectNode home = object(files.get(HOME));
            ObjectNode about = object(files.get(ABOUT));
            ObjectNode services = object(files.get(SERVICES));
            ObjectNode transactions = object(files.get(TRANSACTIONS));
            ObjectNode contact = object(files.get(CONTACT));

            return new NorthlightWebsiteForm(
                    text(home, "hero", "headline"),
                    text(home, "hero", "subheadline"),
                    text(home, "hero", "text"),
                    text(home, "approach", "title"),
                    text(home, "approach", "intro"),
                    pillars(home.path("approach").path("pillars")),
                    text(home, "philosophy", "title"),
                    joined(home.path("philosophy").path("paragraphs")),
                    text(home, "highlightsImage"),
                    text(home, "expertise", "title"),
                    text(home, "expertise", "subtitle"),
                    titleItems(home.path("expertise").path("categories")),
                    text(home, "impactShowcaseImage"),

                    text(about, "heading"),
                    text(about, "companyName"),
                    text(about, "intro", "title"),
                    joined(about.path("intro").path("paragraphs")),
                    text(about, "founder", "name"),
                    text(about, "founder", "title"),
                    text(about, "founder", "image", "src"),
                    joined(about.path("founder").path("bio")),
                    namedTexts(about.path("values").path("list")),
                    text(about, "whatSetsUsApart", "title"),
                    text(about, "whatSetsUsApart", "content"),
                    text(about, "choosingNorthlight", "title"),
                    text(about, "choosingNorthlight", "content"),

                    text(services, "intro", "title"),
                    text(services, "intro", "subtitle"),
                    text(services, "intro", "paragraph"),
                    services(services.path("services"), services.path("serviceImages")),

                    text(transactions, "heading"),
                    text(transactions, "subheading"),
                    transactions(transactions.path("transactions")),

                    text(contact, "heading"),
                    text(contact, "text"),
                    text(contact, "subtext"),
                    text(contact, "footer", "address"),
                    text(contact, "footer", "email"),
                    text(contact, "footer", "copyright")
            );
        } catch (IOException e) {
            throw new IllegalStateException("Could not parse website JSON.", e);
        }
    }

    public List<GithubFileChange> apply(Long projectId,
                                        Map<String, byte[]> files,
                                        Map<String, String[]> params,
                                        MultiValueMap<String, MultipartFile> uploads) {
        try {
            Map<String, ObjectNode> json = new LinkedHashMap<>();
            json.put(HOME, object(files.get(HOME)));
            json.put(ABOUT, object(files.get(ABOUT)));
            json.put(SERVICES, object(files.get(SERVICES)));
            json.put(TRANSACTIONS, object(files.get(TRANSACTIONS)));
            json.put(CONTACT, object(files.get(CONTACT)));

            applyHome(projectId, json.get(HOME), params, uploads);
            applyAbout(projectId, json.get(ABOUT), params, uploads);
            applyServices(projectId, json.get(SERVICES), params, uploads);
            applyTransactions(projectId, json.get(TRANSACTIONS), params, uploads);
            applyContact(json.get(CONTACT), params);

            List<GithubFileChange> changes = new ArrayList<>();
            changes.addAll(imageChanges(projectId, uploads));

            for (Map.Entry<String, ObjectNode> entry : json.entrySet()) {
                byte[] updated = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(entry.getValue());
                byte[] original = files.get(entry.getKey());
                if (!Arrays.equals(updated, original)) {
                    changes.add(new GithubFileChange(entry.getKey(), updated));
                }
            }

            return changes;
        } catch (IOException e) {
            throw new IllegalStateException("Could not update website content.", e);
        }
    }

    private void applyHome(Long projectId, ObjectNode home, Map<String, String[]> params,
                           MultiValueMap<String, MultipartFile> uploads) throws IOException {
        ObjectNode hero = object(home, "hero");
        put(hero, "headline", param(params, "homeHeroHeadline"));
        put(hero, "subheadline", param(params, "homeHeroSubheadline"));
        put(hero, "text", param(params, "homeHeroText"));

        ObjectNode approach = object(home, "approach");
        put(approach, "title", param(params, "homeApproachTitle"));
        put(approach, "intro", param(params, "homeApproachIntro"));
        ArrayNode pillars = array(approach, "pillars");
        for (int i = 0; i < pillars.size(); i++) {
            ObjectNode pillar = objectAt(pillars, i);
            put(pillar, "number", param(params, "homePillarNumber_" + i));
            put(pillar, "title", param(params, "homePillarTitle_" + i));
            put(pillar, "description", param(params, "homePillarDescription_" + i));
        }

        ObjectNode philosophy = object(home, "philosophy");
        put(philosophy, "title", param(params, "homePhilosophyTitle"));
        philosophy.set("paragraphs", lines(param(params, "homePhilosophyParagraphs")));

        imageParam(home, "highlightsImage", projectId, "homeHighlightsImage", "home-highlights", uploads);
        ObjectNode expertise = object(home, "expertise");
        put(expertise, "title", param(params, "homeExpertiseTitle"));
        put(expertise, "subtitle", param(params, "homeExpertiseSubtitle"));
        ArrayNode categories = array(expertise, "categories");
        for (int i = 0; i < categories.size(); i++) {
            put(objectAt(categories, i), "title", param(params, "homeExpertiseTitle_" + i));
        }
        imageParam(home, "impactShowcaseImage", projectId, "homeImpactShowcaseImage", "home-impact", uploads);
    }

    private void applyAbout(Long projectId, ObjectNode about, Map<String, String[]> params,
                            MultiValueMap<String, MultipartFile> uploads) throws IOException {
        put(about, "heading", param(params, "aboutHeading"));
        put(about, "companyName", param(params, "aboutCompanyName"));
        ObjectNode intro = object(about, "intro");
        put(intro, "title", param(params, "aboutIntroTitle"));
        intro.set("paragraphs", lines(param(params, "aboutIntroParagraphs")));

        ObjectNode founder = object(about, "founder");
        put(founder, "name", param(params, "aboutFounderName"));
        put(founder, "title", param(params, "aboutFounderTitle"));
        founder.set("bio", lines(param(params, "aboutFounderBio")));
        ObjectNode image = object(founder, "image");
        String founderImage = uploadedPublicPath(projectId, "aboutFounderImage", "about-founder", uploads);
        if (founderImage != null) {
            image.put("src", founderImage);
        }

        ArrayNode values = array(object(about, "values"), "list");
        for (int i = 0; i < values.size(); i++) {
            ObjectNode value = objectAt(values, i);
            put(value, "name", param(params, "aboutValueName_" + i));
            put(value, "description", param(params, "aboutValueDescription_" + i));
        }

        ObjectNode apart = object(about, "whatSetsUsApart");
        put(apart, "title", param(params, "aboutWhatSetsUsApartTitle"));
        put(apart, "content", param(params, "aboutWhatSetsUsApartContent"));

        ObjectNode choosing = object(about, "choosingNorthlight");
        put(choosing, "title", param(params, "aboutChoosingNorthlightTitle"));
        put(choosing, "content", param(params, "aboutChoosingNorthlightContent"));
    }

    private void applyServices(Long projectId, ObjectNode services, Map<String, String[]> params,
                               MultiValueMap<String, MultipartFile> uploads) throws IOException {
        ObjectNode intro = object(services, "intro");
        put(intro, "title", param(params, "servicesIntroTitle"));
        put(intro, "subtitle", param(params, "servicesIntroSubtitle"));
        put(intro, "paragraph", param(params, "servicesIntroParagraph"));

        ArrayNode items = array(services, "services");
        for (int i = 0; i < items.size(); i++) {
            ObjectNode item = objectAt(items, i);
            put(item, "title", param(params, "serviceTitle_" + i));
            put(item, "description", param(params, "serviceDescription_" + i));
        }

        ArrayNode images = array(services, "serviceImages");
        for (int i = 0; i < images.size(); i++) {
            String uploadName = "serviceImage_" + i;
            String publicPath = uploadedPublicPath(projectId, uploadName, "service-" + (i + 1), uploads);
            if (publicPath != null) {
                objectAt(images, i).put("src", publicPath);
            }
        }
    }

    private void applyTransactions(Long projectId, ObjectNode root, Map<String, String[]> params,
                                   MultiValueMap<String, MultipartFile> uploads) throws IOException {
        put(root, "heading", param(params, "transactionsHeading"));
        put(root, "subheading", param(params, "transactionsSubheading"));

        ArrayNode transactions = array(root, "transactions");
        for (int i = 0; i < transactions.size(); i++) {
            ObjectNode tx = objectAt(transactions, i);
            put(tx, "location", param(params, "transactionLocation_" + i));
            put(tx, "units", param(params, "transactionUnits_" + i));
            ArrayNode images = array(tx, "images");
            for (int j = 0; j < images.size(); j++) {
                String uploadName = "transactionImage_" + i + "_" + j;
                String publicPath = uploadedPublicPath(projectId, uploadName,
                        "transaction-" + (i + 1) + "-" + (j + 1), uploads);
                if (publicPath != null) {
                    images.set(j, objectMapper.getNodeFactory().textNode(publicPath));
                }
            }
        }
    }

    private void applyContact(ObjectNode contact, Map<String, String[]> params) {
        put(contact, "heading", param(params, "contactHeading"));
        put(contact, "text", param(params, "contactText"));
        put(contact, "subtext", param(params, "contactSubtext"));
        ObjectNode footer = object(contact, "footer");
        put(footer, "address", param(params, "contactAddress"));
        put(footer, "email", param(params, "contactEmail"));
        put(footer, "copyright", param(params, "contactCopyright"));
    }

    private List<GithubFileChange> imageChanges(Long projectId, MultiValueMap<String, MultipartFile> uploads) throws IOException {
        List<GithubFileChange> changes = new ArrayList<>();
        if (uploads == null) {
            return changes;
        }

        for (Map.Entry<String, List<MultipartFile>> entry : uploads.entrySet()) {
            MultipartFile file = firstPresent(entry.getValue());
            if (file == null) {
                continue;
            }

            String baseName = switch (entry.getKey()) {
                case "homeHighlightsImage" -> "home-highlights";
                case "homeImpactShowcaseImage" -> "home-impact";
                case "aboutFounderImage" -> "about-founder";
                default -> {
                    if (entry.getKey().startsWith("serviceImage_")) {
                        int index = Integer.parseInt(entry.getKey().substring("serviceImage_".length())) + 1;
                        yield "service-" + index;
                    }
                    if (entry.getKey().startsWith("transactionImage_")) {
                        String[] parts = entry.getKey().substring("transactionImage_".length()).split("_");
                        yield "transaction-" + (Integer.parseInt(parts[0]) + 1) + "-" + (Integer.parseInt(parts[1]) + 1);
                    }
                    yield sanitize(entry.getKey());
                }
            };
            changes.add(new GithubFileChange(repoImagePath(projectId, baseName, file), file.getBytes()));
        }
        return changes;
    }

    private void imageParam(ObjectNode root, String field, Long projectId, String uploadName, String baseName,
                            MultiValueMap<String, MultipartFile> uploads) throws IOException {
        String publicPath = uploadedPublicPath(projectId, uploadName, baseName, uploads);
        if (publicPath != null) {
            root.put(field, publicPath);
        }
    }

    private String uploadedPublicPath(Long projectId, String uploadName, String baseName,
                                      MultiValueMap<String, MultipartFile> uploads) throws IOException {
        if (uploads == null) {
            return null;
        }
        MultipartFile file = firstPresent(uploads.get(uploadName));
        return file == null ? null : publicImagePath(projectId, baseName, file);
    }

    private MultipartFile firstPresent(List<MultipartFile> files) {
        if (files == null) {
            return null;
        }
        return files.stream().filter(f -> f != null && !f.isEmpty()).findFirst().orElse(null);
    }

    private String repoImagePath(Long projectId, String baseName, MultipartFile file) {
        return "public" + publicImagePath(projectId, baseName, file);
    }

    private String publicImagePath(Long projectId, String baseName, MultipartFile file) {
        return "/images/cms/" + projectId + "/" + sanitize(baseName) + extension(file);
    }

    private String extension(MultipartFile file) {
        String type = file.getContentType();
        if ("image/jpeg".equalsIgnoreCase(type)) return ".jpg";
        if ("image/png".equalsIgnoreCase(type)) return ".png";
        if ("image/webp".equalsIgnoreCase(type)) return ".webp";

        String name = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return ".jpg";
        if (name.endsWith(".png")) return ".png";
        if (name.endsWith(".webp")) return ".webp";
        throw new IllegalArgumentException("Only JPG, PNG, and WEBP images are supported.");
    }

    private String sanitize(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return normalized.isBlank() ? "image" : normalized;
    }

    private ObjectNode object(byte[] bytes) throws IOException {
        return (ObjectNode) objectMapper.readTree(new String(bytes, StandardCharsets.UTF_8));
    }

    private ObjectNode object(ObjectNode parent, String field) {
        JsonNode child = parent.get(field);
        if (child instanceof ObjectNode objectNode) {
            return objectNode;
        }
        ObjectNode created = objectMapper.createObjectNode();
        parent.set(field, created);
        return created;
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

    private ObjectNode objectAt(ArrayNode array, int index) {
        JsonNode child = array.get(index);
        if (child instanceof ObjectNode objectNode) {
            return objectNode;
        }
        ObjectNode created = objectMapper.createObjectNode();
        array.set(index, created);
        return created;
    }

    private String text(JsonNode root, String... path) {
        JsonNode cursor = root;
        for (String segment : path) {
            cursor = cursor.path(segment);
        }
        return cursor.isMissingNode() || cursor.isNull() ? "" : cursor.asText();
    }

    private String joined(JsonNode array) {
        if (!array.isArray()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        array.forEach(node -> lines.add(node.asText("")));
        return String.join("\n\n", lines);
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

    private String param(Map<String, String[]> params, String name) {
        String[] values = params.get(name);
        return values == null || values.length == 0 ? "" : values[0];
    }

    private void put(ObjectNode node, String field, String value) {
        if (value != null) {
            node.put(field, value);
        }
    }

    private List<NorthlightWebsiteForm.Pillar> pillars(JsonNode array) {
        List<NorthlightWebsiteForm.Pillar> list = new ArrayList<>();
        if (array.isArray()) {
            array.forEach(node -> list.add(new NorthlightWebsiteForm.Pillar(
                    text(node, "number"), text(node, "title"), text(node, "description"))));
        }
        return list;
    }

    private List<NorthlightWebsiteForm.TitleItem> titleItems(JsonNode array) {
        List<NorthlightWebsiteForm.TitleItem> list = new ArrayList<>();
        if (array.isArray()) {
            array.forEach(node -> list.add(new NorthlightWebsiteForm.TitleItem(text(node, "title"))));
        }
        return list;
    }

    private List<NorthlightWebsiteForm.NamedText> namedTexts(JsonNode array) {
        List<NorthlightWebsiteForm.NamedText> list = new ArrayList<>();
        if (array.isArray()) {
            array.forEach(node -> list.add(new NorthlightWebsiteForm.NamedText(
                    text(node, "name"), text(node, "description"))));
        }
        return list;
    }

    private List<NorthlightWebsiteForm.ServiceItem> services(JsonNode services, JsonNode images) {
        List<NorthlightWebsiteForm.ServiceItem> list = new ArrayList<>();
        if (services.isArray()) {
            for (int i = 0; i < services.size(); i++) {
                JsonNode service = services.get(i);
                JsonNode image = images.isArray() && images.size() > i ? images.get(i) : objectMapper.createObjectNode();
                list.add(new NorthlightWebsiteForm.ServiceItem(
                        text(service, "title"), text(service, "description"), text(image, "src")));
            }
        }
        return list;
    }

    private List<NorthlightWebsiteForm.TransactionItem> transactions(JsonNode array) {
        List<NorthlightWebsiteForm.TransactionItem> list = new ArrayList<>();
        if (array.isArray()) {
            array.forEach(node -> {
                List<String> images = new ArrayList<>();
                if (node.path("images").isArray()) {
                    node.path("images").forEach(image -> images.add(image.asText("")));
                }
                list.add(new NorthlightWebsiteForm.TransactionItem(
                        text(node, "location"), text(node, "units"), images));
            });
        }
        return list;
    }
}
