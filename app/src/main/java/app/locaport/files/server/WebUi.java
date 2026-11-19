package app.locaport.files.server;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class WebUi {

    private static final String FOLDER_ICON =
            "<svg viewBox=\"0 0 24 24\" width=\"22\" height=\"22\" aria-hidden=\"true\">"
                    + "<path fill=\"currentColor\" d=\"M3 6a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v10"
                    + "a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6z\"/></svg>";
    private static final String DOWNLOAD_ICON =
            "<svg viewBox=\"0 0 24 24\" width=\"19\" height=\"19\" aria-hidden=\"true\">"
                    + "<path fill=\"currentColor\" d=\"M12 3v11l4-4 1.4 1.4L12 17.8 6.6 11.4 8 10l4 4V3z"
                    + "M5 19h14v2H5z\"/></svg>";
    private static final String LINK_ICON =
            "<svg viewBox=\"0 0 24 24\" width=\"19\" height=\"19\" aria-hidden=\"true\">"
                    + "<path fill=\"currentColor\" d=\"M10.6 13.4a1 1 0 0 1 0-1.4l3.4-3.4a3 3 0 1 1 4.2 4.2l-2 2"
                    + "a1 1 0 0 1-1.4-1.4l2-2a1 1 0 0 0-1.4-1.4L12 11.4a1 1 0 0 1-1.4 0z"
                    + "M13.4 10.6a1 1 0 0 1 0 1.4L10 15.4A3 3 0 1 1 5.8 11.2l2-2a1 1 0 0 1 1.4 1.4l-2 2"
                    + "a1 1 0 0 0 1.4 1.4l3.4-3.4a1 1 0 0 1 1.4 0z\"/></svg>";
    private static final String PENCIL_ICON =
            "<svg viewBox=\"0 0 24 24\" width=\"19\" height=\"19\" aria-hidden=\"true\">"
                    + "<path fill=\"currentColor\" d=\"M4 17.2V20h2.8L18 8.8 15.2 6 4 17.2zM20.7 6.1"
                    + "a1 1 0 0 0 0-1.4l-1.4-1.4a1 1 0 0 0-1.4 0l-1.1 1.1L19.6 7l1.1-.9z\"/></svg>";
    private static final String TRASH_ICON =
            "<svg viewBox=\"0 0 24 24\" width=\"19\" height=\"19\" aria-hidden=\"true\">"
                    + "<path fill=\"currentColor\" d=\"M6 7h12l-1 14H7L6 7zm3-4h6l1 3H8l1-3zM4 7h16v2H4z\"/></svg>";
    private static final String OPEN_ICON =
            "<svg viewBox=\"0 0 24 24\" width=\"19\" height=\"19\" aria-hidden=\"true\">"
                    + "<path fill=\"currentColor\" d=\"M10 6l6 6-6 6-1.4-1.4 4.6-4.6-4.6-4.6L10 6z\"/></svg>";

    private WebUi() {
    }

    public static String render(File root, File directory, boolean readOnly, String deviceName) throws IOException {
        String base = FileOps.relativePath(root, directory);
        String baseUrl = encodeHref(base);
        if (!baseUrl.endsWith("/")) {
            baseUrl = baseUrl + "/";
        }

        List<File> children = new ArrayList<>();
        File[] listed = directory.listFiles();
        if (listed != null) {
            Collections.addAll(children, listed);
        }
        children.sort((a, b) -> {
            if (a.isDirectory() != b.isDirectory()) {
                return a.isDirectory() ? -1 : 1;
            }
            return a.getName().compareToIgnoreCase(b.getName());
        });

        StringBuilder rows = new StringBuilder(4096);
        if (!"/".equals(base)) {
            rows.append("<div class=\"row dir\" data-name=\"..\" data-size=\"0\" data-time=\"0\"")
                    .append(" data-path=\"").append(escapeAttribute(parentOf(baseUrl)))
                    .append("\" data-dir=\"1\">")
                    .append("<span class=\"ico folder\">").append(FOLDER_ICON).append("</span>")
                    .append("<a class=\"name\" href=\"").append(escapeAttribute(parentOf(baseUrl)))
                    .append("\">Parent folder</a>")
                    .append("<span class=\"meta\"></span><span class=\"date\"></span>")
                    .append("<span class=\"actions\"><a class=\"iconbtn\" href=\"")
                    .append(escapeAttribute(parentOf(baseUrl)))
                    .append("\" title=\"Open\">").append(OPEN_ICON).append("</a></span></div>");
        }

        for (File child : children) {
            boolean isDir = child.isDirectory();
            String name = child.getName();
            String path = baseUrl + FileOps.encodeSegment(name) + (isDir ? "/" : "");
            String extension = isDir ? "" : extensionOf(name);
            String badge = extension.isEmpty() ? "FILE" : extension.toUpperCase(Locale.US);
            String iconClass = isDir ? "folder" : "file " + extensionClass(extension);

            rows.append("<div class=\"row").append(isDir ? " dir" : "").append("\" data-name=\"")
                    .append(escapeAttribute(name.toLowerCase(Locale.US)))
                    .append("\" data-size=\"").append(child.length())
                    .append("\" data-time=\"").append(child.lastModified())
                    .append("\" data-path=\"").append(escapeAttribute(path))
                    .append("\" data-dir=\"").append(isDir ? "1" : "0").append("\">")
                    .append("<span class=\"ico ").append(iconClass).append("\">")
                    .append(isDir ? FOLDER_ICON : "<b>" + escapeHtml(badge) + "</b>")
                    .append("</span>")
                    .append("<a class=\"name\" href=\"").append(escapeAttribute(path)).append("\"")
                    .append(isDir ? "" : " download").append(">")
                    .append(escapeHtml(name)).append("</a>")
                    .append("<span class=\"meta\">")
                    .append(isDir ? "" : FileOps.humanSize(child.length())).append("</span>")
                    .append("<span class=\"date\">")
                    .append(isDir ? "" : dateLabel(child.lastModified())).append("</span>")
                    .append("<span class=\"actions\">")
                    .append(isDir
                            ? "<a class=\"iconbtn\" href=\"" + escapeAttribute(path) + "\" title=\"Open\">" + OPEN_ICON + "</a>"
                            : "<a class=\"iconbtn\" href=\"" + escapeAttribute(path) + "\" download title=\"Download\">" + DOWNLOAD_ICON + "</a>")
                    .append("<button type=\"button\" class=\"iconbtn\" data-act=\"copy\" title=\"Copy link\">")
                    .append(LINK_ICON).append("</button>");
            if (!readOnly) {
                if (!isDir) {
                    rows.append("<button type=\"button\" class=\"iconbtn\" data-act=\"rename\" title=\"Rename\">")
                            .append(PENCIL_ICON).append("</button>");
                }
                rows.append("<button type=\"button\" class=\"iconbtn danger\" data-act=\"delete\" title=\"Delete\">")
                        .append(TRASH_ICON).append("</button>");
            }
            rows.append("</span></div>");
        }
        if (children.isEmpty()) {
            rows.append("<div class=\"empty\"><span>This folder is empty</span>"
                    + "<small>Drop files here, or use Upload.</small></div>");
        }

        String controls = "<button type=\"button\" class=\"btn ghost\" id=\"view\" title=\"Toggle layout\">Grid</button>"
                + "<select id=\"sort\" class=\"btn ghost\" title=\"Sort\">"
                + "<option value=\"name\">Name</option>"
                + "<option value=\"new\">Newest</option>"
                + "<option value=\"size\">Largest</option></select>";

        String toolbar;
        if (readOnly) {
            toolbar = "<label class=\"search\"><span class=\"searchIcon\">" + SEARCH_ICON + "</span>"
                    + "<input id=\"q\" type=\"search\" placeholder=\"Filter files\" autocomplete=\"off\"></label>"
                    + controls
                    + "<span class=\"pill\">Read-only</span>";
        } else {
            toolbar = "<label class=\"btn primary\">Upload"
                    + "<input id=\"f\" type=\"file\" multiple></label>"
                    + "<button type=\"button\" class=\"btn\" onclick=\"newFolder()\">New folder</button>"
                    + "<label class=\"search\"><span class=\"searchIcon\">" + SEARCH_ICON + "</span>"
                    + "<input id=\"q\" type=\"search\" placeholder=\"Filter files\" autocomplete=\"off\"></label>"
                    + controls;
        }

        return PAGE
                .replace("{{BASE}}", escapeJs(baseUrl))
                .replace("{{DEVICE}}", escapeHtml(deviceName))
                .replace("{{CRUMBS}}", breadcrumbs(baseUrl))
                .replace("{{ROWS}}", rows.toString())
                .replace("{{TOOLBAR}}", toolbar)
                .replace("{{COUNT}}", String.valueOf(children.size()))
                .replace("{{READ_ONLY}}", readOnly ? "true" : "false");
    }

    private static String breadcrumbs(String baseUrl) {
        StringBuilder out = new StringBuilder();
        out.append("<a href=\"/\">Storage</a>");
        if ("/".equals(baseUrl)) {
            return out.toString();
        }
        String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String[] parts = trimmed.substring(1).split("/");
        StringBuilder path = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            path.append('/').append(part);
            out.append("<span class=\"sep\">/</span><a href=\"")
                    .append(escapeAttribute(path.toString() + "/"))
                    .append("\">").append(escapeHtml(safeDecode(part))).append("</a>");
        }
        return out.toString();
    }

    private static String safeDecode(String value) {
        try {
            return java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return value;
        }
    }

    private static String parentOf(String baseUrl) {
        String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        int slash = trimmed.lastIndexOf('/');
        return slash <= 0 ? "/" : trimmed.substring(0, slash + 1);
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.US);
    }

    private static String extensionClass(String extension) {
        switch (extension) {
            case "jpg":
            case "jpeg":
            case "png":
            case "gif":
            case "webp":
            case "heic":
            case "bmp":
            case "svg":
                return "media image";
            case "mp4":
            case "mkv":
            case "webm":
            case "mov":
            case "avi":
            case "3gp":
                return "media video";
            case "mp3":
            case "m4a":
            case "flac":
            case "wav":
            case "ogg":
            case "opus":
                return "media audio";
            case "pdf":
            case "doc":
            case "docx":
            case "txt":
            case "md":
            case "xls":
            case "xlsx":
            case "ppt":
            case "pptx":
                return "doc";
            case "zip":
            case "rar":
            case "7z":
            case "tar":
            case "gz":
            case "apk":
                return "archive";
            case "xml":
            case "json":
            case "html":
            case "js":
            case "css":
            case "java":
            case "py":
            case "sh":
                return "code";
            default:
                return "other";
        }
    }

    private static String dateLabel(long epochMillis) {
        return new SimpleDateFormat("d MMM yyyy", Locale.US).format(new Date(epochMillis));
    }

    private static String encodeHref(String path) {
        String[] parts = path.split("/", -1);
        StringBuilder out = new StringBuilder(path.length() + 16);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                out.append('/');
            }
            out.append(FileOps.encodeSegment(parts[i]));
        }
        String encoded = out.toString();
        return encoded.startsWith("/") ? encoded : "/" + encoded;
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String escapeAttribute(String value) {
        return escapeHtml(value).replace("\"", "&quot;");
    }

    private static String escapeJs(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("</", "<\\/");
    }

    private static final String SEARCH_ICON =
            "<svg viewBox=\"0 0 24 24\" width=\"18\" height=\"18\" aria-hidden=\"true\">"
                    + "<path fill=\"currentColor\" d=\"M10 4a6 6 0 1 0 3.5 10.9l4.3 4.3 1.4-1.4-4.3-4.3A6 6 0 0 0 10 4z"
                    + "m0 2a4 4 0 1 1 0 8 4 4 0 0 1 0-8z\"/></svg>";

    private static final String PAGE = """
            <!doctype html>
            <html lang="en">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
            <meta name="theme-color" content="#0b3d2e">
            <title>{{DEVICE}} · Locaport</title>
            <style>
            :root{
              color-scheme:light dark;
              --bg:#f4f7f5; --card:#fff; --ink:#121a16; --muted:#5f6f67; --line:#e6eeea;
              --brand:#0b3d2e; --brand-ink:#e9f6ef; --accent:#1f7a57; --danger:#b3261e;
              --shadow:0 1px 2px rgba(16,40,30,.06),0 12px 32px rgba(16,40,30,.06);
              --radius:16px;
            }
            *{box-sizing:border-box}
            html,body{height:100%}
            body{margin:0;font-family:ui-sans-serif,system-ui,-apple-system,"Segoe UI",Roboto,Inter,sans-serif;
              background:var(--bg);color:var(--ink);-webkit-font-smoothing:antialiased}
            a{color:inherit}
            header{position:sticky;top:0;z-index:20;background:color-mix(in srgb,var(--brand) 94%,black);color:var(--brand-ink);
              box-shadow:0 8px 24px rgba(0,0,0,.12)}
            .bar{display:flex;align-items:center;gap:14px;width:100%;max-width:1040px;margin:0 auto;
              padding:14px 16px}
            .brand{display:flex;align-items:center;gap:9px;font-weight:720;letter-spacing:.2px}
            .dot{width:10px;height:10px;border-radius:3px;background:#7ce3b0;box-shadow:0 0 0 4px rgba(124,227,176,.16)}
            .crumbs{display:flex;align-items:center;gap:2px;min-width:0;font-size:13px;color:#bcd8c9;
              overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
            .crumbs a{color:#eaf7f0;text-decoration:none;padding:3px 2px;border-radius:6px}
            .crumbs a:hover{background:rgba(255,255,255,.1)}
            .crumbs .sep{margin:0 5px;color:#7ba893}
            main{max-width:1040px;margin:0 auto;padding:18px 16px 40px}
            .toolbar{display:flex;gap:9px;align-items:center;flex-wrap:wrap;margin-bottom:14px}
            .btn{display:inline-flex;align-items:center;justify-content:center;gap:7px;border:0;
              border-radius:999px;padding:10px 16px;font:inherit;font-size:13.5px;font-weight:650;
              background:var(--card);color:var(--ink);cursor:pointer;box-shadow:var(--shadow);
              transition:transform .12s ease,background .15s ease,color .15s ease}
            .btn:hover{transform:translateY(-1px)}
            .btn:active{transform:translateY(0) scale(.985)}
            .btn.primary{background:var(--brand);color:#fff}
            .btn.primary:hover{background:#0e4c39}
            .btn.ghost{background:transparent;box-shadow:none;border:1px solid var(--line)}
            select.btn{appearance:none;padding-right:32px;
              background-image:linear-gradient(45deg,transparent 50%,var(--muted) 50%),linear-gradient(135deg,var(--muted) 50%,transparent 50%);
              background-position:calc(100% - 17px) 52%,calc(100% - 12px) 52%;
              background-size:5px 5px,5px 5px;background-repeat:no-repeat}
            .pill{font-size:12px;padding:6px 11px;border-radius:999px;background:color-mix(in srgb,var(--accent) 14%,transparent);
              color:var(--accent);font-weight:650}
            input[type=file]{display:none}
            .search{display:flex;align-items:center;gap:8px;flex:1;min-width:190px;background:var(--card);
              border:1px solid var(--line);border-radius:12px;padding:0 12px;box-shadow:var(--shadow)}
            .search input{border:0;outline:0;background:transparent;color:inherit;font:inherit;
              padding:11px 0;width:100%}
            .searchIcon{display:flex;color:var(--muted)}
            .card{background:var(--card);border-radius:var(--radius);box-shadow:var(--shadow);overflow:hidden}
            .row{display:grid;grid-template-columns:34px minmax(0,1fr) 76px 92px auto;gap:12px;
              align-items:center;padding:11px 14px;border-bottom:1px solid var(--line);
              transition:background .12s ease}
            .row:last-child{border-bottom:0}
            .row:hover{background:color-mix(in srgb,var(--accent) 6%,transparent)}
            .ico{display:flex;align-items:center;justify-content:center;width:30px;height:30px;border-radius:9px;
              color:var(--brand);background:color-mix(in srgb,var(--accent) 10%,transparent);font-size:9px;
              font-weight:800;letter-spacing:.3px}
            .ico b{font-size:9px}
            .ico.folder{background:color-mix(in srgb,var(--accent) 16%,transparent)}
            .ico.image{color:#7a4b00;background:#fff1d6}
            .ico.video{color:#6a1b9a;background:#f3e5f5}
            .ico.audio{color:#00695c;background:#e0f2f1}
            .ico.doc{color:#1565c0;background:#e3f2fd}
            .ico.archive{color:#8d6e63;background:#efebe9}
            .ico.code{color:#37474f;background:#eceff1}
            .name{text-decoration:none;font-weight:600;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
            .name:hover{text-decoration:underline;text-underline-offset:3px}
            .meta,.date{font-size:12.5px;color:var(--muted);font-variant-numeric:tabular-nums;white-space:nowrap;text-align:right}
            .actions{display:flex;gap:2px;align-items:center;justify-content:flex-end;opacity:.75}
            .row:hover .actions{opacity:1}
            .iconbtn{display:inline-flex;align-items:center;justify-content:center;width:34px;height:34px;
              border:0;border-radius:10px;background:transparent;color:var(--muted);cursor:pointer;
              text-decoration:none;transition:background .12s ease,color .12s ease}
            .iconbtn:hover{background:color-mix(in srgb,var(--accent) 12%,transparent);color:var(--ink)}
            .iconbtn.danger:hover{background:color-mix(in srgb,var(--danger) 12%,transparent);color:var(--danger)}
            .empty{display:flex;flex-direction:column;align-items:center;gap:6px;padding:44px 16px;color:var(--muted)}
            .empty span{font-weight:650;color:var(--ink)}
            .empty small{font-size:12.5px}
            .footer{display:flex;justify-content:space-between;align-items:center;margin-top:12px;
              font-size:12px;color:var(--muted);padding:0 4px}
            #drop{position:fixed;inset:0;z-index:50;display:none;align-items:center;justify-content:center;
              background:rgba(11,61,46,.55);backdrop-filter:blur(3px)}
            #drop.on{display:flex}
            #drop .zone{display:flex;flex-direction:column;align-items:center;gap:10px;padding:34px 46px;
              border:2px dashed #bdf3d7;border-radius:22px;background:rgba(11,61,46,.85);color:#eafff5;
              font-weight:700;font-size:16px}
            #prog{height:4px;border-radius:999px;background:var(--line);overflow:hidden;margin:0 0 12px;display:none}
            #prog.on{display:block}
            #bar{height:100%;width:0;background:var(--accent);transition:width .15s ease}
            #toast{position:fixed;left:50%;bottom:26px;transform:translate(-50%,20px);z-index:60;
              background:#111c17;color:#ecfff5;padding:11px 18px;border-radius:999px;font-size:13px;
              box-shadow:0 12px 30px rgba(0,0,0,.25);opacity:0;pointer-events:none;transition:.2s ease}
            #toast.show{opacity:1;transform:translate(-50%,0)}
            .list.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(170px,1fr));gap:12px;background:transparent;box-shadow:none}
            .list.grid .row{display:flex;flex-direction:column;align-items:flex-start;gap:8px;border:0;
              background:var(--card);border-radius:14px;padding:14px;box-shadow:var(--shadow)}
            .list.grid .meta,.list.grid .date{display:none}
            .list.grid .name{max-width:100%;font-size:13.5px}
            .list.grid .actions{opacity:1;margin-top:auto}
            @media(max-width:680px){.date{display:none}.row{grid-template-columns:32px minmax(0,1fr) auto}}
            @media(prefers-color-scheme:dark){
              :root{--bg:#0d1411;--card:#16211c;--ink:#e8f1eb;--muted:#90a49a;--line:#22322a;--brand:#0b241b;
                --brand-ink:#e9f6ef;--accent:#7ce3b0;--danger:#ff8a80;--shadow:none}
              header{background:#0b241b}
              .btn.primary{background:#7ce3b0;color:#082016}
              .btn.primary:hover{background:#8ff0bf}
              .ico.image{color:#ffd78a;background:#3a2c14}
              .ico.video{color:#e2b6ff;background:#2e1a3b}
              .ico.audio{color:#9ff0e4;background:#12312c}
              .ico.doc{color:#9fc9ff;background:#14273d}
              .ico.archive{color:#d7c2b8;background:#2b211d}
              .ico.code{color:#cfe0e6;background:#1d2629}
              #drop .zone{background:rgba(8,32,22,.92)}
            }
            </style>
            </head>
            <body>
            <header>
              <div class="bar">
                <span class="brand"><span class="dot"></span>{{DEVICE}}</span>
                <nav class="crumbs">{{CRUMBS}}</nav>
              </div>
            </header>
            <main>
              <div class="toolbar">{{TOOLBAR}}</div>
              <div id="prog"><div id="bar"></div></div>
              <div id="list" class="card list">{{ROWS}}</div>
              <div class="footer">
                <span>{{COUNT}} items</span>
                <span>Locaport · local only</span>
              </div>
            </main>
            <div id="drop"><div class="zone">Drop files to upload</div></div>
            <div id="toast"></div>
            <script>
            const BASE="{{BASE}}";
            const RO={{READ_ONLY}};
            const $=s=>document.querySelector(s);
            const Q=$("#q"), LIST=$("#list");
            const enc=n=>encodeURIComponent(n);
            function toast(msg){const t=$("#toast");t.textContent=msg;t.classList.add("show");
              clearTimeout(t._h);t._h=setTimeout(()=>t.classList.remove("show"),2400);}
            function filter(){const v=(Q&&Q.value||"").toLowerCase();
              document.querySelectorAll("#list .row").forEach(r=>{
                const n=r.getAttribute("data-name")||"";r.style.display=n.includes(v)?"":"none";});}
            if(Q){Q.addEventListener("input",filter);
              document.addEventListener("keydown",e=>{if(e.key==="/"&&document.activeElement!==Q){e.preventDefault();Q.focus();}});}
            function newFolder(){
              if(RO)return;
              const name=prompt("Folder name");if(!name)return;
              fetch(BASE+enc(name),{method:"MKCOL"}).then(r=>{
                if(r.ok||r.status===201)location.reload();else toast("Could not create folder ("+r.status+")");});
            }
            function uploadFiles(files){
              if(RO||!files||!files.length)return;
              const prog=$("#prog"),bar=$("#bar"),total=files.length;let i=0;
              prog.classList.add("on");
              (function next(){
                if(i>=total){location.reload();return;}
                const f=files[i],xhr=new XMLHttpRequest();
                xhr.open("PUT",BASE+enc(f.name));
                xhr.upload.onprogress=e=>{if(e.lengthComputable){
                  bar.style.width=(((i+e.loaded/e.total)/total)*100).toFixed(1)+"%";}};
                xhr.onload=()=>{if(xhr.status>=200&&xhr.status<300){i++;next();}
                  else{toast("Upload failed: "+f.name+" ("+xhr.status+")");prog.classList.remove("on");}};
                xhr.onerror=()=>{toast("Upload failed: "+f.name);prog.classList.remove("on");};
                xhr.send(f);
              })();
            }
            const F=$("#f");if(F){F.addEventListener("change",()=>uploadFiles(F.files));}
            const V=$("#view");
            if(V){V.addEventListener("click",()=>{LIST.classList.toggle("grid");
              V.textContent=LIST.classList.contains("grid")?"List":"Grid";});}
            const S=$("#sort");
            if(S){S.addEventListener("change",()=>{
              const rows=Array.from(LIST.querySelectorAll(".row"));
              const parent=rows.find(r=>r.getAttribute("data-name")==="..");
              const rest=rows.filter(r=>r!==parent);
              const key=S.value;
              rest.sort((a,b)=>key==="new"?+b.dataset.time-+a.dataset.time
                :key==="size"?+b.dataset.size-+a.dataset.size
                :(a.dataset.name||"").localeCompare(b.dataset.name||""));
              if(parent)LIST.appendChild(parent);
              rest.forEach(r=>LIST.appendChild(r));
            });}
            let dragDepth=0;
            addEventListener("dragenter",e=>{if(RO)return;e.preventDefault();dragDepth++;
              $("#drop").classList.add("on");});
            addEventListener("dragover",e=>{if(RO)return;e.preventDefault();});
            addEventListener("dragleave",e=>{if(RO)return;if(--dragDepth<=0){dragDepth=0;$("#drop").classList.remove("on");}});
            addEventListener("drop",e=>{if(RO)return;e.preventDefault();dragDepth=0;
              $("#drop").classList.remove("on");uploadFiles(e.dataTransfer.files);});
            document.addEventListener("click",e=>{
              const b=e.target.closest("button[data-act]");if(!b)return;
              const row=b.closest(".row");if(!row)return;
              const path=row.getAttribute("data-path"),name=row.getAttribute("data-name");
              const isDir=row.getAttribute("data-dir")==="1";
              const act=b.getAttribute("data-act");
              if(act==="copy"){
                const url=location.origin+path;
                (navigator.clipboard?navigator.clipboard.writeText(url):Promise.reject())
                  .then(()=>toast("Link copied")).catch(()=>prompt("Copy this link",url));
              } else if(act==="delete"){
                if(!confirm("Delete "+(isDir?"folder ":"")+name+"?"))return;
                fetch(path,{method:"DELETE"}).then(r=>{
                  if(r.ok||r.status===204)location.reload();else toast("Delete failed ("+r.status+")");});
              } else if(act==="rename"){
                const next=prompt("New name",name);if(!next||next===name)return;
                const parent=path.slice(0,path.lastIndexOf("/",path.length-2)+1);
                const dest=parent+enc(next)+(isDir?"/":"");
                fetch(path,{method:"MOVE",headers:{Destination:location.origin+dest,Overwrite:"T"}})
                  .then(r=>{if(r.ok||r.status===201)location.reload();else toast("Rename failed ("+r.status+")");});
              }
            });
            </script>
            </body>
            </html>
            """;
}
