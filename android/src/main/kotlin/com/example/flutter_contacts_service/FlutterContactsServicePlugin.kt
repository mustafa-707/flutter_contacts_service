package com.example.flutter_contacts_service

import android.app.Activity
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.BaseColumns
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.util.Log
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.*

private fun Cursor.getStringOrNull(columnIndex: Int): String? {
    return if (columnIndex != -1 && !isNull(columnIndex)) getString(columnIndex) else null
}

class FlutterContactsServicePlugin : MethodCallHandler, FlutterPlugin, ActivityAware {
    private var contentResolver: ContentResolver? = null
    private var methodChannel: MethodChannel? = null
    private var delegate: BaseContactsServiceDelegate? = null
    private var resources: Resources? = null
    private var scope = CoroutineScope(Dispatchers.Main + Job())
    private var activityBinding: ActivityPluginBinding? = null

    companion object {
        private const val FORM_OPERATION_CANCELED = 1
        private const val FORM_COULD_NOT_BE_OPEN = 2
        private const val LOG_TAG = "flutter_contacts"
        private const val CHANNEL_NAME = "flutter_contacts_service"
        private val PROJECTION =
            arrayOf(
                ContactsContract.Data.CONTACT_ID,
                ContactsContract.Profile.DISPLAY_NAME,
                ContactsContract.Contacts.Data.MIMETYPE,
                ContactsContract.RawContacts.ACCOUNT_TYPE,
                ContactsContract.RawContacts.ACCOUNT_NAME,
                StructuredName.DISPLAY_NAME,
                StructuredName.GIVEN_NAME,
                StructuredName.MIDDLE_NAME,
                StructuredName.FAMILY_NAME,
                StructuredName.PREFIX,
                StructuredName.SUFFIX,
                CommonDataKinds.Note.NOTE,
                Phone.NUMBER,
                Phone.TYPE,
                Phone.LABEL,
                Email.DATA,
                Email.ADDRESS,
                Email.TYPE,
                Email.LABEL,
                Organization.COMPANY,
                Organization.TITLE,
                StructuredPostal.FORMATTED_ADDRESS,
                StructuredPostal.TYPE,
                StructuredPostal.LABEL,
                StructuredPostal.STREET,
                StructuredPostal.POBOX,
                StructuredPostal.NEIGHBORHOOD,
                StructuredPostal.CITY,
                StructuredPostal.REGION,
                StructuredPostal.POSTCODE,
                StructuredPostal.COUNTRY
            )
    }

    private fun initInstance(messenger: BinaryMessenger, context: Context) {
        methodChannel = MethodChannel(messenger, CHANNEL_NAME)
        methodChannel?.setMethodCallHandler(this)
        contentResolver = context.contentResolver
    }

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        resources = binding.applicationContext.resources
        initInstance(binding.binaryMessenger, binding.applicationContext)
        delegate = ContactServiceDelegate(binding.applicationContext)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        scope.cancel()

