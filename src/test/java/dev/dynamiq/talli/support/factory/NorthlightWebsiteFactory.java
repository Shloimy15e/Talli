package dev.dynamiq.talli.support.factory;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class NorthlightWebsiteFactory {

    public static final long PROJECT_ID = 77L;

    private NorthlightWebsiteFactory() {
    }

    public static Map<String, byte[]> repoFiles() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("content/home.json", bytes("""
                {
                  "hero": {"headline": "Old headline", "subheadline": "Old sub", "text": "Old text"},
                  "approach": {
                    "title": "Approach",
                    "intro": "Intro",
                    "pillars": [{"number": "1", "title": "Understand", "description": "Listen"}]
                  },
                  "philosophy": {"title": "Philosophy", "paragraphs": ["One", "Two"]},
                  "highlightsImage": "/images/home/highlights.png",
                  "expertise": {"title": "Expertise", "subtitle": "Sub", "categories": [{"title": "Office"}]},
                  "impactShowcaseImage": "/images/home/impact.jpg",
                  "customUnknownKey": "keep me"
                }
                """));
        files.put("content/about.json", bytes("""
                {
                  "heading": "About",
                  "companyName": "Northlight",
                  "intro": {"title": "Intro", "paragraphs": ["About one"]},
                  "founder": {
                    "name": "Harrison Rand",
                    "title": "Founder",
                    "image": {"src": "/images/about/profile.png"},
                    "bio": ["Bio one"]
                  },
                  "values": {"list": [{"name": "Integrity", "description": "Do right"}]},
                  "whatSetsUsApart": {"title": "Apart", "content": "Different"},
                  "choosingNorthlight": {"title": "Choose", "content": "Because"}
                }
                """));
        files.put("content/services.json", bytes("""
                {
                  "intro": {"title": "Services", "subtitle": "Sub", "paragraph": "Paragraph"},
                  "serviceImages": [{"src": "/images/services/one.png"}],
                  "services": [{"title": "Advisory", "description": "Advice"}]
                }
                """));
        files.put("content/transactions.json", bytes("""
                {
                  "heading": "Recent Transactions",
                  "subheading": "Past successes",
                  "transactions": [{"location": "Lithonia, GA", "units": "200 Units", "images": ["/images/transactions/a.jpg"]}]
                }
                """));
        files.put("content/contact.json", bytes("""
                {
                  "heading": "Contact",
                  "text": "Talk to us",
                  "subtext": "",
                  "footer": {"address": "NYC", "email": "info@example.com", "copyright": "Copyright"}
                }
                """));
        return files;
    }

    public static Map<String, String[]> formParams() {
        Map<String, String[]> params = new LinkedHashMap<>();
        params.put("homeHeroHeadline", one("Old headline"));
        params.put("homeHeroSubheadline", one("Old sub"));
        params.put("homeHeroText", one("Old text"));
        params.put("homeApproachTitle", one("Approach"));
        params.put("homeApproachIntro", one("Intro"));
        params.put("homePillars__title_0", one("Understand"));
        params.put("homePillars__description_0", one("Listen"));
        params.put("homePhilosophyTitle", one("Philosophy"));
        params.put("homePhilosophyParagraphs", one("One\n\nTwo"));
        params.put("homeExpertiseTitle", one("Expertise"));
        params.put("homeExpertiseSubtitle", one("Sub"));
        params.put("homeExpertiseCategories__title_0", one("Office"));
        params.put("aboutHeading", one("About"));
        params.put("aboutCompanyName", one("Northlight"));
        params.put("aboutIntroTitle", one("Intro"));
        params.put("aboutIntroParagraphs", one("About one"));
        params.put("aboutFounderName", one("Harrison Rand"));
        params.put("aboutFounderTitle", one("Founder"));
        params.put("aboutFounderBio", one("Bio one"));
        params.put("aboutValues__name_0", one("Integrity"));
        params.put("aboutValues__description_0", one("Do right"));
        params.put("aboutWhatSetsUsApartTitle", one("Apart"));
        params.put("aboutWhatSetsUsApartContent", one("Different"));
        params.put("aboutChoosingNorthlightTitle", one("Choose"));
        params.put("aboutChoosingNorthlightContent", one("Because"));
        params.put("servicesIntroTitle", one("Services"));
        params.put("servicesIntroSubtitle", one("Sub"));
        params.put("servicesIntroParagraph", one("Paragraph"));
        params.put("services__title_0", one("Advisory"));
        params.put("services__description_0", one("Advice"));
        params.put("services__imageExisting_0", one("/images/services/one.png"));
        params.put("transactionsHeading", one("Recent Transactions"));
        params.put("transactionsSubheading", one("Past successes"));
        params.put("transactions__location_0", one("Lithonia, GA"));
        params.put("transactions__units_0", one("200 Units"));
        params.put("transactions__imagesTouched_0", one("true"));
        params.put("transactions__imagesExisting_0_0", one("/images/transactions/a.jpg"));
        params.put("contactHeading", one("Contact"));
        params.put("contactText", one("Talk to us"));
        params.put("contactSubtext", one(""));
        params.put("contactAddress", one("NYC"));
        params.put("contactEmail", one("info@example.com"));
        params.put("contactCopyright", one("Copyright"));
        return params;
    }

    public static String[] one(String value) {
        return new String[] {value};
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
