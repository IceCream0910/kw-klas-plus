import CoreImage
import CoreImage.CIFilterBuiltins
import UIKit

enum LibraryQrImageRenderer {
    static func image(from value: String, darkMode: Bool, dimension: CGFloat = 220) -> UIImage? {
        guard !value.isEmpty else { return nil }
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(value.utf8)
        filter.correctionLevel = "M"
        guard let output = filter.outputImage else { return nil }

        let colorFilter = CIFilter.falseColor()
        colorFilter.inputImage = output
        colorFilter.color0 = CIColor(color: darkMode ? .white : .black)
        colorFilter.color1 = CIColor(color: .clear)
        guard let colored = colorFilter.outputImage else { return nil }

        let scale = max(dimension / colored.extent.width, dimension / colored.extent.height)
        let scaled = colored.transformed(by: CGAffineTransform(scaleX: scale, y: scale))
        let context = CIContext()
        guard let cgImage = context.createCGImage(scaled, from: scaled.extent) else { return nil }
        return UIImage(cgImage: cgImage)
    }
}
