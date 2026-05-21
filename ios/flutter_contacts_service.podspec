#
# To learn more about a Podspec see http://guides.cocoapods.org/syntax/podspec.html.
# Run `pod lib lint flutter_contacts_service.podspec` to validate before publishing.
#
Pod::Spec.new do |s|
  s.name             = 'flutter_contacts_service'
  s.version          = '0.1.0'
  s.summary          = 'A Flutter plugin to read, create, update and delete device contacts on Android and iOS.'
  s.description      = <<-DESC
A Flutter plugin for managing device contacts natively — read, create, update,
delete, and open the native contact UI on Android and iOS.
                       DESC
  s.homepage         = 'https://github.com/mustafa-707/flutter_contacts_service'
  s.license          = { :file => '../LICENSE' }
  s.author           = { 'mustafa-707' => 'https://github.com/mustafa-707' }
  s.source           = { :path => '.' }
  s.source_files     = 'flutter_contacts_service/Sources/flutter_contacts_service/**/*.swift'
  s.resource_bundles = { 'flutter_contacts_service_privacy' => ['flutter_contacts_service/Sources/flutter_contacts_service/PrivacyInfo.xcprivacy'] }
  s.dependency 'Flutter'
  s.frameworks = 'Contacts', 'ContactsUI'
  s.platform = :ios, '13.0'

  # Flutter.framework does not contain a i386 slice.
  s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES', 'EXCLUDED_ARCHS[sdk=iphonesimulator*]' => 'i386' }
  s.swift_version = '5.0'
end
