import SwiftUI
import UIKit
import WidgetKit

enum LibraryQRWidgetKind {
    static let id = "LibraryQRWidget"
}

struct LibraryQREntry: TimelineEntry {
    let date: Date
}

struct LibraryQRProvider: TimelineProvider {
    func placeholder(in context: Context) -> LibraryQREntry {
        LibraryQREntry(date: Date())
    }

    func getSnapshot(in context: Context, completion: @escaping (LibraryQREntry) -> Void) {
        completion(LibraryQREntry(date: Date()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<LibraryQREntry>) -> Void) {
        completion(Timeline(entries: [LibraryQREntry(date: Date())], policy: .never))
    }
}

struct LibraryQRWidgetView: View {
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        ZStack {
            backgroundColor
            if let image = UIImage(named: assetName, in: .main, compatibleWith: nil) {
                if #available(iOS 18.0, *) {
                    Image(uiImage: image)
                        .resizable()
                        .widgetAccentedRenderingMode(.fullColor)
                        .scaledToFit()
                } else {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFit()
                }
            } else {
                Image(systemName: "qrcode.viewfinder")
                    .resizable()
                    .scaledToFit()
                    .foregroundStyle(colorScheme == .dark ? .white : .black)
                    .padding(32)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .widgetURL(URL(string: "kwklasplus://library-qr"))
        .modifier(LibraryQRWidgetBackground(color: backgroundColor))
    }

    private var assetName: String {
        colorScheme == .dark ? "QrWidgetDark" : "QrWidgetLight"
    }

    private var backgroundColor: Color {
        colorScheme == .dark ? Color(red: 0.16, green: 0.17, blue: 0.20) : .white
    }
}

private struct LibraryQRWidgetBackground: ViewModifier {
    let color: Color

    func body(content: Content) -> some View {
        if #available(iOS 17.0, *) {
            content.containerBackground(for: .widget) {
                color
            }
        } else {
            content
        }
    }
}

struct LibraryQRWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: LibraryQRWidgetKind.id, provider: LibraryQRProvider()) { _ in
            LibraryQRWidgetView()
        }
        .configurationDisplayName("도서관 출입증")
        .description("중앙도서관 출입증 QR 코드를 홈 화면에서 빠르게 열 수 있어요.")
        .supportedFamilies([.systemSmall])
        .contentMarginsDisabled()
    }
}
