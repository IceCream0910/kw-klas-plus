import SwiftUI

extension View {
    @ViewBuilder
    func klasFloatingSurface(cornerRadius: CGFloat) -> some View {
        if #available(iOS 26.0, *) {
            glassEffect(.regular, in: .rect(cornerRadius: cornerRadius))
        } else {
            background(.regularMaterial, in: RoundedRectangle(cornerRadius: cornerRadius))
        }
    }
}

extension ToolbarItemPlacement {
    static var klasPinnedTrailing: ToolbarItemPlacement {
        // Xcode 26.x SDK에는 topBarPinnedTrailing이 없어 컴파일러 버전으로 한 번 더 막는다.
        #if compiler(>=6.4)
        if #available(iOS 27.0, *) {
            return .topBarPinnedTrailing
        }
        #endif
        return .topBarTrailing
    }
}
