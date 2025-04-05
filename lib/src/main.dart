import 'dart:async';

import 'package:collection/collection.dart';
import 'package:flutter/services.dart';
import 'package:quiver/core.dart';

class FlutterContactsService {
  static const MethodChannel _channel = MethodChannel(
    'flutter_contacts_service',
  );

  /// Fetches all contacts, or when specified, the contacts with a name
  /// matching [query]
  static Future<List<ContactInfo>> getContacts({
    String? query,
    bool withThumbnails = true,
    bool photoHighResolution = true,
    bool orderByGivenName = true,
    bool iOSLocalizedLabels = true,
    bool androidLocalizedLabels = true,
  }) async {
    Iterable contacts = await _channel.invokeMethod(
      'getContacts',
      <String, dynamic>{
        'query': query,
        'withThumbnails': withThumbnails,
        'photoHighResolution': photoHighResolution,
        'orderByGivenName': orderByGivenName,
        'iOSLocalizedLabels': iOSLocalizedLabels,
        'androidLocalizedLabels': androidLocalizedLabels,
      },
    );
    return contacts.map((m) => ContactInfo.fromMap(m)).toList();
  }

  /// Fetches all contacts, or when specified, the contacts with the phone
  /// matching [phone]
  static Future<List<ContactInfo>> getContactsForPhone(
    String? phone, {
    bool withThumbnails = true,
    bool photoHighResolution = true,
    bool orderByGivenName = true,
    bool iOSLocalizedLabels = true,
    bool androidLocalizedLabels = true,
  }) async {
    if (phone == null || phone.isEmpty) return List.empty();

    Iterable contacts =
        await _channel.invokeMethod('getContactsForPhone', <String, dynamic>{
      'phone': phone,
      'withThumbnails': withThumbnails,
      'photoHighResolution': photoHighResolution,
      'orderByGivenName': orderByGivenName,
      'iOSLocalizedLabels': iOSLocalizedLabels,
      'androidLocalizedLabels': androidLocalizedLabels,
    });
    return contacts.map((m) => ContactInfo.fromMap(m)).toList();
  }

  /// Fetches all contacts, or when specified, the contacts with the email
  /// matching [email]
  /// Works only on iOS
  static Future<List<ContactInfo>> getContactsForEmail(
    String email, {
    bool withThumbnails = true,
    bool photoHighResolution = true,
    bool orderByGivenName = true,
    bool iOSLocalizedLabels = true,
    bool androidLocalizedLabels = true,
  }) async {
    List contacts = await _channel.invokeMethod(
      'getContactsForEmail',
      <String, dynamic>{
        'email': email,
        'withThumbnails': withThumbnails,
        'photoHighResolution': photoHighResolution,
        'orderByGivenName': orderByGivenName,
        'iOSLocalizedLabels': iOSLocalizedLabels,
        'androidLocalizedLabels': androidLocalizedLabels,
      },
    );
    return contacts.map((m) => ContactInfo.fromMap(m)).toList();
  }

  /// Loads the avatar for the given contact and returns it. If the user does
  /// not have an avatar, then `null` is returned in that slot. Only implemented
  /// on Android.
  static Future<Uint8List?> getAvatar(
    final ContactInfo contact, {
    final bool photoHighRes = true,
  }) =>
      _channel.invokeMethod(
        'getAvatar',
        <String, dynamic>{
          'contact': ContactInfo._toMap(contact),
          'photoHighResolution': photoHighRes,
        },
      );

  /// Adds the [contact] to the device contact list
  static Future addContact(ContactInfo contact) => _channel.invokeMethod(
        'addContact',
        ContactInfo._toMap(contact),
      );

  /// Deletes the [contact] if it has a valid identifier
  static Future deleteContact(ContactInfo contact) => _channel.invokeMethod(
        'deleteContact',
        ContactInfo._toMap(contact),
      );

  /// Updates the [contact] if it has a valid identifier
  static Future updateContact(ContactInfo contact) => _channel.invokeMethod(
        'updateContact',
        ContactInfo._toMap(contact),
      );