        methodChannel?.setMethodCallHandler(null)
        methodChannel = null
        contentResolver = null
        delegate = null
        resources = null
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "getContacts" -> {
                scope.launch {
                    try {
                        val contacts =
                            withContext(Dispatchers.IO) {
                                getContactsImpl(
                                    call.method,
                                    call.argument("query"),
                                    call.argument("withThumbnails") ?: false,
                                    call.argument("photoHighResolution") ?: false,
                                    call.argument("orderByGivenName") ?: false,
                                    call.argument("androidLocalizedLabels") ?: false
                                )
                            }
                        result.success(contacts)
                    } catch (e: Exception) {
                        result.error("ERROR", e.message, null)
                    }
                }
            }
            "getContactsForPhone" -> {
                getContactsForPhone(
                    call.method,
                    call.argument<String>("phone"),
                    call.argument<Boolean>("withThumbnails") ?: false,
                    call.argument<Boolean>("photoHighResolution") ?: false,
                    call.argument<Boolean>("orderByGivenName") ?: false,
                    call.argument<Boolean>("androidLocalizedLabels") ?: false,
                    result
                )
            }
            "getContactsForEmail" -> {
                getContactsForEmail(
                    call.method,
                    call.argument<String>("email") ?: "",
                    call.argument<Boolean>("withThumbnails") ?: false,
                    call.argument<Boolean>("photoHighResolution") ?: false,
                    call.argument<Boolean>("orderByGivenName") ?: false,
                    call.argument<Boolean>("androidLocalizedLabels") ?: false,
                    result
                )
            }
            "getAvatar" -> {
                val contact = Contact.fromMap(call.argument<HashMap<String, Any>>("contact") ?: hashMapOf())
                getAvatar(contact, call.argument<Boolean>("photoHighResolution") ?: false, result)
            }
            "addContact" -> {
                scope.launch {
                    try {
                        val contactMap =
                            (call.arguments as? HashMap<*, *>)
                                ?.mapValues { it.value?.toString() ?: "" }
                                ?.filterKeys { it is String }
                                ?.mapKeys { it.key as String } ?: hashMapOf()

                        val contact = Contact.fromMap(contactMap)
                        val success = withContext(Dispatchers.IO) { addContact(contact) }

                        if (success) {
                            result.success(null)
                        } else {
                            result.error("null", "Failed to add the contact", null)
                        }
                    } catch (e: Exception) {
                        result.error("ERROR", "Failed to add contact: ${e.message}", null)
                    }
                }
            }
            "deleteContact" -> {
                scope.launch {
                    try {
                        val contactMap =
                            (call.arguments as? HashMap<*, *>)
                                ?.mapValues { it.value?.toString() ?: "" }
                                ?.filterKeys { it is String }
                                ?.mapKeys { it.key as String } ?: hashMapOf()

                        val contact = Contact.fromMap(contactMap)
                        val success = withContext(Dispatchers.IO) { deleteContact(contact) }

                        if (success) {
                            result.success(null)
                        } else {
                            result.error(
                                "null",
                                "Failed to delete the contact, make sure it has a valid identifier",
                                null
                            )
                        }
                    } catch (e: Exception) {
                        result.error("ERROR", "Failed to delete contact: ${e.message}", null)
                    }
                }
            }
            "updateContact" -> {
                scope.launch {
                    try {
                        val contactMap =
                            (call.arguments as? HashMap<*, *>)
                                ?.mapValues { it.value?.toString() ?: "" }
                                ?.filterKeys { it is String }
                                ?.mapKeys { it.key as String } ?: hashMapOf()

                        val contact = Contact.fromMap(contactMap)
                        val success = withContext(Dispatchers.IO) { updateContact(contact) }

                        if (success) {
                            result.success(null)
                        } else {
                            result.error(
                                "null",
                                "Failed to update the contact, make sure it has a valid identifier",
                                null
                            )
                        }
                    } catch (e: Exception) {
                        result.error("ERROR", "Failed to update contact: ${e.message}", null)
                    }
                }
            }
            "openExistingContact" -> {
                scope.launch {
                    try {
                        val contact = Contact.fromMap(call.argument<HashMap<String, Any>>("contact") ?: hashMapOf())
                        val localizedLabels = call.argument<Boolean>("androidLocalizedLabels") ?: false

                        withContext(Dispatchers.Main) {
                            delegate?.let {
                                it.updateResult(result)
                                it.updateLocalizedLabels(localizedLabels)
                                it.openExistingContact(contact)
                            } ?: run { result.success(FORM_COULD_NOT_BE_OPEN) }
                        }
                    } catch (e: Exception) {
                        result.error("ERROR", "Failed to open existing contact: ${e.message}", null)
                    }
                }
            }
            "openContactForm" -> {
                scope.launch {
                    try {
                        val localizedLabels = call.argument<Boolean>("androidLocalizedLabels") ?: false

                        withContext(Dispatchers.Main) {
                            delegate?.let {
                                it.updateResult(result)
                                it.updateLocalizedLabels(localizedLabels)
                                it.openContactForm()
                            } ?: run { result.success(FORM_COULD_NOT_BE_OPEN) }
                        }
                    } catch (e: Exception) {
                        result.error("ERROR", "Failed to open contact form: ${e.message}", null)
                    }
                }
            }
            "openDeviceContactPicker" -> {
                scope.launch {
                    try {
                        val localizedLabels = call.argument<Boolean>("androidLocalizedLabels") ?: false

                        withContext(Dispatchers.Main) { openDeviceContactPicker(result, localizedLabels) }
                    } catch (e: Exception) {
                        result.error("ERROR", "Failed to open device contact picker: ${e.message}", null)
                    }
                }
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    private fun openDeviceContactPicker(result: MethodChannel.Result, localizedLabels: Boolean) {
        delegate?.let {
            it.updateResult(result)
            it.updateLocalizedLabels(localizedLabels)
            it.openContactPicker()
        } ?: run { result.success(FORM_COULD_NOT_BE_OPEN) }
    }

    private suspend fun getContactsImpl(
        callMethod: String,
        query: String?,
        withThumbnails: Boolean,
        photoHighResolution: Boolean,
        orderByGivenName: Boolean,
        localizedLabels: Boolean
    ): List<Map<String, Any?>> =
        withContext(Dispatchers.IO) {
            val contacts =
                when (callMethod) {
                    "openDeviceContactPicker" -> getContactsFrom(getCursor(null, query), localizedLabels)
                    "getContacts" -> getContactsFrom(getCursor(query, null), localizedLabels)
                    "getContactsForPhone" -> getContactsFrom(getCursorForPhone(query ?: ""), localizedLabels)
                    "getContactsForEmail" -> getContactsFrom(getCursorForEmail(query ?: ""), localizedLabels)
                    else -> emptyList()
                }

            if (withThumbnails) {
                contacts.forEach { contact ->
                    contact.identifier?.let { id ->
                        val avatar = loadContactPhotoHighRes(id, photoHighResolution, contentResolver)
                        contact.avatar = avatar ?: ByteArray(0)
                    }
                }
            }

            if (orderByGivenName) {
                contacts.sortedWith { a, b -> a.compareTo(b) }
            }

            contacts.map { it.toMap() }
        }

    private fun getContactsForPhone(
        callMethod: String,
        phone: String?,
        withThumbnails: Boolean,
        photoHighResolution: Boolean,
        orderByGivenName: Boolean,
        localizedLabels: Boolean,
        result: MethodChannel.Result
    ) {
        scope.launch {
            try {
                val contacts =
                    withContext(Dispatchers.IO) {
                        getContactsImpl(
                            callMethod,
                            phone,
                            withThumbnails,
                            photoHighResolution,
                            orderByGivenName,
                            localizedLabels
                        )
                    }
                result.success(contacts)
            } catch (e: Exception) {
                result.error("ERROR", e.message, null)
            }
        }
    }

    private fun getContactsForEmail(
        callMethod: String,
        email: String,
        withThumbnails: Boolean,
        photoHighResolution: Boolean,
        orderByGivenName: Boolean,
        localizedLabels: Boolean,
        result: MethodChannel.Result
    ) {
        scope.launch {
            try {
                val contacts =
                    withContext(Dispatchers.IO) {
                        getContactsImpl(
                            callMethod,
                            email,
                            withThumbnails,
                            photoHighResolution,
                            orderByGivenName,
                            localizedLabels
                        )
                    }
                result.success(contacts)
            } catch (e: Exception) {
                result.error("ERROR", e.message, null)
            }
        }
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        delegate?.bindToActivity(binding)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        delegate?.unbindActivity()
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        delegate?.bindToActivity(binding)
    }

    override fun onDetachedFromActivity() {
        delegate?.unbindActivity()
    }

    private abstract inner class BaseContactsServiceDelegate {
        protected val REQUEST_OPEN_CONTACT_FORM = 52941
        protected val REQUEST_OPEN_EXISTING_CONTACT = 52942
        protected val REQUEST_OPEN_CONTACT_PICKER = 52943
        private var methodChannelResult: MethodChannel.Result? = null
        protected var useLocalizedLabels: Boolean = false

        fun updateResult(newResult: MethodChannel.Result) {
            methodChannelResult = newResult
        }

        fun updateLocalizedLabels(useLocalized: Boolean) {
            useLocalizedLabels = useLocalized
        }

        protected fun finishWithResult(result: Any?) {
            methodChannelResult?.success(result)
            methodChannelResult = null
        }

        protected fun getResult(): MethodChannel.Result? = methodChannelResult

        protected fun getLocalizedLabels(): Boolean = useLocalizedLabels

        abstract fun startIntent(intent: Intent, request: Int)

        abstract fun bindToActivity(binding: ActivityPluginBinding)

        abstract fun unbindActivity()

        abstract fun openExistingContact(contact: Contact)

        abstract fun openContactForm()

        abstract fun openContactPicker()
    }

    private inner class ContactServiceDelegate(private val context: Context) : BaseContactsServiceDelegate() {
        private var binding: ActivityPluginBinding? = null

        override fun bindToActivity(binding: ActivityPluginBinding) {
            this.binding = binding
            binding.addActivityResultListener { requestCode, resultCode, data ->
                handleActivityResult(requestCode, resultCode, data)
            }
        }

        override fun unbindActivity() {
            binding?.removeActivityResultListener { requestCode, resultCode, data ->
                handleActivityResult(requestCode, resultCode, data)
            }
            binding = null
        }

        override fun startIntent(intent: Intent, request: Int) {
            val activity =
                binding?.activity
                    ?: run {
                        finishWithResult(FORM_COULD_NOT_BE_OPEN)
                        return
                    }

            if (intent.resolveActivity(context.packageManager) != null) {
                activity.startActivityForResult(intent, request)
            } else {
                finishWithResult(FORM_COULD_NOT_BE_OPEN)
            }
        }

        private fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
            when (requestCode) {
                REQUEST_OPEN_CONTACT_PICKER -> {
                    if (resultCode == Activity.RESULT_CANCELED) {
                        finishWithResult(FORM_OPERATION_CANCELED)
                        return true
                    }
                    val contactUri = data?.data
                    if (contactUri != null) {
                        val cursor = contentResolver?.query(contactUri, null, null, null, null)
                        if (cursor?.moveToFirst() == true) {
                            val id = contactUri.lastPathSegment
                            scope.launch {
                                try {
                                    val contacts =
                                        withContext(Dispatchers.IO) {
                                            getContactsImpl(
                                                "openDeviceContactPicker",
                                                id,
                                                false,
                                                false,
                                                false,
                                                useLocalizedLabels
                                            )
                                        }
                                    finishWithResult(contacts)
                                } catch (e: Exception) {
                                    finishWithResult(FORM_OPERATION_CANCELED)
                                }
                            }
                        }
                        cursor?.close()
                    }
                    return true
                }
            }
            return false
        }

        private fun getContactByIdentifier(identifier: String?): HashMap<String, Any>? {
            if (identifier == null) return null

            return try {
                val cursor =
                    contentResolver?.query(
                        ContactsContract.Data.CONTENT_URI,
                        PROJECTION,
                        "${ContactsContract.RawContacts.CONTACT_ID} = ?",
                        arrayOf(identifier),
                        null
                    )

                cursor?.use {
                    val contacts = getContactsFrom(cursor, useLocalizedLabels)
                    contacts.firstOrNull()?.toMap() as? HashMap<String, Any>
                }
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Error getting contact: ${e.message}")
                null
            }
        }

        override fun openExistingContact(contact: Contact) {
            val identifier = contact.identifier ?: ""
            try {
                val contactMapFromDevice = getContactByIdentifier(identifier)
                if (contactMapFromDevice != null) {
                    val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, identifier)
                    val intent =
                        Intent(Intent.ACTION_EDIT).apply {
                            setDataAndType(uri, ContactsContract.Contacts.CONTENT_ITEM_TYPE)
                            putExtra("finishActivityOnSaveCompleted", true)
                        }
                    startIntent(intent, REQUEST_OPEN_EXISTING_CONTACT)
                } else {
                    finishWithResult(FORM_COULD_NOT_BE_OPEN)
                }
            } catch (e: Exception) {
                finishWithResult(FORM_COULD_NOT_BE_OPEN)
            }
        }

