import SwiftUI

struct FunnelGlow: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var drift = false
    var body: some View {
        GeometryReader { geometry in
            ZStack(alignment: .top) {
                LinearGradient(colors: [KlasTheme.primary.opacity(0.12), .clear], startPoint: .top, endPoint: .bottom)
                Ellipse().fill(KlasTheme.primary.opacity(0.18))
                    .frame(width: geometry.size.width * 0.85, height: 220)
                    .offset(x: reduceMotion ? -60 : drift ? 50 : -80, y: -110)
                Ellipse().fill(Color.cyan.opacity(0.10))
                    .frame(width: geometry.size.width * 0.6, height: 180)
                    .offset(x: reduceMotion ? 90 : drift ? -30 : 130, y: -55)
            }
            .blur(radius: 38)
        }
        .frame(height: 300)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
        .onAppear {
            guard !reduceMotion else { return }
            withAnimation(.easeInOut(duration: 7).repeatForever(autoreverses: true)) { drift = true }
        }
    }
}

struct FunnelIllustration: View {
    let symbol: String
    var plain = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var floating = false
    @State private var scale: CGFloat = 0.85
    var body: some View {
        if plain {
            Image(systemName: symbol).font(.system(size: 36, weight: .light))
                .foregroundStyle(KlasTheme.primary)
                .frame(maxWidth: .infinity, minHeight: 80, alignment: .leading)
                .accessibilityHidden(true)
        } else {
            Button {
                guard !reduceMotion else { return }
                scale = 0.88
                withAnimation(.spring(response: 0.45, dampingFraction: 0.5)) { scale = 1 }
            } label: {
                Image(systemName: symbol).font(.system(size: 36, weight: .light))
                    .foregroundStyle(KlasTheme.primary)
                    .frame(width: 92, height: 92)
                    .background(KlasTheme.primary.opacity(0.09), in: Circle())
                    .scaleEffect(reduceMotion ? 1 : scale)
                    .offset(y: reduceMotion ? 0 : floating ? -3 : 3)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("아이콘 애니메이션 다시 재생")
            .frame(maxWidth: .infinity, minHeight: 116, alignment: .leading)
            .onAppear {
                withAnimation(reduceMotion ? nil : .spring(response: 0.5, dampingFraction: 0.6)) { scale = 1 }
                if !reduceMotion { withAnimation(.easeInOut(duration: 2.2).repeatForever(autoreverses: true)) { floating = true } }
            }
        }
    }
}

struct DeadlineNotificationPreview: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var appeared = false
    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 30).fill(KlasTheme.onSurfaceVariant.opacity(0.07))
                .frame(width: 160, height: 328)
                .overlay(alignment: .top) { Capsule().fill(KlasTheme.onSurfaceVariant.opacity(0.12)).frame(width: 58, height: 6).padding(.top, 12) }
            HStack(spacing: 14) {
                Image(systemName: "bell.badge").font(.system(size: 28)).foregroundStyle(KlasTheme.primary)
                VStack(alignment: .leading, spacing: 4) {
                    Text("KLAS+ · 알림 예시").font(.caption2).foregroundStyle(KlasTheme.onSurfaceVariant)
                    Text("곧 마감되는 할 일이 있어요").font(.subheadline.bold())
                    Text("자료구조 과제 · 약 2시간 뒤 마감").font(.caption).foregroundStyle(KlasTheme.onSurfaceVariant)
                }
                Spacer(minLength: 0)
            }
            .padding(18)
            .background(KlasTheme.background, in: RoundedRectangle(cornerRadius: 22))
            .shadow(color: .black.opacity(0.07), radius: 16, y: 5)
            .padding(.horizontal, 6)
            .opacity(appeared ? 1 : 0)
            .offset(y: appeared || reduceMotion ? 0 : 20)
        }
        .frame(height: 338)
        .onAppear { withAnimation(reduceMotion ? nil : .easeOut(duration: 0.55)) { appeared = true } }
    }
}
