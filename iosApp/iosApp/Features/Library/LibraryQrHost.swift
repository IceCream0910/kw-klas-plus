import SwiftUI

struct LibraryQrHostModifier: ViewModifier {
    @ObservedObject var controller: LibraryQrController
    var colorScheme: ColorScheme?

    func body(content: Content) -> some View {
        content
            .sheet(item: $controller.presentedSheet, onDismiss: controller.onSheetDismissed) { sheet in
                sheetContent(sheet)
                    .preferredColorScheme(colorScheme)
            }
            .alert(
                "위젯 추가",
                isPresented: $controller.addWidgetAlertPresented
            ) {
                Button("확인", role: .cancel) {}
            } message: {
                Text(controller.addWidgetMessage)
            }
    }

    @ViewBuilder
    private func sheetContent(_ sheet: LibraryQrPresentedSheet) -> some View {
        switch sheet {
        case .qr:
            LibraryQrSheet(
                state: controller.qrState,
                onRefresh: controller.refreshQr,
                onSettings: controller.presentSettingsFromQr,
                onAddWidget: { controller.addWidgetAlertPresented = true }
            )
            .presentationDetents([.large])
            .presentationDragIndicator(.visible)
            .alert(
                controller.errorTitle,
                isPresented: $controller.errorAlertPresented
            ) {
                Button("확인") { controller.dismissErrorAlert() }
            } message: {
                Text(controller.errorMessage)
            }
        case .settings:
            LibraryQrSettingsSheet(
                state: $controller.settingsState,
                onSave: controller.saveSettings
            )
            .presentationDetents([.large])
            .presentationDragIndicator(.visible)
        }
    }
}

extension View {
    func libraryQrHost(_ controller: LibraryQrController, colorScheme: ColorScheme?) -> some View {
        modifier(LibraryQrHostModifier(controller: controller, colorScheme: colorScheme))
    }
}