        override fun openContactForm() {
            try {
                val intent =
                    Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI).apply {
                        putExtra("finishActivityOnSaveCompleted", true)
                    }
                startIntent(intent, REQUEST_OPEN_CONTACT_FORM)
            } catch (e: Exception) {
                finishWithResult(FORM_COULD_NOT_BE_OPEN)
            }
        }

        override fun openContactPicker() {
            val intent = Intent(Intent.ACTION_PICK).apply { type = ContactsContract.Contacts.CONTENT_TYPE }
            startIntent(intent, REQUEST_OPEN_CONTACT_PICKER)
        }
    }

    private fun getCursor(query: String?, rawContactId: String?): Cursor? {
        var selection =
            "(" +
                ContactsContract.Data.MIMETYPE +
                "=? OR " +
                ContactsContract.Data.MIMETYPE +
                "=? OR " +
                ContactsContract.Data.MIMETYPE +
                "=? OR " +
                ContactsContract.Data.MIMETYPE +
                "=? OR " +
                ContactsContract.Data.MIMETYPE +
                "=? OR " +
                ContactsContract.Data.MIMETYPE +
                "=? OR " +
                ContactsContract.Data.MIMETYPE +
                "=? OR " +
                ContactsContract.RawContacts.ACCOUNT_TYPE +
                "=?)"

        val selectionArgs =
            mutableListOf(
                ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
                ContactsContract.RawContacts.ACCOUNT_TYPE
            )

        if (query != null) {
            selection = ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " LIKE ?"
            selectionArgs.clear()
            selectionArgs.add("$query%")
        }

        if (rawContactId != null) {
            selection += " AND " + ContactsContract.Data.CONTACT_ID + " =?"
            selectionArgs.add(rawContactId)
        }

        return contentResolver?.query(
            ContactsContract.Data.CONTENT_URI,
            PROJECTION,
            selection,
            selectionArgs.toTypedArray(),
            null
        )
    }

    private fun getCursorForPhone(phone: String): Cursor? {
        if (phone.isEmpty()) return null

        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phone))
        val projection = arrayOf(BaseColumns._ID)

        val contactIds = mutableListOf<String>()
        val phoneCursor = contentResolver?.query(uri, projection, null, null, null)
        phoneCursor?.use {
            while (it.moveToNext()) {
                contactIds.add(it.getString(it.getColumnIndex(BaseColumns._ID)))
            }
        }

        if (contactIds.isNotEmpty()) {
            val contactIdsListString = contactIds.joinToString(",", "(", ")")
            val contactSelection = "${ContactsContract.Data.CONTACT_ID} IN $contactIdsListString"
            return contentResolver?.query(ContactsContract.Data.CONTENT_URI, PROJECTION, contactSelection, null, null)
        }

        return null
    }

    private fun getCursorForEmail(email: String): Cursor? {
        if (email.isEmpty()) return null

        val selection = "${ContactsContract.CommonDataKinds.Email.ADDRESS} LIKE ?"
        val selectionArgs = arrayOf("%$email%")

        return contentResolver?.query(ContactsContract.Data.CONTENT_URI, PROJECTION, selection, selectionArgs, null)
    }

    private fun getContactsFrom(cursor: Cursor?, localizedLabels: Boolean): ArrayList<Contact> {
        val map = linkedMapOf<String, Contact>()

        cursor?.use { safeCursor ->
            while (safeCursor.moveToNext()) {
                try {

                    val contactId =
                        safeCursor.getStringOrNull(safeCursor.getColumnIndex(ContactsContract.Data.CONTACT_ID))
                            ?: continue

                    val contact = map.getOrPut(contactId) { Contact(contactId) }

                    safeCursor.apply {
                        val mimeType = getStringOrNull(getColumnIndex(ContactsContract.Data.MIMETYPE))
                        contact.apply {
                            displayName = getStringOrNull(getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME))
                            androidAccountType =
                                getStringOrNull(getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE))
                            androidAccountName =
                                getStringOrNull(getColumnIndex(ContactsContract.RawContacts.ACCOUNT_NAME))
                        }

                        when (mimeType) {
                            ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
                                contact.apply {
                                    givenName = getStringOrNull(getColumnIndex(StructuredName.GIVEN_NAME))
                                    middleName = getStringOrNull(getColumnIndex(StructuredName.MIDDLE_NAME))
                                    familyName = getStringOrNull(getColumnIndex(StructuredName.FAMILY_NAME))
                                    prefix = getStringOrNull(getColumnIndex(StructuredName.PREFIX))
                                    suffix = getStringOrNull(getColumnIndex(StructuredName.SUFFIX))
                                }
                            }
                            ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE -> {
                                contact.note = getStringOrNull(getColumnIndex(CommonDataKinds.Note.NOTE))
                            }
                            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                                getStringOrNull(getColumnIndex(CommonDataKinds.Phone.NUMBER))?.let { phoneNumber ->
                                    if (phoneNumber.isNotBlank()) {
                                        val type = getInt(getColumnIndex(CommonDataKinds.Phone.TYPE))
                                        val label = Item.getPhoneLabel(resources, type, safeCursor, localizedLabels)
                                        contact.phones.add(Item(label, phoneNumber, type))
                                    }
                                }
                            }
                            ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> {
                                getStringOrNull(getColumnIndex(CommonDataKinds.Email.ADDRESS))?.let { email ->
                                    if (email.isNotBlank()) {
                                        val type = getInt(getColumnIndex(CommonDataKinds.Email.TYPE))
                                        val label = Item.getEmailLabel(resources, type, safeCursor, localizedLabels)
                                        contact.emails.add(Item(label, email, type))
                                    }
                                }
                            }
                            ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> {
                                contact.apply {
                                    company = getStringOrNull(getColumnIndex(CommonDataKinds.Organization.COMPANY))
                                    jobTitle = getStringOrNull(getColumnIndex(CommonDataKinds.Organization.TITLE))
                                }
                            }
                            ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> {
                                val type = getInt(getColumnIndex(CommonDataKinds.StructuredPostal.TYPE))
                                val label = PostalAddress.getLabel(resources, type, safeCursor, localizedLabels)
                                val street =
                                    getStringOrNull(getColumnIndex(CommonDataKinds.StructuredPostal.STREET)) ?: ""
                                val city = getStringOrNull(getColumnIndex(CommonDataKinds.StructuredPostal.CITY)) ?: ""
                                val postcode =
                                    getStringOrNull(getColumnIndex(CommonDataKinds.StructuredPostal.POSTCODE)) ?: ""
                                val region =
                                    getStringOrNull(getColumnIndex(CommonDataKinds.StructuredPostal.REGION)) ?: ""
                                val country =
                                    getStringOrNull(getColumnIndex(CommonDataKinds.StructuredPostal.COUNTRY)) ?: ""

                                contact.postalAddresses.add(
                                    PostalAddress(label, street, city, postcode, region, country, type)
                                )
                            }
                            ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE -> {
                                val eventType = getInt(getColumnIndex(CommonDataKinds.Event.TYPE))
                                if (eventType == CommonDataKinds.Event.TYPE_BIRTHDAY) {
                                    contact.birthday = getStringOrNull(getColumnIndex(CommonDataKinds.Event.START_DATE))
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "Error processing contact: ${e.message}")
                    continue
                }
            }
        }

        return ArrayList(map.values)
    }

    private fun setAvatarDataForContactIfAvailable(contact: Contact) {
        val contactUri =
            ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contact.identifier!!.toLong())
        val photoUri = Uri.withAppendedPath(contactUri, ContactsContract.Contacts.Photo.CONTENT_DIRECTORY)
        val avatarCursor =
            contentResolver?.query(photoUri, arrayOf(ContactsContract.Contacts.Photo.PHOTO), null, null, null)
        avatarCursor?.use {
            if (it.moveToFirst()) {
                contact.avatar = it.getBlob(0)
            }
        }
    }

    private fun getAvatar(contact: Contact, highRes: Boolean, result: MethodChannel.Result) {
        scope.launch {
            try {
                val avatar =
                    withContext(Dispatchers.IO) {
                        loadContactPhotoHighRes(contact.identifier, highRes, contentResolver)
                    }
                result.success(avatar)
            } catch (e: Exception) {
                result.error("ERROR", e.message, null)
            }
        }
    }

    private suspend fun loadContactPhotoHighRes(
        identifier: String?,
        highRes: Boolean,
        contentResolver: ContentResolver?
    ): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                if (identifier == null || contentResolver == null) return@withContext null

                val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, identifier.toLong())
                ContactsContract.Contacts.openContactPhotoInputStream(contentResolver, uri, highRes)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    ByteArrayOutputStream().use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                        outputStream.toByteArray()
                    }
                }
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Error loading contact photo: ${e.message}")
                null
            }
        }

    private fun addContact(contact: Contact): Boolean {
        val ops = ArrayList<android.content.ContentProviderOperation>()

        ops.add(
            android.content.ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build()
        )

        ops.add(
            android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(
                    ContactsContract.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                )
                .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, contact.givenName)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME, contact.middleName)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, contact.familyName)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.PREFIX, contact.prefix)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.SUFFIX, contact.suffix)
                .build()
        )

        ops.add(
            android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Note.NOTE, contact.note)
                .build()
        )

        ops.add(
            android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(
                    ContactsContract.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE
                )
                .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, contact.company)
                .withValue(ContactsContract.CommonDataKinds.Organization.TITLE, contact.jobTitle)
                .build()
        )

        contact.phones.forEach { phone ->
            val op =
                android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone.value)

            if (phone.type == ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM) {
                op.withValue(
                        ContactsContract.CommonDataKinds.Phone.TYPE,
                        ContactsContract.CommonDataKinds.BaseTypes.TYPE_CUSTOM
                    )
                    .withValue(ContactsContract.CommonDataKinds.Phone.LABEL, phone.label)
            } else {
                op.withValue(ContactsContract.CommonDataKinds.Phone.TYPE, phone.type)
            }
            ops.add(op.build())
        }

        contact.emails.forEach { email ->
            ops.add(
                android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email.value)
                    .withValue(ContactsContract.CommonDataKinds.Email.TYPE, email.type)
                    .build()
            )
        }

        contact.postalAddresses.forEach { address ->
            ops.add(
                android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE
                    )
                    .withValue(ContactsContract.CommonDataKinds.StructuredPostal.TYPE, address.type)
                    .withValue(ContactsContract.CommonDataKinds.StructuredPostal.STREET, address.street)
                    .withValue(ContactsContract.CommonDataKinds.StructuredPostal.CITY, address.city)
                    .withValue(ContactsContract.CommonDataKinds.StructuredPostal.REGION, address.region)
                    .withValue(ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE, address.postcode)
                    .withValue(ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY, address.country)
                    .build()
            )
        }

        try {
            contentResolver?.applyBatch(ContactsContract.AUTHORITY, ops)
            return true
        } catch (e: Exception) {
            Log.e("ContactsService", "Error adding contact: ${e.message}")
            return false
        }
    }

    private fun deleteContact(contact: Contact): Boolean {
        val ops =
            arrayListOf(
                android.content.ContentProviderOperation.newDelete(ContactsContract.RawContacts.CONTENT_URI)
                    .withSelection("${ContactsContract.RawContacts.CONTACT_ID}=?", arrayOf(contact.identifier))
                    .build()
            )

        return try {
            contentResolver?.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        } catch (e: Exception) {
            Log.e("ContactsService", "Error deleting contact: ${e.message}")
            false
        }
    }

    private fun updateContact(contact: Contact): Boolean {
        val ops = ArrayList<android.content.ContentProviderOperation>()

        val deleteMimeTypes =
            listOf(
                ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE
            )

        deleteMimeTypes.forEach { mimeType ->
            ops.add(
                android.content.ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                    .withSelection(
                        "${ContactsContract.Data.CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?",
                        arrayOf(contact.identifier, mimeType)
                    )
                    .build()
            )
        }

        ops.add(
            android.content.ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                .withSelection(
                    "${ContactsContract.Data.CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?",
                    arrayOf(contact.identifier, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                )
                .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, contact.givenName)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME, contact.middleName)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, contact.familyName)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.PREFIX, contact.prefix)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.SUFFIX, contact.suffix)
                .build()
        )

        ops.add(
            android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(
                    ContactsContract.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE
                )
                .withValue(ContactsContract.Data.RAW_CONTACT_ID, contact.identifier)
                .withValue(
                    ContactsContract.CommonDataKinds.Organization.TYPE,
                    ContactsContract.CommonDataKinds.Organization.TYPE_WORK
                )
                .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, contact.company)
                .withValue(ContactsContract.CommonDataKinds.Organization.TITLE, contact.jobTitle)
                .build()
        )

        ops.add(
            android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.Data.RAW_CONTACT_ID, contact.identifier)
                .withValue(ContactsContract.CommonDataKinds.Note.NOTE, contact.note)
                .build()
        )

        ops.add(
            android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(ContactsContract.Data.RAW_CONTACT_ID, contact.identifier)
                .withValue(ContactsContract.Data.IS_SUPER_PRIMARY, 1)
                .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, contact.avatar)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                .build()
        )

        contact.phones.forEach { phone ->
            val op =
                android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, contact.identifier)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone.value)

            if (phone.type == ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM) {
                op.withValue(
                        ContactsContract.CommonDataKinds.Phone.TYPE,
                        ContactsContract.CommonDataKinds.BaseTypes.TYPE_CUSTOM
                    )
                    .withValue(ContactsContract.CommonDataKinds.Phone.LABEL, phone.label)
            } else {
                op.withValue(ContactsContract.CommonDataKinds.Phone.TYPE, phone.type)
            }
            ops.add(op.build())
        }

        contact.emails.forEach { email ->
            ops.add(
                android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, contact.identifier)
                    .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email.value)
                    .withValue(ContactsContract.CommonDataKinds.Email.TYPE, email.type)
                    .build()
            )
        }

        contact.postalAddresses.forEach { address ->
            ops.add(
                android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE
                    )
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, contact.identifier)
                    .withValue(ContactsContract.CommonDataKinds.StructuredPostal.TYPE, address.type)
                    .withValue(ContactsContract.CommonDataKinds.StructuredPostal.STREET, address.street)
                    .withValue(ContactsContract.CommonDataKinds.StructuredPostal.CITY, address.city)
                    .withValue(ContactsContract.CommonDataKinds.StructuredPostal.REGION, address.region)
                    .withValue(ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE, address.postcode)
                    .withValue(ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY, address.country)
                    .build()
            )
        }

        return try {
            contentResolver?.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        } catch (e: Exception) {
            Log.e("ContactsService", "Error updating contact: ${e.message}")
            false
        }
    }
}

