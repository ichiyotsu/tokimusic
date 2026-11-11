// swift-tools-version:5.9
import PackageDescription
let package = Package(name: "TokiMusicCore", platforms: [.iOS(.v16)],
    products: [.library(name: "TokiMusicCore", targets: ["TokiMusicCore"])],
    targets: [.target(name: "TokiMusicCore"), .testTarget(name: "TokiMusicCoreTests", dependencies: ["TokiMusicCore"])])
