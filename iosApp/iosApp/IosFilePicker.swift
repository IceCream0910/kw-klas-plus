import Shared
import UniformTypeIdentifiers
import UIKit

protocol FilePickerPresenting: AnyObject {
    func present(allowMultiple: Bool, from presenter: UIViewController?, completion: @escaping (FilePickerResult) -> Void)
}

final class IosFilePicker {
    private let presenter: FilePickerPresenting

    init(presenter: FilePickerPresenting = SystemFilePickerPresenter()) {
        self.presenter = presenter
    }

    func pickForWeb(allowMultiple: Bool, from viewController: UIViewController?, completion: @escaping ([URL]?) -> Void) {
        presenter.present(allowMultiple: allowMultiple, from: viewController) { result in
            if let selected = result as? FilePickerResultSelected {
                completion(selected.references.compactMap(URL.init(string:)))
            } else {
                completion(nil)
            }
        }
    }
}

final class SystemFilePickerPresenter: NSObject, FilePickerPresenting, UIDocumentPickerDelegate {
    private var completion: ((FilePickerResult) -> Void)?

    func present(
        allowMultiple: Bool,
        from presenter: UIViewController?,
        completion: @escaping (FilePickerResult) -> Void
    ) {
        finishPending(FilePickerResultCancelled())
        self.completion = completion
        guard let presenter = presenter ?? UIView.klas_keyWindowRootViewController else {
            finishPending(FilePickerResultFailed(reason: "no_presenter"))
            return
        }
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.item], asCopy: true)
        picker.allowsMultipleSelection = allowMultiple
        picker.delegate = self
        presenter.present(picker, animated: true)
    }

    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        controller.dismiss(animated: true)
        if urls.isEmpty {
            finishPending(FilePickerResultCancelled())
        } else {
            finishPending(FilePickerResultSelected(references: urls.map(\.absoluteString)))
        }
    }

    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        finishPending(FilePickerResultCancelled())
    }

    private func finishPending(_ result: FilePickerResult) {
        let completion = completion
        self.completion = nil
        completion?(result)
    }
}

extension UIView {
    static var klas_keyWindowRootViewController: UIViewController? {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first { $0.isKeyWindow }?
            .rootViewController
    }

    var klas_presentingViewController: UIViewController? {
        var responder: UIResponder? = self
        while let current = responder {
            if let viewController = current as? UIViewController {
                return viewController
            }
            responder = current.next
        }
        return Self.klas_keyWindowRootViewController
    }
}