  static Future<ContactInfo> openContactForm({
    bool iOSLocalizedLabels = true,
    bool androidLocalizedLabels = true,
  }) async {
    dynamic result =
        await _channel.invokeMethod('openContactForm', <String, dynamic>{
      'iOSLocalizedLabels': iOSLocalizedLabels,
      'androidLocalizedLabels': androidLocalizedLabels,
    });
    return _handleFormOperation(result);
  }

  static Future<ContactInfo> openExistingContact(
    ContactInfo contact, {
    bool iOSLocalizedLabels = true,
    bool androidLocalizedLabels = true,
  }) async {
    dynamic result = await _channel.invokeMethod(
      'openExistingContact',
      <String, dynamic>{
        'contact': ContactInfo._toMap(contact),
        'iOSLocalizedLabels': iOSLocalizedLabels,
        'androidLocalizedLabels': androidLocalizedLabels,
      },
    );
    return _handleFormOperation(result);
  }

  // Displays the device/native contact picker dialog and returns the contact selected by the user
  static Future<ContactInfo?> openDeviceContactPicker({
    bool iOSLocalizedLabels = true,
    bool androidLocalizedLabels = true,
  }) async {
    dynamic result = await _channel
        .invokeMethod('openDeviceContactPicker', <String, dynamic>{
      'iOSLocalizedLabels': iOSLocalizedLabels,
      'androidLocalizedLabels': androidLocalizedLabels,
    });
    // result contains either :
    // - an List of contacts containing 0 or 1 contact
    // - a FormOperationErrorCode value
    if (result is List) {
      if (result.isEmpty) {
        return null;
      }
      result = result.first;
    }
    return _handleFormOperation(result);
  }

  static ContactInfo _handleFormOperation(dynamic result) {
    if (result is int) {
      switch (result) {
        case 1:
          throw FormOperationException(
            errorCode: FormOperationErrorCode.FORM_OPERATION_CANCELED,
          );
        case 2:
          throw FormOperationException(
            errorCode: FormOperationErrorCode.FORM_COULD_NOT_BE_OPEN,
          );
        default:
          throw FormOperationException(
            errorCode: FormOperationErrorCode.FORM_OPERATION_UNKNOWN_ERROR,
          );
      }
    } else if (result is Map) {
      return ContactInfo.fromMap(result);
    } else {
      throw FormOperationException(
          errorCode: FormOperationErrorCode.FORM_OPERATION_UNKNOWN_ERROR);
    }
  }
}

class FormOperationException implements Exception {
  final FormOperationErrorCode? errorCode;

  const FormOperationException({this.errorCode});
  @override
  String toString() => 'FormOperationException: $errorCode';
}

enum FormOperationErrorCode {
  FORM_OPERATION_CANCELED,
  FORM_COULD_NOT_BE_OPEN,
  FORM_OPERATION_UNKNOWN_ERROR
}

class ContactInfo {
  ContactInfo({
    this.displayName,
    this.givenName,
    this.middleName,
    this.familyName,
    this.prefix,
    this.suffix,
    this.company,
    this.jobTitle,
    this.note,
    this.birthday,
    this.emails,
    this.phones,
    this.postalAddresses,
    this.avatar,
    this.androidAccountType,
    this.androidAccountTypeRaw,
    this.androidAccountName,
  });

  String? identifier,
      displayName,
      givenName,
      middleName,
      prefix,
      suffix,
      familyName,
      company,
      jobTitle;
  String? note;

  String? androidAccountTypeRaw, androidAccountName;
  AndroidAccountType? androidAccountType;
  List<ValueItem>? emails = [];
  List<ValueItem>? phones = [];
  List<PostalAddress>? postalAddresses = [];
  Uint8List? avatar;
  DateTime? birthday;

  String initials() {
    return ((givenName?.isNotEmpty == true ? givenName![0] : "") +
            (familyName?.isNotEmpty == true ? familyName![0] : ""))
        .toUpperCase();
  }

