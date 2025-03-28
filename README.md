# flutter_contacts_service

[![StandWithPalestine](https://raw.githubusercontent.com/TheBSD/StandWithPalestine/main/badges/StandWithPalestine.svg)](https://github.com/TheBSD/StandWithPalestine/blob/main/docs/README.md) [![Pub Package](https://img.shields.io/pub/v/flutter_contacts_service.svg)](https://pub.dev/packages/flutter_contacts_service)

A Flutter plugin for managing device contacts with enhanced features. This package provides a simple and efficient way to access, create, update and delete contacts on both Android and iOS platforms.

## Features

- Read all device contacts
- Get contacts by phone number or email
- Create new contacts
- Update existing contacts
- Delete contacts
- Get contact avatars (high/low resolution)
- Open native contact form
- Open existing contact in native UI
- Support for localized labels
- Enhanced performance with optional thumbnail loading
- Support for postal addresses
- Birthday field support
- Comprehensive error handling

## Getting Started

Add **`flutter_contacts_service`** to your `pubspec.yaml`:

```yaml
dependencies:
  flutter_contacts_service: ^1.0.0
```

### Platform Specific Setup

#### Android

Android is Handled by the plugin.

#### iOS

Add the following to your `Info.plist`:

```xml
<key>NSContactsUsageDescription</key>
<string>This app requires contacts access to function properly.</string>
```

and if you are using Permission Handler: (dont forget to add this to your Podfile)

```ruby
    config.build_settings['GCC_PREPROCESSOR_DEFINITIONS'] ||= [
     '$(inherited)',
     'PERMISSION_CONTACTS=1',
    ]
```

## Usage

### Import the package

```dart
import 'package:flutter_contacts_service/flutter_contacts_service.dart';
```

### Basic Operations

```dart
// Get all contacts
List<ContactInfo> contacts = await FlutterContactsService.getContacts();

// Get contacts without thumbnails (faster)
List<ContactInfo> contacts = await FlutterContactsService.getContacts(
  withThumbnails: false
);

// Get contacts by query
List<ContactInfo> contacts = await FlutterContactsService.getContacts(
  query: "John"
);

// Get contacts by phone number
List<ContactInfo> contacts = await FlutterContactsService.getContactsForPhone(
  phone: "+1234567890"
);

// Get contacts by email
List<ContactInfo> contacts = await FlutterContactsService.getContactsForEmail(
  email: "example@email.com"
);

// Get avatar for a contact
Uint8List avatar = await FlutterContactsService.getAvatar(
  contact,
  photoHighResolution: true
);
```

### Contact Management

```dart
// Add a new contact
ContactInfo newContact = ContactInfo(
  givenName: "John",
  familyName: "Doe",
  phones: [Item(label: "mobile", value: "+1234567890")],
  emails: [Item(label: "work", value: "john.doe@company.com")]
);
await FlutterContactsService.addContact(newContact);

// Update a contact
contact.familyName = "Smith";
await FlutterContactsService.updateContact(contact);

// Delete a contact
await FlutterContactsService.deleteContact(contact);
```

### Native UI Integration

```dart
// Open native contact form
await FlutterContactsService.openContactForm();

// Open existing contact in native UI
await FlutterContactsService.openExistingContact(contact);

// Open device contact picker
await FlutterContactsService.openDeviceContactPicker();
```

## Contact Model

```dart
class ContactInfo {
  String? identifier;
  String? displayName;
  String? givenName;
  String? middleName;
  String? familyName;
  String? prefix;
  String? suffix;
  String? company;
  String? jobTitle;
  String? note;
  String? birthday;
  List<ValueItem> emails;
  List<ValueItem> phones;
  List<PostalAddress> postalAddresses;
  Uint8List? avatar;
}
```

## Error Handling

The plugin includes comprehensive error handling and will throw specific exceptions for common error cases:

```dart
try {
  await FlutterContactsService.addContact(contact);
} catch (e) {
  print('Error adding contact: $e');
}
```

## Additional Notes

- This plugin does not handle permission requesting. Use plugins like `permission_handler` to manage contact permissions.
- Contact avatars are loaded lazily by default to improve performance.
- The plugin supports both Android and iOS native contact forms.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request. For major changes, please open an issue first to discuss what you would like to change.

## Support

If you find this plugin helpful, consider supporting me:

[![Buy Me A Coffee](https://www.buymeacoffee.com/assets/img/guidelines/download-assets-sm-1.svg)](https://buymeacoffee.com/is10vmust)
