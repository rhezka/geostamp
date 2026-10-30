import SwiftUI
import WebKit
import Photos
import Network
import CoreLocation

@main
struct GeoStampApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView().ignoresSafeArea(.all, edges: .bottom)
        }
    }
}

struct ContentView: UIViewRepresentable {
    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.allowsInlineMediaPlayback = true
        config.mediaTypesRequiringUserActionForPlayback = []
        // cadangan bila halaman dibuka lewat file://
        config.preferences.setValue(true, forKey: "allowFileAccessFromFileURLs")
        config.setValue(true, forKey: "allowUniversalAccessFromFileURLs")
        config.userContentController.add(context.coordinator, name: "savePhoto")
        config.userContentController.add(context.coordinator, name: "getLocation")

        let web = WKWebView(frame: .zero, configuration: config)
        web.isOpaque = false
        web.backgroundColor = UIColor(red: 0.059, green: 0.067, blue: 0.082, alpha: 1)
        web.scrollView.bounces = false
        web.allowsBackForwardNavigationGestures = false
        web.navigationDelegate = context.coordinator
        web.uiDelegate = context.coordinator

        context.coordinator.ensureLocationPermission()
        if let webDir = Bundle.main.url(forResource: "Web", withExtension: nil) {
            context.coordinator.startApp(web: web, webDir: webDir)
        }
        return web
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator() }

    final class Coordinator: NSObject, WKNavigationDelegate, WKUIDelegate, WKScriptMessageHandler, CLLocationManagerDelegate {
        var server: LocalServer?
        var webDir: URL?
        var portIndex = 0
        let ports: [UInt16] = [18923, 18924, 18925]

        func startApp(web: WKWebView, webDir: URL) {
            self.webDir = webDir
            loadCurrent(into: web)
        }

        private func loadCurrent(into web: WKWebView) {
            guard portIndex < ports.count, let dir = webDir else {
                // cadangan terakhir: file:// seperti sebelumnya
                if let dir = webDir {
                    web.loadFileURL(dir.appendingPathComponent("index.html"),
                                    allowingReadAccessTo: dir)
                }
                return
            }
            let port = ports[portIndex]
            let server = LocalServer(root: dir)
            self.server = server
            server.start(port: port)
            if let url = URL(string: "http://127.0.0.1:\(port)/index.html") {
                web.load(URLRequest(url: url))
            }
        }

        func webView(_ webView: WKWebView,
                     didFailProvisionalNavigation navigation: WKNavigation!,
                     withError error: Error) {
            portIndex += 1
            loadCurrent(into: webView)
        }

        // izin kamera/galeri untuk input file di dalam webview
        func webView(_ webView: WKWebView,
                     requestMediaCapturePermissionFor origin: WKSecurityOrigin,
                     initiatedByFrame frame: WKFrameInfo,
                     type: WKMediaCaptureType,
                     decisionHandler: @escaping (WKPermissionDecision) -> Void) {
            decisionHandler(.grant)
        }

        var locationManager: CLLocationManager?
        var pendingWebView: WKWebView?

        func ensureLocationPermission() {
            let m = locationManager ?? CLLocationManager()
            locationManager = m
            m.delegate = self
            if m.authorizationStatus == .notDetermined {
                m.requestWhenInUseAuthorization()
            }
        }

        // halaman minta titik GPS lewat jalur bawaan (WebView sering menggantung)
        func fetchLocation(webView: WKWebView) {
            if locationManager == nil { ensureLocationPermission() }
            guard let m = locationManager else {
                webView.evaluateJavaScript("window.geostampNativeLocation(null,null,'unavailable')",
                                           completionHandler: nil)
                return
            }
            switch m.authorizationStatus {
            case .authorizedAlways, .authorizedWhenInUse:
                pendingWebView = webView
                m.desiredAccuracy = kCLLocationAccuracyBest
                m.requestLocation()
                DispatchQueue.main.asyncAfter(deadline: .now() + 20) { [weak self] in
                    guard let self = self, let web = self.pendingWebView else { return }
                    self.pendingWebView = nil
                    web.evaluateJavaScript("window.geostampNativeLocation(null,null,'timeout')",
                                           completionHandler: nil)
                }
            case .notDetermined:
                pendingWebView = webView
                m.requestWhenInUseAuthorization()
            default:
                webView.evaluateJavaScript("window.geostampNativeLocation(null,null,'denied')",
                                           completionHandler: nil)
            }
        }

        func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
            guard pendingWebView != nil else { return }
            switch manager.authorizationStatus {
            case .authorizedAlways, .authorizedWhenInUse:
                manager.desiredAccuracy = kCLLocationAccuracyBest
                manager.requestLocation()
            case .denied, .restricted:
                if let web = pendingWebView {
                    pendingWebView = nil
                    web.evaluateJavaScript("window.geostampNativeLocation(null,null,'denied')",
                                           completionHandler: nil)
                }
            default:
                break
            }
        }

        func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
            guard let web = pendingWebView, let loc = locations.last else { return }
            pendingWebView = nil
            web.evaluateJavaScript(
                "window.geostampNativeLocation(\(loc.coordinate.latitude),\(loc.coordinate.longitude),null)",
                completionHandler: nil)
        }

        func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
            guard let web = pendingWebView else { return }
            pendingWebView = nil
            web.evaluateJavaScript("window.geostampNativeLocation(null,null,'unavailable')",
                                   completionHandler: nil)
        }

        // halaman mengirim gambar jadi (base64) untuk disimpan ke galeri
        func userContentController(_ userContentController: WKUserContentController,
                                   didReceive message: WKScriptMessage) {
            if message.name == "getLocation" {
                if let web = message.webView { fetchLocation(webView: web) }
                return
            }
            guard message.name == "savePhoto",
                  let base64 = message.body as? String,
                  let data = Data(base64Encoded: base64),
                  let image = UIImage(data: data) else { return }

            PHPhotoLibrary.requestAuthorization(for: .addOnly) { status in
                guard status == .authorized || status == .limited else { return }
                PHPhotoLibrary.shared().performChanges({
                    PHAssetChangeRequest.creationRequestForAsset(from: image)
                })
            }
        }
    }
}