data class PostalAddress(
    var label: String?,
    var street: String?,
    var city: String?,
    var postcode: String?,
    var region: String?,
    var country: String?,
    var type: Int
) {

    fun toMap(): Map<String, String> {
        return mapOf(
            "label" to (label ?: ""),
            "street" to (street ?: ""),
            "city" to (city ?: ""),
            "postcode" to (postcode ?: ""),
            "region" to (region ?: ""),
            "country" to (country ?: ""),
            "type" to type.toString()
        )
    }

    companion object {
        fun fromMap(map: Map<String, String>): PostalAddress {
            val label = map["label"]
            val street = map["street"]
            val city = map["city"]
            val postcode = map["postcode"]
            val region = map["region"]
            val country = map["country"]
            val type = map["type"]?.toIntOrNull() ?: -1
            return PostalAddress(label, street, city, postcode, region, country, type)
        }

        fun getLabel(resources: Resources?, type: Int, cursor: Cursor, localizedLabels: Boolean): String {
            return if (localizedLabels && resources != null) {
                CommonDataKinds.StructuredPostal.getTypeLabel(resources, type, "").toString().lowercase()
            } else {
                when (cursor.getInt(cursor.getColumnIndex(StructuredPostal.TYPE))) {
                    StructuredPostal.TYPE_HOME -> "home"
                    StructuredPostal.TYPE_WORK -> "work"
                    StructuredPostal.TYPE_CUSTOM -> {
                        cursor?.getString(cursor.getColumnIndex(StructuredPostal.LABEL)) ?: ""
                    }
                    else -> "other"
                }
            }
        }
    }

    private fun Cursor.getStringOrNull(columnIndex: Int): String? {
        return if (columnIndex != -1 && !isNull(columnIndex)) getString(columnIndex) else null
    }

    private fun Cursor.getIntOrNull(columnIndex: Int): Int? {
        return if (columnIndex != -1 && !isNull(columnIndex)) getInt(columnIndex) else null
    }
}

