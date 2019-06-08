package xywang.webarchiver;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

abstract class URLFilter {

    static class InvalidDocumentException extends Exception {
        InvalidDocumentException(String msg) {
            super(msg);
        }
    }

    private String type;

    URLFilter(String type) {
        this.type = type;
    }

    public String getType() { return type; }
    abstract boolean canParse(String url);
    abstract String getURL(String url);
    abstract String getTitle(Document doc) throws InvalidDocumentException;
    abstract String getBodyInner(Document doc) throws InvalidDocumentException;
}

class WeChatFilter extends URLFilter {

    WeChatFilter() {
        super("WeChat");
    }

    @Override
    protected boolean canParse(String url) {
        return url.startsWith("https://mp.weixin.qq.com");
    }

    @Override
    String getURL(String url) {
        return url;
    }

    @Override
    protected String getTitle(Document doc) throws InvalidDocumentException {
        Element titleEle = doc.getElementById("activity-name");
        if (titleEle == null)
            throw new InvalidDocumentException("Cannot find the title element.");

        return titleEle.text();
    }

    @Override
    protected String getBodyInner(Document doc) throws InvalidDocumentException {
        Element bodyEle = doc.getElementById("js_content");
        if (bodyEle == null)
            throw new InvalidDocumentException("Cannot find the body element.");
        return bodyEle.html();
    }
}

class ThePaperFilter extends URLFilter {

    ThePaperFilter() {
        super("ThePaper");
    }

    @Override
    protected boolean canParse(String url) {
        return url.startsWith("https://m.thepaper.cn");
    }

    @Override
    String getURL(String url) {
        return url;
    }

    @Override
    String getTitle(Document doc) throws InvalidDocumentException {
        Elements titlees = doc.getElementsByClass("t_newsinfo");
        if (titlees.size() != 1)
            throw new InvalidDocumentException("Cannot locate the title element.");

        return titlees.first().text();
    }

    @Override
    String getBodyInner(Document doc) throws InvalidDocumentException {
        Elements bodyes = doc.getElementsByClass("news_part_father");
        if (bodyes.size() != 1)
            throw new InvalidDocumentException("Cannot locate the body element.");
        return bodyes.html();
    }
}

class ThePaperClientFilter extends ThePaperFilter {

    @Override
    protected boolean canParse(String url) {
        return url.startsWith("我在澎湃新闻看到一篇有意思的文章");
    }

    @Override
    String getURL(String url) {
        return url.substring(url.indexOf("https://m.thepaper.cn/"));
    }

    @Override
    String getTitle(Document doc) throws InvalidDocumentException {
        return super.getTitle(doc);
    }

    @Override
    String getBodyInner(Document doc) throws InvalidDocumentException {
        return super.getBodyInner(doc);
    }
}