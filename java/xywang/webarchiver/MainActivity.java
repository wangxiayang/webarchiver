package xywang.webarchiver;

import android.content.Intent;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

import com.evernote.auth.EvernoteAuth;
import com.evernote.auth.EvernoteService;
import com.evernote.clients.ClientFactory;
import com.evernote.clients.NoteStoreClient;
import com.evernote.edam.error.EDAMNotFoundException;
import com.evernote.edam.error.EDAMSystemException;
import com.evernote.edam.error.EDAMUserException;
import com.evernote.edam.type.Data;
import com.evernote.edam.type.Note;
import com.evernote.edam.type.NoteAttributes;
import com.evernote.edam.type.Resource;
import com.evernote.edam.type.ResourceAttributes;
import com.evernote.thrift.TException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Whitelist;
import org.jsoup.select.Elements;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

class RetrieveArticleTask extends AsyncTask<String, String, String> {

    private TextView tv;

    RetrieveArticleTask(TextView tv) {
        super();

        this.tv = tv;
    }

    private static Cleaner cl = null;
    private static Cleaner getCleaner() {
        if (cl != null)
            return cl;
        else {
            Whitelist wl = new Whitelist();

            /* enml2 */
            wl.addAttributes("en-note", "bgcolor", "text", "xmlns");
            wl.addAttributes("en-crypt", "hint", "cipher", "length");
            wl.addAttributes("en-todo", "checked");
            wl.addAttributes("en-media", "type", "hash", "height", "width", "usemap", "align", "border", "hspace",
                    "vspace", "longdesc", "alt");
            wl.addAttributes("a","charset","type","name","href","hreflang","rel","rev","shape","coords","target");
            wl.addTags("abbr");
            wl.addTags("acronym");
            wl.addTags("address");
            wl.addAttributes("area","shape","coords","href","nohref","alt","target");
            wl.addTags("b");
            wl.addAttributes("bdo","lang","xml","dir");
            wl.addTags("big");
            wl.addAttributes("blockquote","cite");
            wl.addAttributes("br","clear");
            wl.addAttributes("caption","align");
            wl.addTags("center");
            wl.addTags("cite");
            wl.addTags("code");
            wl.addAttributes("col","span","width");
            wl.addAttributes("colgroup","span","width");
            wl.addTags("dd");
            wl.addAttributes("del","cite","datetime");
            wl.addTags("dfn");
            wl.addTags("div");
            wl.addAttributes("dl","compact");
            wl.addTags("dt");
            wl.addTags("em");
            wl.addAttributes("font","size","color","face");
            wl.addTags("h1");
            wl.addTags("h2");
            wl.addTags("h3");
            wl.addTags("h4");
            wl.addTags("h5");
            wl.addTags("h6");
            wl.addAttributes("hr","align","noshade","size","width");
            wl.addTags("i");
            wl.addAttributes("img","src","alt","name","longdesc","height","width","usemap","ismap","align","border","hspace","vspace");
            wl.addAttributes("ins","cite","datetime");
            wl.addTags("kbd");
            wl.addAttributes("li","type","value");
            wl.addAttributes("map","title","name");
            wl.addAttributes("ol","type","compact","start");
            wl.addTags("p");
            wl.addAttributes("pre","width","xml");
            wl.addAttributes("q","cite");
            wl.addTags("s");
            wl.addTags("samp");
            wl.addTags("small");
            wl.addTags("span");
            wl.addTags("strike");
            wl.addTags("strong");
            wl.addTags("sub");
            wl.addTags("sup");
            wl.addAttributes("table","summary","width","border","cellspacing","cellpadding","align","bgcolor");
            wl.addTags("tbody");
            wl.addAttributes("td","abbr","rowspan","colspan","nowrap","bgcolor","width","height");
            wl.addTags("tfoot");
            wl.addAttributes("th","abbr","rowspan","colspan","nowrap","bgcolor","width","height");
            wl.addTags("thead");
            wl.addAttributes("tr","bgcolor");
            wl.addTags("tt");
            wl.addTags("u");
            wl.addAttributes("ul","type","compact");
            wl.addTags("var");

            cl = new Cleaner(wl);
            return cl;
        }
    }