  ContactInfo.fromMap(Map m) {
    identifier = m["identifier"];
    displayName = m["displayName"];
    givenName = m["givenName"];
    middleName = m["middleName"];
    familyName = m["familyName"];
    prefix = m["prefix"];
    suffix = m["suffix"];
    company = m["company"];
    jobTitle = m["jobTitle"];
    note = m["note"];
    androidAccountTypeRaw = m["androidAccountType"];
    androidAccountType = accountTypeFromString(androidAccountTypeRaw);
    androidAccountName = m["androidAccountName"];
    emails = (m["emails"] as List?)?.map((m) => ValueItem.fromMap(m)).toList();
    phones = (m["phones"] as List?)?.map((m) => ValueItem.fromMap(m)).toList();
    postalAddresses = (m["postalAddresses"] as List?)
        ?.map((m) => PostalAddress.fromMap(m))
        .toList();
    avatar = m["avatar"];
    try {
      birthday = m["birthday"] != null ? DateTime.parse(m["birthday"]) : null;
    } catch (e) {
      birthday = null;
    }
  }

  static Map _toMap(ContactInfo contact) {
    var emails = [];
    for (ValueItem email in contact.emails ?? []) {
      emails.add(ValueItem._toMap(email));
    }
    var phones = [];
    for (ValueItem phone in contact.phones ?? []) {
      phones.add(ValueItem._toMap(phone));
    }
    var postalAddresses = [];
    for (PostalAddress address in contact.postalAddresses ?? []) {
      postalAddresses.add(PostalAddress._toMap(address));
    }

    final birthday = contact.birthday == null
        ? null
        : "${contact.birthday!.year.toString()}-${contact.birthday!.month.toString().padLeft(2, '0')}-${contact.birthday!.day.toString().padLeft(2, '0')}";

    return {
      "identifier": contact.identifier,
      "displayName": contact.displayName,
      "givenName": contact.givenName,
      "middleName": contact.middleName,
      "familyName": contact.familyName,
      "prefix": contact.prefix,
      "suffix": contact.suffix,
      "company": contact.company,
      "jobTitle": contact.jobTitle,
      "note": contact.note,
      "androidAccountType": contact.androidAccountTypeRaw,
      "androidAccountName": contact.androidAccountName,
      "emails": emails,
      "phones": phones,
      "postalAddresses": postalAddresses,
      "avatar": contact.avatar,
      "birthday": birthday
    };
  }

  Map toMap() {
    return ContactInfo._toMap(this);
  }

  /// The [+] operator fills in this contact's empty fields with the fields from [other]
  operator +(ContactInfo other) => ContactInfo(
        givenName: givenName ?? other.givenName,
        middleName: middleName ?? other.middleName,
        prefix: prefix ?? other.prefix,
        suffix: suffix ?? other.suffix,
        familyName: familyName ?? other.familyName,
        company: company ?? other.company,
        note: note ?? other.note,
        jobTitle: jobTitle ?? other.jobTitle,
        androidAccountType: androidAccountType ?? other.androidAccountType,
        androidAccountName: androidAccountName ?? other.androidAccountName,
        emails: emails == null
            ? other.emails
            : emails!
                .toSet()
                .union(other.emails?.toSet() ?? <ValueItem>{})
                .toList(),
        phones: phones == null
            ? other.phones
            : phones!
                .toSet()
                .union(other.phones?.toSet() ?? <ValueItem>{})
                .toList(),
        postalAddresses: postalAddresses == null
            ? other.postalAddresses
            : postalAddresses!
                .toSet()
                .union(other.postalAddresses?.toSet() ?? <PostalAddress>{})
                .toList(),
        avatar: avatar ?? other.avatar,
        birthday: birthday ?? other.birthday,
      );

