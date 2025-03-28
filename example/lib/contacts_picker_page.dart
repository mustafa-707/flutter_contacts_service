import 'dart:developer';

import 'package:flutter/material.dart';
import 'package:flutter_contacts_service/flutter_contacts_service.dart';
import 'package:flutter_contacts_service_example/main.dart';

class ContactPickerPage extends StatefulWidget {
  const ContactPickerPage({super.key});

  @override
  State<ContactPickerPage> createState() => _ContactPickerPageState();
}

class _ContactPickerPageState extends State<ContactPickerPage> {
  ContactInfo? _contact;

  @override
  void initState() {
    super.initState();
  }

  Future<void> _pickContact() async {
    try {
      final ContactInfo? contact =
          await FlutterContactsService.openDeviceContactPicker(
        iOSLocalizedLabels: iOSLocalizedLabels,
      );
      setState(() {
        _contact = contact;
      });
    } catch (e) {
      log(e.toString());
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Contacts Picker Example')),
      body: SafeArea(
          child: Column(
        children: <Widget>[
          ElevatedButton(
            onPressed: _pickContact,
            child: const Text('Pick a contact'),
          ),
          if (_contact != null)
            Text('Contact selected: ${_contact?.displayName}'),
        ],
      )),
    );
  }
}
