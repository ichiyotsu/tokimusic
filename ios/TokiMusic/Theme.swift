import SwiftUI
/// TokiPalette — same hex as Tokinote (Matcha). Light/dark pairs mirror Android Color.kt.
enum TokiPalette {
    static func primary(dark: Bool) -> Color { dark ? Color(hex: 0xB1D18A) : Color(hex: 0x4C662B) }
    static func onPrimary(dark: Bool) -> Color { dark ? Color(hex: 0x1F3701) : .white }
    static func primaryContainer(dark: Bool) -> Color { dark ? Color(hex: 0x354E16) : Color(hex: 0xCDEDA3) }
    static func background(dark: Bool) -> Color { dark ? Color(hex: 0x12140E) : Color(hex: 0xF9FAEF) }
    static func onSurface(dark: Bool) -> Color { dark ? Color(hex: 0xE2E3D8) : Color(hex: 0x1A1C16) }
    static func onSurfaceVariant(dark: Bool) -> Color { dark ? Color(hex: 0xC5C8BA) : Color(hex: 0x44483D) }
    static func surfaceContainer(dark: Bool) -> Color { dark ? Color(hex: 0x1E201A) : Color(hex: 0xEEEFE3) }
}
extension Color { init(hex: UInt32) { self.init(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255) } }
/// TokiShapes (points): card 34/22/34/22, chip 20/20/20/7, capsule 36.
struct ExpressiveCardShape: Shape {
    func path(in rect: CGRect) -> Path { Path(roundedRect: rect, cornerRadius: 26) } // SwiftUI has no per-corner radii in Shape; views use UnevenRoundedRectangle on iOS 16+.
}
let tokiCardCorners = UnevenRoundedRectangle(topLeadingRadius: 34, bottomTrailingRadius: 34, bottomLeadingRadius: 22, topTrailingRadius: 22)
let tokiChipCorners = UnevenRoundedRectangle(topLeadingRadius: 20, bottomTrailingRadius: 20, bottomLeadingRadius: 7, topTrailingRadius: 20)
