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

class ZhihuFilter extends URLFilter {

    ZhihuFilter() {
        super("Zhihu");
    }

    @Override
    boolean canParse(String url) {
        return url.startsWith("https://www.zhihu.com/question/");
    }

    @Override
    String getURL(String url) {
        return url;
    }

    @Override
    String getTitle(Document doc) throws InvalidDocumentException {
        Elements titlees = doc.getElementsByClass("QuestionHeader-title");
        if (titlees.size() != 1) {
            Elements htmls = doc.getElementsByTag("html");
            if (htmls.size() != 1)
                throw new InvalidDocumentException("Cannot locate the title element.");
            Element htmle = htmls.first();
            for (Element e : htmle.children()) {
                if (e.tagName().equals("head")) {
                    for (Element headchild : e.children()) {
                        if (headchild.tagName().equals("title"))
                            return headchild.text();
                    }
                }
            }
            throw new InvalidDocumentException("Cannot locate the title element.");
        }
        return titlees.first().text();
    }

    @Override
    String getBodyInner(Document doc) throws InvalidDocumentException {
        Elements bodyes = doc.getElementsByClass("RichContent-inner");
        if (bodyes.size() != 1) {
            bodyes = doc.getElementsByClass("Post-RichTextContainer");
            if (bodyes.size() != 1) {
                Elements htmls = doc.getElementsByTag("html");
                if (htmls.size() != 1)
                    throw new InvalidDocumentException("Cannot locate the body element.");
                Element htmle = htmls.first();
                for (Element e : htmle.children()) {
                    if (e.tagName().equals("body")) {
                        return e.html();
                    }
                }
                throw new InvalidDocumentException("Cannot locate the body element.");
            } else {
                return bodyes.first().html();
            }
        }
        return bodyes.first().html();
    }
}

class ZhihuClientFilter extends ZhihuFilter {
    @Override
    boolean canParse(String url) {
        return url.contains("（分享自知乎网）");
    }

    @Override
    String getURL(String url) {
        int idx = url.indexOf("https://www.zhihu.com/question/");
        if (idx == -1)
            idx = url.indexOf("https://zhuanlan.zhihu.com/");
        else
            idx = url.indexOf("https://");

        return url.substring(idx);
    }
}

class LTNFilter extends URLFilter {

    LTNFilter() {
        super("Liberty Times");
    }

    @Override
    boolean canParse(String url) {
        return url.startsWith("https://news.ltn.com.tw/news/");
    }

    @Override
    String getURL(String url) {
        return url;
    }

    @Override
    String getTitle(Document doc) throws InvalidDocumentException {
        Elements titlePrts = doc.getElementsByClass("whitecon articlebody");
        if (titlePrts.size() != 1)
            throw new InvalidDocumentException("Cannot locate the title element.");

        Element titlePrt = titlePrts.first();
        for (Element e : titlePrt.children()) {
            if (e.tagName().equals("h1"))
                return e.text();
        }
        throw new InvalidDocumentException("Cannot locate the title element.");
    }

    @Override
    String getBodyInner(Document doc) throws InvalidDocumentException {
        Elements bodyes = doc.getElementsByAttributeValue("itemprop", "articleBody");
        if (bodyes.size() != 1)
            throw new InvalidDocumentException("Cannot locate the body element.");
        return bodyes.html();
    }
}

class PTTFilter extends URLFilter {

    PTTFilter() {
        super("PTT");
    }

    @Override
    boolean canParse(String url) {
        return url.startsWith("https://www.ptt.cc/bbs");
    }

    @Override
    String getURL(String url) {
        return url;
    }

    @Override
    String getTitle(Document doc) throws InvalidDocumentException {
        Elements htmls = doc.getElementsByTag("html");
        if (htmls.size() != 1)
            throw new InvalidDocumentException("Cannot locate the title element.");
        Element htmle = htmls.first();

        for (Element e: htmle.children()) {
            if (e.tagName().equals("head")) {
                for (Element inhead: e.children()) {
                    if (inhead.tagName().equals("title"))
                        return inhead.text();
                }
            }
        }

        throw new InvalidDocumentException("Cannot locate the title element.");
    }

    @Override
    String getBodyInner(Document doc) throws InvalidDocumentException {
        Element bodye = doc.getElementById("main-container");
        if (bodye == null)
            throw new InvalidDocumentException("Cannot find the body element.");
        return bodye.html();
    }
}

class DefaultFilter extends URLFilter {

    DefaultFilter() {
        super("Default");
    }

    @Override
    boolean canParse(String url) {
        return url.startsWith("https://");
    }

    @Override
    String getURL(String url) {
        return url;
    }

    @Override
    String getTitle(Document doc) throws InvalidDocumentException {
        Elements htmls = doc.getElementsByTag("html");
        if (htmls.size() != 1)
            throw new InvalidDocumentException("Cannot locate the title element.");
        Element htmle = htmls.first();

        for (Element e: htmle.children()) {
            if (e.tagName().equals("head")) {
                for (Element inhead: e.children()) {
                    if (inhead.tagName().equals("title"))
                        return inhead.text();
                }
            }
        }

        throw new InvalidDocumentException("Cannot locate the title element.");
    }

    @Override
    String getBodyInner(Document doc) throws InvalidDocumentException {
        Elements htmls = doc.getElementsByTag("html");
        if (htmls.size() != 1)
            throw new InvalidDocumentException("Cannot locate the body element.");
        Element htmle = htmls.first();

        for (Element e: htmle.children()) {
            if (e.tagName().equals("body")) {
                return e.html();
            }
        }

        throw new InvalidDocumentException("Cannot locate the body element.");
    }
}