data class Contact(
    var identifier: String?,
    var displayName: String? = null,
    var givenName: String? = null,
    var middleName: String? = null,
    var familyName: String? = null,
    var prefix: String? = null,
    var suffix: String? = null,
    var company: String? = null,
    var jobTitle: String? = null,
    var note: String? = null,
    var birthday: String? = null,
    var androidAccountType: String? = null,
    var androidAccountName: String? = null,
    var emails: MutableList<Item> = mutableListOf(),
    var phones: MutableList<Item> = mutableListOf(),
    var postalAddresses: MutableList<PostalAddress> = mutableListOf(),
    var avatar: ByteArray = byteArrayOf()
) : Comparable<Contact> {

    fun toMap(): HashMap<String, Any> {
        return hashMapOf(
            "identifier" to (identifier ?: ""),
            "displayName" to (displayName ?: ""),
            "givenName" to (givenName ?: ""),
            "middleName" to (middleName ?: ""),
            "familyName" to (familyName ?: ""),
            "prefix" to (prefix ?: ""),
            "suffix" to (suffix ?: ""),
            "company" to (company ?: ""),
            "jobTitle" to (jobTitle ?: ""),
            "avatar" to (avatar ?: byteArrayOf()),
            "note" to (note ?: ""),
            "birthday" to (birthday ?: ""),
            "androidAccountType" to (androidAccountType ?: ""),
            "androidAccountName" to (androidAccountName ?: ""),
            "emails" to emails.map { it.toMap() },
            "phones" to phones.map { it.toMap() },
            "postalAddresses" to postalAddresses.map { it.toMap() }
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any?>): Contact {
            val contact =
                Contact(
                    identifier = map["identifier"] as? String,
                    displayName = map["displayName"] as? String,
                    givenName = map["givenName"] as? String,
                    middleName = map["middleName"] as? String,
                    familyName = map["familyName"] as? String,
                    prefix = map["prefix"] as? String,
                    suffix = map["suffix"] as? String,
                    company = map["company"] as? String,
                    jobTitle = map["jobTitle"] as? String,
                    note = map["note"] as? String,
                    birthday = map["birthday"] as? String,
                    androidAccountType = map["androidAccountType"] as? String,
                    androidAccountName = map["androidAccountName"] as? String
                )

            (map["emails"] as? List<Map<String, String>>)?.forEach { contact.emails.add(Item.fromMap(it)) }

            (map["phones"] as? List<Map<String, String>>)?.forEach { contact.phones.add(Item.fromMap(it)) }

            (map["postalAddresses"] as? List<Map<String, String>>)?.forEach {
                contact.postalAddresses.add(PostalAddress.fromMap(it))
            }

            contact.avatar = map["avatar"] as? ByteArray ?: byteArrayOf()

            return contact
        }
    }

    override fun compareTo(other: Contact): Int {
        return (givenName ?: "").compareTo(other.givenName ?: "")
    }
}