    @Override
    protected String doInBackground(String... params) {
        String url = params[0];
        long totalSize = 0;

        List<URLFilter> filters = new ArrayList<>();
        filters.add(new WeChatFilter());
        filters.add(new ThePaperFilter());
        filters.add(new ThePaperClientFilter());
        filters.add(new ZhihuFilter());
        filters.add(new ZhihuClientFilter());
        filters.add(new LTNFilter());
        filters.add(new PTTFilter());
        filters.add(new DefaultFilter());

        URLFilter filter = null;
        for (URLFilter f : filters) {
            if (f.canParse(url)) {
                filter = f;
                url = filter.getURL(url);
                break;
            }
        }

        if (filter == null) {
            System.err.println("invalid url=" + url);
            return "[Error] Invalid url: " + url;
        }

        publishProgress("Found " + filter.getType() + " link.");

        publishProgress("Connecting to the Web host...");
        Document doc = null;
        try {
            doc = Jsoup.connect(url).get();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            return "[Error] Malformed URL: " + url;
        } catch (IOException e) {
            e.printStackTrace();
            return "[Error] Failed to fetch " + url;
        }

        String title;
        String bodyInner;
        try {
            title = filter.getTitle(doc);
            bodyInner = filter.getBodyInner(doc);
        } catch (URLFilter.InvalidDocumentException e) {
            e.printStackTrace();
            return "[Error] " + e.getMessage();
        }

        publishProgress("Title: " + title);

        List<Resource> resources = new ArrayList<>();
        /* img -> en-media */
        Document bodyDoc = Jsoup.parseBodyFragment(bodyInner);
        Elements es = bodyDoc.getElementsByTag("img");
        int idx = 0;
        for (Element e : es) {
            idx++;

            Element enew = new Element("en-media");

            if (e.hasAttr("height"))
                enew.attr("height", e.attr("height"));
            if (e.hasAttr("width"))
                enew.attr("width", e.attr("width"));
            if (e.hasAttr("usemap"))
                enew.attr("usemap", e.attr("usemap"));
            if (e.hasAttr("align"))
                enew.attr("align", e.attr("align"));
            if (e.hasAttr("border"))
                enew.attr("border", e.attr("border"));
            if (e.hasAttr("hspace"))
                enew.attr("hspace", e.attr("hspace"));
            if (e.hasAttr("vspace"))
                enew.attr("vspace", e.attr("vspace"));

            String imgURL = null;

            if (e.hasAttr("data-actualsrc")) {
                enew.attr("src", e.attr("data-actualsrc"));
                imgURL = e.attr("data-actualsrc");
            } else if (e.hasAttr("data-src")) {
                enew.attr("src", e.attr("data-src"));
                imgURL = e.attr("data-src");
            } else if (e.hasAttr("data-original")) {
                enew.attr("src", e.attr("data-original"));
                imgURL = e.attr("data-original");
            } else if (e.hasAttr("src")) {
                enew.attr("src", e.attr("src"));
                imgURL = e.attr("src");
            } else {
                return "[Error] Cannot decide image source: " + e.html();
            }

            /* handle relative path */
            if (imgURL.startsWith("/")) {
                String hostname = url.substring(8);
                int slashIdx = hostname.indexOf("/");
                hostname = hostname.substring(0, slashIdx);
                imgURL = "https://" + hostname + imgURL;
                enew.attr("src", imgURL);
            }

            publishProgress("[" + idx + "/" + es.size() + "] Downloading " + imgURL + ".");

            ByteArrayOutputStream out;
            try {
                BufferedInputStream in = new BufferedInputStream(new URL(imgURL).openStream());
                byte[] block = new byte[10240];
                int len = 0;
                out = new ByteArrayOutputStream();
                while ((len = in.read(block)) >= 0) {
                    out.write(block, 0, len);
                }
                in.close();
                out.close();
            } catch (MalformedURLException e1) {
                return "[Error] Malformed URL: " + imgURL + ".\nThe element is "
                        + e;
            } catch (IOException e1) {
                e1.printStackTrace();
                return "[Error] IOException during fetching " + imgURL + ".\nThe element is "
                        + e;
            }

            publishProgress(imgURL + " has been downloaded.");

            byte[] bodyb = out.toByteArray();
            byte[] hashb;
            try {
                hashb = MessageDigest.getInstance("MD5").digest(bodyb);
            } catch (NoSuchAlgorithmException e1) {
                e1.printStackTrace();
                return "[Error] Cannot find MD5 algorithm";
            }

            Data d = new Data();
            d.setSize(bodyb.length);
            d.setBodyHash(hashb);
            d.setBody(bodyb);

            totalSize += bodyb.length;

            Resource r = new Resource();
            r.setData(d);

            String mime;
            String suffix;
            if (imgURL.contains(".jpg")
                    || imgURL.contains(".jpeg")
                    || imgURL.contains("=jpg")
                    || imgURL.contains("_jpg")
                    || imgURL.contains("=jpeg")
                    || imgURL.contains("_jpeg")) {
                mime = "image/jpeg";
                suffix = "jpeg";
            } else if (imgURL.contains(".png")
                    || imgURL.contains("=png")
                    || imgURL.contains("_png")) {
                mime = "image/png";
                suffix = "png";
            } else if (imgURL.contains(".gif")
                    || imgURL.contains("=gif")
                    || imgURL.contains("_gif")) {
                mime = "image/gif";
                suffix = "gif";
            } else {
                return "[Error] Unknown image type " + imgURL;
            }
            r.setMime(mime);

            ResourceAttributes ra = new ResourceAttributes();
            ra.setFileName(idx + "." + suffix);
            r.setAttributes(ra);
            resources.add(r);

            StringBuilder sb = new StringBuilder();
            for (byte b : hashb) {
                int intv = 0xff & b;
                if (intv < 0x10) {
                    sb.append('0');
                }
                sb.append(Integer.toHexString(intv));
            }
            String hash = sb.toString();

            enew.attr("type", mime);
            enew.attr("hash", hash);
            e.replaceWith(enew);
        }

        Document cleandoc = getCleaner().clean(bodyDoc);

        /* remove invalid href value */
        Elements aes = cleandoc.getElementsByTag("a");
        for (Element e : aes) {
            if (!e.hasAttr("href"))
                continue;

            String v = e.attr("href");
            if (!v.startsWith("https://"))
                e.attr("href", url);
        }

        Document.OutputSettings os = cleandoc.outputSettings();
        /* pretty-printing self-closing tags */
        os.syntax(Document.OutputSettings.Syntax.xml);

        publishProgress("Connecting to Evernote...");
        String noteBody = cleandoc.getElementsByTag("body").html();
        /* upload note */
        EvernoteAuth auth = new EvernoteAuth(EvernoteService.SANDBOX, Config.TOKEN);
        ClientFactory factory = new ClientFactory(auth);
        NoteStoreClient client;
        try {
            client = factory.createNoteStoreClient();
        } catch (EDAMUserException e) {
            e.printStackTrace();
            return "[Error] Evernote user exception: " + e.getMessage() + ".";
        } catch (EDAMSystemException e) {
            e.printStackTrace();
            return "[Error] Evernote system exception: " + e.getMessage() + ".";
        } catch (TException e) {
            e.printStackTrace();
            return "[Error Evernote TException: " + e.getMessage() + ".";
        }

        Note n = new Note();
        n.setTitle(title);
        for (Resource r : resources) {
            n.addToResources(r);
        }
        if (n.getAttributes() == null) {
            n.setAttributes(new NoteAttributes());
        }
        n.getAttributes().setSourceURL(url);
        n.getAttributes().setSource("xywang-app");

        String content = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<!DOCTYPE en-note SYSTEM \"http://xml.evernote.com/pub/enml2.dtd\">"
                + "<en-note>"
                + noteBody
                + "</en-note>";
        n.setContent(content);
        totalSize += content.length();

        String sizeString = null;
        if (totalSize < 1024) {
            sizeString = totalSize + " bytes";
        } else if (totalSize < 1024 * 1024) {
            sizeString = (totalSize / 1024) + "KB";
        } else if (totalSize < 1024 * 1024 * 1024) {
            sizeString = (totalSize / 1024 / 1024) + "MB";
        } else {
            sizeString = " larger than 1GB";
        }

        publishProgress("The estimated size is " + sizeString + ". Creating the note...");
        try {
            client.createNote(n);
        } catch (TException e) {
            e.printStackTrace();
            return "[Error] Evernote TException: " + e.getMessage() + ".";
        } catch (EDAMUserException e) {
            e.printStackTrace();
            return "[Error] Evernote user exception: " + e.getMessage() + ".";
        } catch (EDAMSystemException e) {
            e.printStackTrace();
            return "[Error] Evernote system exception: " + e.getMessage() + ".";
        } catch (EDAMNotFoundException e) {
            e.printStackTrace();
            return "[Error] Evernote not found exception: " + e.getMessage() + ".";
        }

        return "Successfully upload \"" + title + "\".";
    }

    @Override
    protected void onProgressUpdate(String... params) {
        String logtxt = params[0];

        tv.setText(logtxt + "\n\n" + tv.getText());
    }

    @Override
    protected void onPostExecute(String logtxt) {
        tv.setText(logtxt + "\n\n" + tv.getText());
    }
}

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView logtv = findViewById(R.id.logtv);

        /* check config */
        if ("Fill the token here".equals(Config.TOKEN)) {
            logtv.setText("[Error] Please fill the token in Config.java");
            return;
        }

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        String inputLink = null;

        if (Intent.ACTION_SEND.equals(action)
                && type != null) {
            if ("text/plain".equals(type)) {
                String text = intent.getStringExtra(Intent.EXTRA_TEXT);
                logtv.setText("Received: " + text);

                inputLink = text;
            } else {
                String text = intent.getStringExtra(Intent.EXTRA_TEXT);
                logtv.setText("[Error] Received: " + text);
            }
        }

        if (inputLink != null) {
            new RetrieveArticleTask((TextView)findViewById(R.id.logtv)).execute(inputLink);
        }
    }
}