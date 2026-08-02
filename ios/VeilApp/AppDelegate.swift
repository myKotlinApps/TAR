import UIKit
@main
class AppDelegate: UIResponder, UIApplicationDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        application.ignoreSnapshotOnNextApplicationLaunch()
        DispatchQueue.global(qos: .background).async { VeilClient.shared.connect() }
        return true
    }
    func applicationDidEnterBackground(_ application: UIApplication) {
        var bgTask = UIBackgroundTaskIdentifier(rawValue: 0)
        bgTask = application.beginBackgroundTask(expirationHandler: { application.endBackgroundTask(bgTask) })
    }
}
