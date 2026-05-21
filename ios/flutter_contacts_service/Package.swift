// swift-tools-version: 5.9
// The swift-tools-version declares the minimum version of Swift required to build this package.

import PackageDescription

let package = Package(
  name: "flutter_contacts_service",
  platforms: [
    .iOS("13.0"),
  ],
  products: [
    .library(name: "flutter-contacts-service", targets: ["flutter_contacts_service"]),
  ],
  dependencies: [
    .package(name: "FlutterFramework", path: "../FlutterFramework"),
  ],
  targets: [
    .target(
      name: "flutter_contacts_service",
      dependencies: [
        .product(name: "FlutterFramework", package: "FlutterFramework"),
      ],
      resources: [
        .process("PrivacyInfo.xcprivacy"),
      ],
      linkerSettings: [
        .linkedFramework("Contacts"),
        .linkedFramework("ContactsUI"),
      ]
    ),
  ]
)
