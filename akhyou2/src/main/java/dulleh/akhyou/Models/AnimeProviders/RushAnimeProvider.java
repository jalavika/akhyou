package dulleh.akhyou.Models.AnimeProviders;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

import dulleh.akhyou.Models.Anime;
import dulleh.akhyou.Models.Episode;
import dulleh.akhyou.Models.Providers;
import dulleh.akhyou.Models.Source;
import dulleh.akhyou.Models.SourceProviders.SourceProvider;
import dulleh.akhyou.Utils.CloudFlareInitializationException;
import dulleh.akhyou.Utils.GeneralUtils;
import rx.exceptions.OnErrorThrowable;

public class RushAnimeProvider implements AnimeProvider {
    private Element animeBox;

    @Override
    public Anime fetchAnime(String url) throws OnErrorThrowable, CloudFlareInitializationException {

        //if (!CloudflareHttpClient.INSTANCE.isInitialized()) {
        //    throw new CloudFlareInitializationException();
        //}

        String body = GeneralUtils.getWebPage(url);

        animeBox = isolate(body);

        if (!hasAnime(animeBox)) {
            throw OnErrorThrowable.from(new Throwable("Failed to retrieve anime."));
        }

        Anime anime = new Anime()
                .setProviderType(Providers.RUSH)
                .setUrl(url);

        animeBox = animeBox.select("div.amin_box2").first();
        anime.setTitle(parseForTitle());

        animeBox = animeBox.select("div.desc_box_mid").first();
        anime.setImageUrl(parseForImageUrl());

        anime = parseForInformation(anime);

        anime.setEpisodes(parseForEpisodes());

        return anime;
    }

    @Override
    public Anime updateCachedAnime(Anime cachedAnime) throws OnErrorThrowable, CloudFlareInitializationException {
        Anime updatedAnime = fetchAnime(cachedAnime.getUrl());

        updatedAnime.inheritWatchedFrom(cachedAnime.getEpisodes());

        updatedAnime.setMajorColour(cachedAnime.getMajorColour());

        return updatedAnime;
    }

    @Override
    public List<Source> fetchSources(String url) throws OnErrorThrowable, CloudFlareInitializationException {

        //if (!CloudflareHttpClient.INSTANCE.isInitialized()) {
         //   throw new CloudFlareInitializationException();
        //}

        String body = GeneralUtils.getWebPage(url);

        Element sourcesBox = isolate(body)
                .select("div#episodes")
                .first();

        return parseForSources(sourcesBox);
    }

    @Override
    public Source fetchVideo(Source source) throws OnErrorThrowable, CloudFlareInitializationException {

        //if (!CloudflareHttpClient.INSTANCE.isInitialized()) {
       //     throw new CloudFlareInitializationException();
       // }

        String pageBody = GeneralUtils.getWebPage(source.getPageUrl());

        Element videoBox = isolate(pageBody)
                .select("div.player-area")
                .first();

        source.setEmbedUrl(parseForEmbedUrl(videoBox));

        if (source.getEmbedUrl().isEmpty()) {
            throw OnErrorThrowable.from(new Throwable("Video removed."));
        }

        source.setVideos(source.getSourceProvider().fetchSource(source.getEmbedUrl()));

        if (source.getVideos() == null) {
            throw OnErrorThrowable.from(new Throwable("Unsupported source."));
        }

        if (source.getVideos().isEmpty()) {
            throw OnErrorThrowable.from(new Throwable("Video retrieval failed."));
        }

        return source;
    }

    private Element isolate (String document) {
        return Jsoup
                .parse(document, Providers.RUSH_BASE_URL)
                .select("div#left-column")
                .first();
    }

    private boolean hasAnime (Element element) {
        return element.select("div.errormessage").isEmpty();
    }

    private String parseForTitle () {
        return animeBox.select("h1").text();
    }

    private String parseForImageUrl () {
        return animeBox.select("div.cat_image > object").first().attr("data");
    }

    private Anime parseForInformation (Anime anime) {
        Element element = animeBox.select("div.cat_box_desc").first();
        String catBoxDesc = element.getAllElements().text();

        String[] currentSplit = catBoxDesc.split("Status: ")[1].split("Alternative Titles: ");
        anime.setStatus(currentSplit[0]);

        currentSplit = currentSplit[1].split("Year: ");
        anime.setAlternateTitle(currentSplit[0]);

        currentSplit = currentSplit[1].split("Genres: ");
        anime.setDate(currentSplit[0]);

        currentSplit = currentSplit[1].split("Description: ");
        anime.setDesc(currentSplit[1]);

        String[] genres = currentSplit[0].split(", ");
        anime.setGenres(genres);
        anime.setGenresString(GeneralUtils.formattedGenres(genres));

        return anime;
    }

    private List<Episode> parseForEpisodes () {
        Elements episodeElements = animeBox.select("div.episode_list");
        List<Episode> episodes = new ArrayList<>(episodeElements.size());

        for (Element e : episodeElements) {
            Episode episode = new Episode();
            episode.setTitle(e.select("a.fixedLinkColor").text().replace("- ", "").replace("Watch", "").replace("now", "").trim());
            episode.setUrl(e.select("a.fixedLinkColor").attr("href"));
            episodes.add(episode);
        }

        return episodes;
    }

    private List<Source> parseForSources (Element element) {
        Elements sourceElements = element.select("div.episode1");
        List<Source> sources = new ArrayList<>(sourceElements.size());

        for (Element e : sourceElements ) {
            StringBuilder titleBuilder = new StringBuilder();
            Source source = new Source();

            Element titleAndUrlElement = e.select("h3 > a").first();

            titleBuilder.append(titleAndUrlElement.text().replace(" Video", ""));

            String lowerCaseSourceTitle = titleBuilder.toString().toLowerCase();

            SourceProvider sourceProvider = GeneralUtils.determineSourceProvider(lowerCaseSourceTitle);
            if (sourceProvider != null) {
                sources.add(addSourceTitleUrlProvider(e, titleAndUrlElement, source, sourceProvider, titleBuilder));
            }

        }

        return sources;
    }

    private Source addSourceTitleUrlProvider (Element e, Element titleAndUrlElement, Source source, SourceProvider sourceProvider, StringBuilder titleBuilder) {

        source.setPageUrl(titleAndUrlElement.attr("href"));

        if (!e.select("div.hdlogo").isEmpty() && !titleBuilder.toString().toLowerCase().contains("hd")) {
            titleBuilder.append(" HD");
        }

        if (!e.select("span.mirror-sub.subbed").isEmpty()) {
            titleBuilder.append(" SUBBED");
        } else {
            titleBuilder.append(" DUBBED");
        }

        source.setSourceProvider(sourceProvider);

        source.setTitle(titleBuilder.toString());

        return source;
    }

    private String parseForEmbedUrl (Element element) {
        return element.select("div.player-area > div > div > iframe")
                .first()
                .attr("src");
    }

}