/// Server HTTP mungil di localhost: menyajikan berkas halaman supaya WebView
/// bekerja dengan origin http biasa (CORS, cache, dan service worker normal),
/// bukan file:// yang serba dibatasi.
final class LocalServer {
    private let root: URL
    private var listener: NWListener?
    private let queue = DispatchQueue(label: "geostamp.http")

    init(root: URL) { self.root = root }

    func start(port: UInt16) {
        stop()
        do {
            let listener = try NWListener(using: .tcp, on: NWEndpoint.Port(rawValue: port)!)
            self.listener = listener
            listener.newConnectionHandler = { [weak self] conn in
                guard let self = self else { return }
                conn.start(queue: self.queue)
                self.receive(on: conn)
            }
            listener.start(queue: queue)
        } catch { }
    }

    func stop() { listener?.cancel(); listener = nil }

    private func receive(on conn: NWConnection, acc: Data = Data()) {
        conn.receive(minimumIncompleteLength: 1, maximumLength: 65536) { [weak self] data, _, _, _ in
            guard let self = self else { conn.cancel(); return }
            var buf = acc
            if let data = data { buf.append(data) }
            if buf.range(of: Data("\r\n\r\n".utf8)) != nil {
                self.respond(on: conn, request: buf)
            } else if buf.count < 65536 {
                self.receive(on: conn, acc: buf)
            } else {
                conn.cancel()
            }
        }
    }

    private func respond(on conn: NWConnection, request: Data) {
        let text = String(data: request, encoding: .utf8) ?? ""
        let line = text.components(separatedBy: "\r\n").first ?? ""
        let parts = line.split(separator: " ")
        var path = parts.count >= 2 ? String(parts[1]) : "/"
        if let q = path.firstIndex(of: "?") { path = String(path[..<q]) }
        path = path.removingPercentEncoding ?? path
        if path.hasSuffix("/") { path += "index.html" }

        let rel = path.hasPrefix("/") ? String(path.dropFirst()) : path
        var file = root.appendingPathComponent(rel)
        // cegah keluar dari folder halaman
        if !file.standardized.path.hasPrefix(root.standardized.path) {
            send(conn: conn, status: "403 Forbidden", body: Data("forbidden".utf8), type: "text/plain")
            return
        }
        var isDir: ObjCBool = false
        if FileManager.default.fileExists(atPath: file.path, isDirectory: &isDir), isDir.boolValue {
            file = file.appendingPathComponent("index.html")
        }
        guard let body = try? Data(contentsOf: file) else {
            send(conn: conn, status: "404 Not Found", body: Data("not found".utf8), type: "text/plain")
            return
        }
        send(conn: conn, status: "200 OK", body: body, type: mime(for: file.pathExtension))
    }

    private func send(conn: NWConnection, status: String, body: Data, type: String) {
        let head = "HTTP/1.1 \(status)\r\nContent-Type: \(type)\r\n" +
                   "Content-Length: \(body.count)\r\nConnection: close\r\n" +
                   "Cache-Control: no-store\r\n\r\n"
        var out = Data(head.utf8)
        out.append(body)
        conn.send(content: out, completion: .contentProcessed { _ in conn.cancel() })
    }

    private func mime(for ext: String) -> String {
        switch ext.lowercased() {
        case "html": return "text/html"
        case "css": return "text/css"
        case "js": return "application/javascript"
        case "json": return "application/json"
        case "png": return "image/png"
        case "jpg", "jpeg": return "image/jpeg"
        case "svg": return "image/svg+xml"
        default: return "application/octet-stream"
        }
    }
}
