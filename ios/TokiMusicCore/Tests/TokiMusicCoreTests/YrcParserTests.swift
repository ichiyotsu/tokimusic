import XCTest
@testable import TokiMusicCore
/// Test samples (also serve as the documented examples):
/// YRC: "[1000,2000](1000,500,0)你(1500,500,0)好(2000,1000,0)世界" -> 1 line, 3 words, text "你好世界".
final class YrcParserTests: XCTestCase {
    func testWordTimings() {
        let lines = YrcParser.parseYrc("[1000,2000](1000,500,0)你(1500,500,0)好(2000,1000,0)世界\n[5000,1000](5000,1000,0)再见")
        XCTAssertEqual(lines.count, 2); XCTAssertEqual(lines[0].words.map(\.text), ["你", "好", "世界"]); XCTAssertEqual(lines[0].text, "你好世界")
    }
    func testProgressAndLrcFallback() {
        XCTAssertEqual(YrcParser.wordProgress(LyricWord(1000, 500, "你"), 1250), 0.5, accuracy: 0.001)
        let lines = YrcParser.parse(yrc: nil, lrc: "[00:01.00]hello\n[00:03.50]world", tlyric: "[00:01.00]你好")
        XCTAssertEqual(lines.first?.translation, "你好"); XCTAssertEqual(lines.first?.durationMs, 2500)
    }
}