  /// Returns true if all items in this contact are identical.
  @override
  bool operator ==(Object other) {
    return other is ContactInfo &&
        avatar == other.avatar &&
        company == other.company &&
        displayName == other.displayName &&
        givenName == other.givenName &&
        familyName == other.familyName &&
        identifier == other.identifier &&
        jobTitle == other.jobTitle &&
        note == other.note &&
        androidAccountType == other.androidAccountType &&
        androidAccountName == other.androidAccountName &&
        middleName == other.middleName &&
        prefix == other.prefix &&
        suffix == other.suffix &&
        birthday == other.birthday &&
        DeepCollectionEquality.unordered().equals(phones, other.phones) &&
        DeepCollectionEquality.unordered().equals(emails, other.emails) &&
        DeepCollectionEquality.unordered()
            .equals(postalAddresses, other.postalAddresses);
  }

  @override
  int get hashCode {
    return hashObjects([
      company,
      displayName,
      familyName,
      givenName,
      identifier,
      jobTitle,
      note,
      androidAccountType,
      androidAccountName,
      middleName,
      prefix,
      suffix,
      birthday,
    ].where((s) => s != null));
  }

  AndroidAccountType? accountTypeFromString(String? androidAccountType) {
    if (androidAccountType == null) {
      return null;
    }
    if (androidAccountType.startsWith("com.google")) {
      return AndroidAccountType.google;
    } else if (androidAccountType.startsWith("com.whatsapp")) {
      return AndroidAccountType.whatsapp;
    } else if (androidAccountType.startsWith("com.facebook")) {
      return AndroidAccountType.facebook;
    }

    /// Other account types are not supported on Android
    /// such as Samsung, htc etc...
    return AndroidAccountType.other;
  }
}

class PostalAddress {
  PostalAddress({
    this.label,
    this.street,
    this.city,
    this.postcode,
    this.region,
    this.country,
  });
  String? label, street, city, postcode, region, country;

  PostalAddress.fromMap(Map m) {
    label = m["label"];
    street = m["street"];
    city = m["city"];
    postcode = m["postcode"];
    region = m["region"];
    country = m["country"];
  }

  @override
  bool operator ==(Object other) {
    return other is PostalAddress &&
        city == other.city &&
        country == other.country &&
        label == other.label &&
        postcode == other.postcode &&
        region == other.region &&
        street == other.street;
  }

  @override
  int get hashCode {
    return hashObjects([
      label,
      street,
      city,
      country,
      region,
      postcode,
    ].where((s) => s != null));
  }

  static Map _toMap(PostalAddress address) => {
        "label": address.label,
        "street": address.street,
        "city": address.city,
        "postcode": address.postcode,
        "region": address.region,
        "country": address.country
      };

  @override
  String toString() {
    String finalString = "";
    if (street != null) {
      finalString += street!;
    }
    if (city != null) {
      if (finalString.isNotEmpty) {
        finalString += ", ${city!}";
      } else {
        finalString += city!;
      }
    }
    if (region != null) {
      if (finalString.isNotEmpty) {
        finalString += ", ${region!}";
      } else {
        finalString += region!;
      }
    }
    if (postcode != null) {
      if (finalString.isNotEmpty) {
        finalString += " ${postcode!}";
      } else {
        finalString += postcode!;
      }
    }
    if (country != null) {
      if (finalString.isNotEmpty) {
        finalString += ", ${country!}";
      } else {
        finalString += country!;
      }
    }
    return finalString;
  }
}

/// Item class used for contact fields which only have a [label] and
/// a [value], such as emails and phone numbers
class ValueItem {
  ValueItem({this.label, this.value});

  String? label, value;

  ValueItem.fromMap(Map m) {
    label = m["label"];
    value = m["value"];
  }

  @override
  bool operator ==(Object other) {
    return other is ValueItem && label == other.label && value == other.value;
  }

  @override
  int get hashCode => hash2(label ?? "", value ?? "");

  static Map _toMap(ValueItem i) => {"label": i.label, "value": i.value};
}

enum AndroidAccountType { facebook, google, whatsapp, other }
