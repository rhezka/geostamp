import SwiftUI
import WebKit
import Photos

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

        // izinkan halaman file:// memanggil layanan luar (peta dan pencarian alamat)
        config.preferences.setValue(true, forKey: "allowFileAccessFromFileURLs")
        config.setValue(true, forKey: "allowUniversalAccessFromFileURLs")

        config.userContentController.add(context.coordinator, name: "savePhoto")

        let web = WKWebView(frame: .zero, configuration: config)
        web.isOpaque = false
        web.backgroundColor = UIColor(red: 0.059, green: 0.067, blue: 0.082, alpha: 1)
        web.scrollView.bounces = false
        web.allowsBackForwardNavigationGestures = false
        web.navigationDelegate = context.coordinator
        web.uiDelegate = context.coordinator

        // halaman dibundel di dalam aplikasi supaya tetap jalan tanpa internet
        guard let webDir = Bundle.main.url(forResource: "Web", withExtension: nil) else {
            return web
        }
        let index = webDir.appendingPathComponent("index.html")
        web.loadFileURL(index, allowingReadAccessTo: webDir)
        return web
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator() }

    final class Coordinator: NSObject, WKNavigationDelegate, WKUIDelegate, WKScriptMessageHandler {
        // izin kamera/galeri untuk input file di dalam webview
        func webView(_ webView: WKWebView,
                     requestMediaCapturePermissionFor origin: WKSecurityOrigin,
                     initiatedByFrame frame: WKFrameInfo,
                     type: WKMediaCaptureType,
                     decisionHandler: @escaping (WKPermissionDecision) -> Void) {
            decisionHandler(.grant)
        }

        // halaman mengirim gambar jadi (base64) untuk disimpan ke galeri
        func userContentController(_ userContentController: WKUserContentController,
                                   didReceive message: WKScriptMessage) {
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
