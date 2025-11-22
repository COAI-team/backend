package kr.or.kosa.backend.toobar.service;

import java.net.URI;

import kr.or.kosa.backend.toobar.dto.LinkPreviewResponse;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;


@Service
public class LinkPreviewService {

    public LinkPreviewResponse fetchPreview(String url) throws Exception {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }

        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0")
                .timeout(5000)
                .get();

        String title = getMetaTag(doc, "meta[property=og:title]");
        if (title == null || title.isEmpty()) {
            title = doc.title();
        }

        String description = getMetaTag(doc, "meta[property=og:description]");
        if (description == null || description.isEmpty()) {
            description = getMetaTag(doc, "meta[name=description]");
        }

        String image = getMetaTag(doc, "meta[property=og:image]");

        String siteName = getMetaTag(doc, "meta[property=og:site_name]");
        if (siteName == null || siteName.isEmpty()) {
            try {
                URI uri = new URI(url);
                siteName = uri.getHost();
            } catch (Exception e) {
                siteName = "";
            }
        }

        return new LinkPreviewResponse(title, description, image, siteName, url);
    }

    private String getMetaTag(Document doc, String cssQuery) {
        Element element = doc.selectFirst(cssQuery);
        if (element == null) return null;
        return element.attr("content");
    }
}
