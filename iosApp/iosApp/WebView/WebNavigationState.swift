import Foundation

// Android WebSurfaceSnapshot과 동일한 의미의 내비게이션 상태
enum WebLoadPhase: Equatable {
    case idle
    case loading(url: String)
    case ready(url: String)
    case failed(url: String?, category: WebNavFailureCategory)
    case disposed
}

enum WebNavFailureCategory: Equatable {
    case network
    case tls
    case http
    case cancelled
    case unknown
}

struct WebNavigationState: Equatable {
    var loadPhase: WebLoadPhase = .idle
    var canGoBack: Bool = false
    var canGoForward: Bool = false

    var url: String? {
        switch loadPhase {
        case .loading(let url), .ready(let url):
            return url
        case .failed(let url, _):
            return url
        case .idle, .disposed:
            return nil
        }
    }
}
