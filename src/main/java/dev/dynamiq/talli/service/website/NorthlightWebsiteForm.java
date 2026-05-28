package dev.dynamiq.talli.service.website;

import java.util.List;

public record NorthlightWebsiteForm(
        String homeHeroHeadline,
        String homeHeroSubheadline,
        String homeHeroText,
        String homeApproachTitle,
        String homeApproachIntro,
        List<Pillar> homePillars,
        String homePhilosophyTitle,
        String homePhilosophyParagraphs,
        String homeHighlightsImage,
        String homeExpertiseTitle,
        String homeExpertiseSubtitle,
        List<TitleItem> homeExpertiseCategories,
        String homeImpactShowcaseImage,

        String aboutHeading,
        String aboutCompanyName,
        String aboutIntroTitle,
        String aboutIntroParagraphs,
        String aboutFounderName,
        String aboutFounderTitle,
        String aboutFounderImage,
        String aboutFounderBio,
        List<NamedText> aboutValues,
        String aboutWhatSetsUsApartTitle,
        String aboutWhatSetsUsApartContent,
        String aboutChoosingNorthlightTitle,
        String aboutChoosingNorthlightContent,

        String servicesIntroTitle,
        String servicesIntroSubtitle,
        String servicesIntroParagraph,
        List<ServiceItem> services,

        String transactionsHeading,
        String transactionsSubheading,
        List<TransactionItem> transactions,

        String contactHeading,
        String contactText,
        String contactSubtext,
        String contactAddress,
        String contactEmail,
        String contactCopyright
) {

    public record Pillar(String number, String title, String description) {
    }

    public record TitleItem(String title) {
    }

    public record NamedText(String name, String description) {
    }

    public record ServiceItem(String title, String description, String image) {
    }

    public record TransactionItem(String location, String units, List<String> images) {
    }
}