/**
 * Represents an object which has a label and a value such as an email or a phone
 * *
 */
data class Item(var label: String?, var value: String?, var type: Int) {

    fun toMap(): Map<String, String> {
        return mapOf("label" to (label ?: ""), "value" to (value ?: ""), "type" to type.toString())
    }

    companion object {
        fun fromMap(map: Map<String, String>): Item {
            val label = map["label"]
            val value = map["value"]
            val type = map["type"]?.toIntOrNull() ?: -1
            return Item(label, value, type)
        }

        fun getPhoneLabel(resources: Resources?, type: Int, cursor: Cursor, localizedLabels: Boolean): String {
            return if (localizedLabels && resources != null) {
                CommonDataKinds.Phone.getTypeLabel(resources, type, "").toString().lowercase()
            } else {
                when (type) {
                    CommonDataKinds.Phone.TYPE_HOME -> "home"
                    CommonDataKinds.Phone.TYPE_WORK -> "work"
                    CommonDataKinds.Phone.TYPE_MOBILE -> "mobile"
                    CommonDataKinds.Phone.TYPE_FAX_WORK -> "fax work"
                    CommonDataKinds.Phone.TYPE_FAX_HOME -> "fax home"
                    CommonDataKinds.Phone.TYPE_MAIN -> "main"
                    CommonDataKinds.Phone.TYPE_COMPANY_MAIN -> "company"
                    CommonDataKinds.Phone.TYPE_PAGER -> "pager"
                    CommonDataKinds.Phone.TYPE_CUSTOM -> {
                        cursor?.getString(cursor.getColumnIndex(CommonDataKinds.Phone.LABEL))?.lowercase() ?: ""
                    }
                    else -> "other"
                }
            }
        }

        fun getEmailLabel(resources: Resources?, type: Int, cursor: Cursor, localizedLabels: Boolean): String {
            return if (localizedLabels && resources != null) {
                CommonDataKinds.Email.getTypeLabel(resources, type, "").toString().lowercase()
            } else {
                when (type) {
                    CommonDataKinds.Email.TYPE_HOME -> "home"
                    CommonDataKinds.Email.TYPE_WORK -> "work"
                    CommonDataKinds.Email.TYPE_MOBILE -> "mobile"
                    CommonDataKinds.Email.TYPE_CUSTOM -> {
                        cursor?.getString(cursor.getColumnIndex(CommonDataKinds.Email.LABEL))?.lowercase() ?: ""
                    }
                    else -> "other"
                }
            }
        }
    }
}
