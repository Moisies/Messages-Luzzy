package app.luzzy.activities

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
import android.content.res.ColorStateList
import android.graphics.drawable.LayerDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Telephony
import android.provider.Telephony.Sms.MESSAGE_TYPE_QUEUED
import android.provider.Telephony.Sms.STATUS_NONE
import android.speech.RecognizerIntent
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.telephony.SubscriptionInfo
import android.text.TextUtils
import android.text.format.DateUtils
import android.text.format.DateUtils.FORMAT_NO_YEAR
import android.text.format.DateUtils.FORMAT_SHOW_DATE
import android.text.format.DateUtils.FORMAT_SHOW_TIME
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.res.ResourcesCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.goodwy.commons.dialogs.ConfirmationDialog
import com.goodwy.commons.dialogs.PermissionRequiredDialog
import com.goodwy.commons.dialogs.RadioGroupDialog
import com.goodwy.commons.dialogs.RadioGroupIconDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.PhoneNumber
import com.goodwy.commons.models.RadioItem
import com.goodwy.commons.models.SimpleContact
import app.luzzy.BuildConfig
import app.luzzy.R
import app.luzzy.adapters.AttachmentsAdapter
import app.luzzy.adapters.AutoCompleteTextViewAdapter
import app.luzzy.adapters.ThreadAdapter
import app.luzzy.databinding.ActivityThreadBinding
import app.luzzy.databinding.ItemSelectedContactBinding
import app.luzzy.dialogs.InvalidNumberDialog
import app.luzzy.dialogs.RenameConversationDialog
import app.luzzy.dialogs.ScheduleMessageDialog
import app.luzzy.extensions.*
import app.luzzy.helpers.*
import app.luzzy.messaging.*
import app.luzzy.models.*
import app.luzzy.models.SendMode
import app.luzzy.models.ThreadItem.ThreadDateTime
import app.luzzy.models.ThreadItem.ThreadError
import app.luzzy.models.ThreadItem.ThreadSending
import app.luzzy.models.ThreadItem.ThreadSent
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.joda.time.DateTime
import java.io.File
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.collections.set

class ThreadActivity : SimpleActivity() {
    private var threadId = 0L
    private var currentSIMCardIndex = 0
    private var isActivityVisible = false
    private var refreshedSinceSent = false
    private var threadItems = ArrayList<ThreadItem>()
    private var bus: EventBus? = null
    private var conversation: Conversation? = null
    private var participants = ArrayList<SimpleContact>()
    private var privateContacts = ArrayList<SimpleContact>()
    private var messages = ArrayList<Message>()
    private val availableSIMCards = ArrayList<SIMCard>()
    private var pendingAttachmentsToSave: List<Attachment>? = null
    private var capturedImageUri: Uri? = null
    private var loadingOlderMessages = false
    private var allMessagesFetched = false
    private var isJumpingToMessage = false
    private var isRecycleBin = false
    private var isLaunchedFromShortcut = false

    private var isScheduledMessage: Boolean = false
    private var messageToResend: Long? = null
    private var scheduledMessage: Message? = null
    private lateinit var scheduledDateTime: DateTime

    private var isAttachmentPickerVisible = false
    private var isSpeechToTextAvailable = false

    private val binding by viewBinding(ActivityThreadBinding::inflate)
    private val sendModeRepository by lazy { ContactSendModeRepository(this) }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        finish()
        startActivity(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupOptionsMenu()
        refreshMenuItems()

        val bottomView = if (isRecycleBin) binding.threadMessagesList else binding.messageHolder.root
        setupEdgeToEdge(

            padBottomImeAndSystem = listOf(
                bottomView,
                binding.shortCodeHolder.root
            ),
            animateIme= true
        )

        val extras = intent.extras
        if (extras == null) {
            toast(com.goodwy.commons.R.string.unknown_error_occurred)
            finish()
            return
        }

        isSpeechToTextAvailable = if (config.useSpeechToText) isSpeechToTextAvailable() else false

        threadId = intent.getLongExtra(THREAD_ID, 0L)

        isRecycleBin = intent.getBooleanExtra(IS_RECYCLE_BIN, false)
        isLaunchedFromShortcut = intent.getBooleanExtra(IS_LAUNCHED_FROM_SHORTCUT, false)

        bus = EventBus.getDefault()
        bus!!.register(this)

        loadConversation()
        setupAttachmentPickerView()
        hideAttachmentPicker()
        maybeSetupRecycleBinView()
    }

    override fun onResume() {
        super.onResume()
        if (config.threadTopStyle == THREAD_TOP_LARGE) binding.topDetailsCompact.root.beGone()
        else binding.topDetailsLarge.beGone()

        val topBarColor = getColoredMaterialStatusBarColor()
        setupTopAppBar(
            topAppBar = binding.threadAppbar,
            navigationIcon = NavigationIcon.Arrow,
            topBarColor = topBarColor,
            appBarLayout = binding.threadAppbar
        )
        setupToolbar(
            toolbar = binding.threadToolbar,
            toolbarNavigationIcon = NavigationIcon.Arrow,
        )
        updateToolbarColors(binding.threadToolbar, topBarColor, useOverflowIcon = false)
        binding.threadToolbar.setBackgroundColor(topBarColor)

        isActivityVisible = true

        notificationManager.cancel(threadId.hashCode())

        ensureBackgroundThread {
            val newConv = conversationsDB.getConversationWithThreadId(threadId)
            if (newConv != null) {
                conversation = newConv
                runOnUiThread {
                    setupThreadTitle()
                }
            }

            val smsDraft = getSmsDraft(threadId)
            if (smsDraft.isNotEmpty()) {
                runOnUiThread {
                    binding.messageHolder.threadTypeMessage.setText(smsDraft)
                    binding.messageHolder.threadTypeMessage.setSelection(smsDraft.length)
                    binding.messageHolder.threadCharacterCounter.beVisibleIf(config.showCharacterCounter)
                }
            }

            markThreadMessagesRead(threadId)
        }

        val bottomBarColor = getBottomBarColor()
        binding.shortCodeHolder.root.setBackgroundColor(bottomBarColor)

    }

    override fun onPause() {
        super.onPause()
        saveDraftMessage()
        bus?.post(Events.RefreshConversations())
        isActivityVisible = false
    }

    override fun onStop() {
        super.onStop()
        saveDraftMessage()
    }

    override fun onDestroy() {
        super.onDestroy()
        bus?.unregister(this)
    }

    private fun saveDraftMessage() {
        val draftMessage = binding.messageHolder.threadTypeMessage.value
        ensureBackgroundThread {
            if (draftMessage.isNotEmpty() && getAttachmentSelections().isEmpty()) {
                saveSmsDraft(draftMessage, threadId)
            } else {
                deleteSmsDraft(threadId)
            }
        }
    }

    private fun refreshMenuItems() {
        val firstPhoneNumber = participants.firstOrNull()?.phoneNumbers?.firstOrNull()?.value
        val archiveAvailable = config.isArchiveAvailable
        binding.threadToolbar.menu.apply {
            findItem(R.id.delete).isVisible = threadItems.isNotEmpty()
            findItem(R.id.restore).isVisible = threadItems.isNotEmpty() && isRecycleBin
            findItem(R.id.archive).isVisible =
                threadItems.isNotEmpty() && conversation?.isArchived == false && !isRecycleBin && archiveAvailable
            findItem(R.id.unarchive).isVisible =
                threadItems.isNotEmpty() && conversation?.isArchived == true && !isRecycleBin && archiveAvailable
            findItem(R.id.rename_conversation).isVisible = participants.size > 1 && conversation != null && !isRecycleBin
            findItem(R.id.conversation_details).isVisible = conversation != null && !isRecycleBin
            findItem(R.id.block_number).isVisible = !isRecycleBin
            findItem(R.id.dial_number).isVisible = participants.size == 1 && !isSpecialNumber() && !isRecycleBin
            findItem(R.id.manage_people).isVisible = !isSpecialNumber() && !isRecycleBin
            findItem(R.id.mark_as_unread).isVisible = threadItems.isNotEmpty() && !isRecycleBin

            findItem(R.id.add_number_to_contact).isVisible =
                participants.size == 1 && participants.first().name == firstPhoneNumber && firstPhoneNumber.any {
                    it.isDigit()
                } && !isRecycleBin
            val unblockText = if (participants.size == 1) com.goodwy.strings.R.string.unblock_number else com.goodwy.strings.R.string.unblock_numbers
            val blockText = if (participants.size == 1) com.goodwy.commons.R.string.block_number else com.goodwy.commons.R.string.block_numbers
            findItem(R.id.block_number).title = if (isBlockNumbers()) getString(unblockText) else getString(blockText)

            findItem(R.id.toggle_send_mode).isVisible = !isRecycleBin
            val currentSendMode = sendModeRepository.getSendMode(threadId)
            val sendModeText = if (currentSendMode == SendMode.SEND) R.string.switch_to_draft_mode else R.string.switch_to_send_mode
            findItem(R.id.toggle_send_mode).title = getString(sendModeText)
        }
    }

    private fun setupOptionsMenu() {
        binding.threadToolbar.setOnMenuItemClickListener { menuItem ->
            if (participants.isEmpty()) {
                return@setOnMenuItemClickListener true
            }

            when (menuItem.itemId) {
                R.id.block_number -> blockNumber()
                R.id.delete -> askConfirmDelete()
                R.id.restore -> askConfirmRestoreAll()
                R.id.archive -> archiveConversation()
                R.id.unarchive -> unarchiveConversation()
                R.id.rename_conversation -> renameConversation()
                R.id.conversation_details -> launchConversationDetails(threadId)
                R.id.add_number_to_contact -> addNumberToContact()
                R.id.dial_number -> dialNumber()
                R.id.manage_people -> managePeople()
                R.id.mark_as_unread -> markAsUnread()
                R.id.toggle_send_mode -> toggleSendMode()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (resultCode != RESULT_OK) return
        val data = resultData?.data
        messageToResend = null

        if (requestCode == CAPTURE_PHOTO_INTENT && capturedImageUri != null) {
            addAttachment(capturedImageUri!!)
        } else if (data != null) {
            when (requestCode) {
                CAPTURE_VIDEO_INTENT,
                PICK_DOCUMENT_INTENT,
                CAPTURE_AUDIO_INTENT,
                PICK_PHOTO_INTENT,
                PICK_VIDEO_INTENT -> addAttachment(data)

                PICK_CONTACT_INTENT -> addContactAttachment(data)
                PICK_SAVE_FILE_INTENT -> saveAttachments(resultData)
                PICK_SAVE_DIR_INTENT -> saveAttachments(resultData)
            }
        }

        if (requestCode == REQUEST_CODE_SPEECH_INPUT) {
            if (resultData != null) {
                val res: ArrayList<String> =
                    resultData.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS) as ArrayList<String>

                val speechToText =  Objects.requireNonNull(res)[0]
                val draft = binding.messageHolder.threadTypeMessage.value
                val draftPlusSpeech =
                    if (draft.isNotEmpty()) {
                        if (draft.last().toString() != " ") "$draft $speechToText" else "$draft $speechToText"
                    } else speechToText
                if (draftPlusSpeech != "") {
                    ensureBackgroundThread {
                        saveSmsDraft(draftPlusSpeech, threadId)
                    }

                }
            }
        }
    }

    private fun setupCachedMessages(callback: () -> Unit) {
        ensureBackgroundThread {
            messages = try {
                if (isRecycleBin) {
                    messagesDB.getThreadMessagesFromRecycleBin(threadId)
                } else {
                    if (config.useRecycleBin) {
                        messagesDB.getNonRecycledThreadMessages(threadId)
                    } else {
                        messagesDB.getThreadMessages(threadId)
                    }
                }.toMutableList() as ArrayList<Message>
            } catch (_: Exception) {
                ArrayList()
            }
            clearExpiredScheduledMessages(threadId, messages)
            messages.removeAll { it.isScheduled && it.millis() < System.currentTimeMillis() }

            messages.sortBy { it.date }
            if (messages.size > MESSAGES_LIMIT) {
                messages = ArrayList(messages.takeLast(MESSAGES_LIMIT))
            }

            setupParticipants()
            setupAdapter()

            runOnUiThread {
                if (messages.isEmpty() && !isSpecialNumber()) {
                    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
                    binding.messageHolder.threadTypeMessage.requestFocus()
                }

                setupThreadTitle()
                setupSIMSelector()

                callback()
            }
        }
    }

    private fun setupThread(callback: () -> Unit) {
        if (conversation == null && isLaunchedFromShortcut) {
            if (isTaskRoot) {
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(this)
                }
            }
            finish()
            return
        }
        val privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        ensureBackgroundThread {
            privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)

            val cachedMessagesCode = messages.clone().hashCode()
            if (!isRecycleBin) {
                messages = getMessages(threadId)
                if (config.useRecycleBin) {
                    val recycledMessages = messagesDB.getThreadMessagesFromRecycleBin(threadId)
                    messages = messages.filterNotInByKey(recycledMessages) { it.getStableId() }
                }
            }

            val hasParticipantWithoutName = participants.any { contact ->
                contact.phoneNumbers.map { it.normalizedNumber }.contains(contact.name)
            }

            try {
                if (participants.isNotEmpty() && messages.hashCode() == cachedMessagesCode && !hasParticipantWithoutName) {
                    setupAdapter()
                    runOnUiThread { callback() }
                    return@ensureBackgroundThread
                }
            } catch (_: Exception) {
            }

            setupParticipants()

            if (privateContacts.isNotEmpty()) {
                val senderNumbersToReplace = HashMap<String, String>()
                participants.filter { it.doesHavePhoneNumber(it.name) }.forEach { participant ->
                    privateContacts.firstOrNull { it.doesHavePhoneNumber(participant.phoneNumbers.first().normalizedNumber) }
                        ?.apply {
                            senderNumbersToReplace[participant.phoneNumbers.first().normalizedNumber] = name
                            participant.name = name
                            participant.photoUri = photoUri
                        }
                }

                messages.forEach { message ->
                    if (senderNumbersToReplace.keys.contains(message.senderName)) {
                        message.senderName = senderNumbersToReplace[message.senderName]!!
                    }
                }
            }

            if (participants.isEmpty()) {
                val name = intent.getStringExtra(THREAD_TITLE) ?: ""
                val number = intent.getStringExtra(THREAD_NUMBER)
                if (number == null) {
                    toast(com.goodwy.commons.R.string.unknown_error_occurred)
                    finish()
                    return@ensureBackgroundThread
                }

                val phoneNumber = PhoneNumber(number, 0, "", number)
                val contact = SimpleContact(
                    rawId = 0,
                    contactId = 0,
                    name = name,
                    photoUri = "",
                    phoneNumbers = arrayListOf(phoneNumber),
                    birthdays = ArrayList(),
                    anniversaries = ArrayList()
                )
                participants.add(contact)
            }

            if (!isRecycleBin) {
                messages.chunked(30).forEach { currentMessages ->
                    messagesDB.insertMessages(*currentMessages.toTypedArray())
                }
            }

            setupAdapter()
            runOnUiThread {
                setupThreadTitle()
                setupSIMSelector()
                callback()
            }
        }
        updateContactImage()
    }

    private fun getOrCreateThreadAdapter(): ThreadAdapter {
        if (isDynamicTheme() && !isSystemInDarkMode()) {
            binding.threadHolder.setBackgroundColor(getSurfaceColor())
        }
        var currAdapter = binding.threadMessagesList.adapter
        if (currAdapter == null) {
            currAdapter = ThreadAdapter(
                activity = this,
                recyclerView = binding.threadMessagesList,
                itemClick = { handleItemClick(it) },
                isRecycleBin = isRecycleBin,
                isGroupChat = participants.size > 1,
                deleteMessages = { messages, toRecycleBin, fromRecycleBin, isPopupMenu ->
                    deleteMessages(messages, toRecycleBin, fromRecycleBin, isPopupMenu)
                }
            )

            binding.threadMessagesList.adapter = currAdapter
        }
        return currAdapter as ThreadAdapter
    }

    private fun setupAdapter() {
        threadItems = getThreadItems()

        runOnUiThread {
            refreshMenuItems()
            getOrCreateThreadAdapter().apply {
                val layoutManager = binding.threadMessagesList.layoutManager as LinearLayoutManager
                val lastPosition = itemCount - 1
                val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
                val shouldScrollToBottom =
                    currentList.lastOrNull() != threadItems.lastOrNull() && lastPosition - lastVisiblePosition == 1
                updateMessages(threadItems, if (shouldScrollToBottom) lastPosition else -1)
            }
        }

        SimpleContactsHelper(this).getAvailableContacts(false) { contacts ->
            contacts.addAll(privateContacts)
            runOnUiThread {
                val adapter = AutoCompleteTextViewAdapter(this, contacts)
                binding.addContactOrNumber.setAdapter(adapter)
                binding.addContactOrNumber.imeOptions = EditorInfo.IME_ACTION_NEXT
                binding.addContactOrNumber.setOnItemClickListener { _, _, position, _ ->
                    val currContacts = (binding.addContactOrNumber.adapter as AutoCompleteTextViewAdapter).resultList
                    val selectedContact = currContacts[position]
                    maybeShowNumberPickerDialog(selectedContact.phoneNumbers) { phoneNumber ->
                        val contactWithSelectedNumber = selectedContact.copy(
                            phoneNumbers = arrayListOf(phoneNumber)
                        )
                        addSelectedContact(contactWithSelectedNumber)
                    }
                }

                binding.addContactOrNumber.onTextChangeListener {
                    binding.confirmInsertedNumber.beVisibleIf(it.length > 2)
                }
            }
        }

        runOnUiThread {
            binding.confirmInsertedNumber.setOnClickListener {
                val number = binding.addContactOrNumber.value
                val phoneNumber = PhoneNumber(number, 0, "", number)
                val contact = SimpleContact(
                    rawId = number.hashCode(),
                    contactId = number.hashCode(),
                    name = number,
                    photoUri = "",
                    phoneNumbers = arrayListOf(phoneNumber),
                    birthdays = ArrayList(),
                    anniversaries = ArrayList()
                )
                addSelectedContact(contact)
            }
        }

        binding.confirmInsertedNumber.setColorFilter(getProperTextColor())
        binding.addContactOrNumber.setBackgroundResource(com.goodwy.commons.R.drawable.search_bg)
        val useSurfaceColor = isDynamicTheme() && !isSystemInDarkMode()
        val surfaceColor = if (useSurfaceColor) getProperBackgroundColor() else getSurfaceColor()
        binding.addContactOrNumber.backgroundTintList = ColorStateList.valueOf(surfaceColor)
    }

    private fun scrollToBottom() {
        val position = getOrCreateThreadAdapter().currentList.lastIndex
        if (position >= 0) {
            binding.threadMessagesList.smoothScrollToPosition(position)
        }
    }

    private fun setupScrollListener() {
        binding.threadMessagesList.onScroll(
            onScrolled = { dx, dy ->
                tryLoadMoreMessages()
                val layoutManager = binding.threadMessagesList.layoutManager as LinearLayoutManager
                val lastVisibleItemPosition = layoutManager.findLastCompletelyVisibleItemPosition()
                val isCloseToBottom =
                    lastVisibleItemPosition >= getOrCreateThreadAdapter().itemCount - SCROLL_TO_BOTTOM_FAB_LIMIT
                val fab = binding.scrollToBottomFab
                if (isCloseToBottom) fab.hide() else fab.show()
            },
            onScrollStateChanged = { newState ->
                if (newState == RecyclerView.SCROLL_STATE_IDLE) tryLoadMoreMessages()
            }
        )
    }

    private fun handleItemClick(any: Any) {
        when {
            any is Message && any.isScheduled -> showScheduledMessageInfo(any)
            any is ThreadError -> {
                binding.messageHolder.threadTypeMessage.setText(any.messageText)
                messageToResend = any.messageId
            }
        }
    }

    private fun deleteMessages(
        messagesToRemove: List<Message>,
        toRecycleBin: Boolean,
        fromRecycleBin: Boolean,
        isPopupMenu: Boolean = false,
    ) {
        val deletePosition = threadItems.indexOf(messagesToRemove.first())
        messages.removeAll(messagesToRemove.toSet())
        threadItems = getThreadItems()

        runOnUiThread {
            if (messages.isEmpty() && !isPopupMenu) {
                finish()
            } else {
                getOrCreateThreadAdapter().apply {
                    updateMessages(threadItems, scrollPosition = deletePosition)
                    finishActMode()
                }
            }
        }

        messagesToRemove.forEach { message ->
            val messageId = message.id
            if (message.isScheduled) {
                deleteScheduledMessage(messageId)
                cancelScheduleSendPendingIntent(messageId)
            } else {
                if (toRecycleBin) {
                    moveMessageToRecycleBin(messageId)
                } else if (fromRecycleBin) {
                    restoreMessageFromRecycleBin(messageId)
                } else {
                    deleteMessage(messageId, message.isMMS)
                }
            }
        }
        updateLastConversationMessage(threadId)

        if (messages.isNotEmpty() && messages.all { it.isScheduled }) {
            val scheduledMessage = messages.last()
            val fakeThreadId = generateRandomId()
            createTemporaryThread(scheduledMessage, fakeThreadId, conversation)
            updateScheduledMessagesThreadId(messages, fakeThreadId)
            threadId = fakeThreadId
        }
    }

    private fun jumpToMessage(messageId: Long) {
        if (messages.any { it.id == messageId }) {
            val index = threadItems.indexOfFirst { (it as? Message)?.id == messageId }
            if (index != -1) binding.threadMessagesList.smoothScrollToPosition(index)
            return
        }

        ensureBackgroundThread {
            if (loadingOlderMessages) return@ensureBackgroundThread
            loadingOlderMessages = true
            isJumpingToMessage = true

            var cutoff = messages.firstOrNull()?.date ?: Int.MAX_VALUE
            var found = false
            var loops = 0

            while (!found && !allMessagesFetched) {
                if (fetchOlderMessages(cutoff).isEmpty() || loops >= 1000) break
                cutoff = messages.first().date
                found = messages.any { it.id == messageId }
                loops++
            }

            threadItems = getThreadItems()
            runOnUiThread {
                loadingOlderMessages = false
                val index = threadItems.indexOfFirst { (it as? Message)?.id == messageId }
                getOrCreateThreadAdapter().updateMessages(
                    newMessages = threadItems, scrollPosition = index, smoothScroll = true
                )
                isJumpingToMessage = false
            }
        }
    }

    private fun tryLoadMoreMessages() {
        if (isJumpingToMessage) return
        val layoutManager = binding.threadMessagesList.layoutManager as LinearLayoutManager
        if (layoutManager.findFirstVisibleItemPosition() <= PREFETCH_THRESHOLD) {
            loadMoreMessages()
        }
    }

    private fun loadMoreMessages() {
        if (messages.isEmpty() || allMessagesFetched || loadingOlderMessages) return
        loadingOlderMessages = true
        val cutoff = messages.first().date
        ensureBackgroundThread {
            fetchOlderMessages(cutoff)
            threadItems = getThreadItems()
            runOnUiThread {
                loadingOlderMessages = false
                getOrCreateThreadAdapter().updateMessages(threadItems)
                getOrCreateThreadAdapter().updateTitle()
            }
        }
    }

    private fun fetchOlderMessages(cutoff: Int): List<Message> {
        var older = getMessages(threadId, cutoff)
            .filterNotInByKey(messages) { it.getStableId() }
        if (config.useRecycleBin && !isRecycleBin) {
            val recycledMessages = messagesDB.getThreadMessagesFromRecycleBin(threadId)
            older = older.filterNotInByKey(recycledMessages) { it.getStableId() }
        }

        if (older.isEmpty()) {
            allMessagesFetched = true
            return older
        }

        messages.addAll(0, older)
        return older
    }

    private fun loadConversation() {
        handlePermission(PERMISSION_READ_PHONE_STATE) { granted ->
            if (granted) {
                setupButtons()
                setupConversation()
                setupCachedMessages {
                    setupThread {
                        val searchedMessageId = intent.getLongExtra(SEARCHED_MESSAGE_ID, -1L)
                        intent.removeExtra(SEARCHED_MESSAGE_ID)
                        if (searchedMessageId != -1L) {
                            jumpToMessage(searchedMessageId)
                        }
                    }
                    setupScrollListener()
                }
            } else {
                finish()
            }
        }
    }

    private fun setupConversation() {
        ensureBackgroundThread {
            conversation = conversationsDB.getConversationWithThreadId(threadId)
        }
    }

    private fun setupButtons() = binding.apply {
        updateTextColors(threadHolder)
        val textColor = getProperTextColor()
        val properPrimaryColor = getProperPrimaryColor()
        val useSurfaceColor = isDynamicTheme() && !isSystemInDarkMode()
        val surfaceColor = if (useSurfaceColor) getProperBackgroundColor() else getSurfaceColor()

        binding.messageHolder.apply {

            threadSendMessage.applyColorFilter(properPrimaryColor.getContrastColor())

            confirmManageContacts.applyColorFilter(textColor)
            threadAddAttachment.applyColorFilter(textColor)
            threadAddAttachment.background.applyColorFilter(surfaceColor)
            threadTypeMessageHolder.background.applyColorFilter(surfaceColor)

            threadMessagesFastscroller.updateColors(getProperAccentColor())

            threadCharacterCounter.beVisibleIf(threadTypeMessage.value.isNotEmpty() && config.showCharacterCounter)
            threadCharacterCounter.backgroundTintList = getProperBackgroundColor().getColorStateList()

            threadTypeMessage.setTextSize(TypedValue.COMPLEX_UNIT_PX, getTextSizeMessage())

            if (isSpeechToTextAvailable) {
                threadSendMessageWrapper.setOnLongClickListener {

                    speechToText()
                    true
                }
            }

            threadSendMessage.backgroundTintList = properPrimaryColor.getColorStateList()
            threadSendMessageWrapper.isClickable = false
            threadTypeMessage.onTextChangeListener {
                messageToResend = null
                checkSendMessageAvailability()
                val messageString = if (config.useSimpleCharacters) {
                    it.normalizeString()
                } else {
                    it
                }
                val messageLength = SmsMessage.calculateLength(messageString, false)
                @SuppressLint("SetTextI18n")
                threadCharacterCounter.text = "${messageLength[2]}/${messageLength[0]}"
                threadCharacterCounter.beVisibleIf(threadTypeMessage.value.isNotEmpty() && config.showCharacterCounter)
            }

            if (config.sendOnEnter) {
                threadTypeMessage.inputType = EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES
                threadTypeMessage.imeOptions = EditorInfo.IME_ACTION_SEND
                threadTypeMessage.setOnEditorActionListener { _, action, _ ->
                    if (action == EditorInfo.IME_ACTION_SEND) {
                        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                        return@setOnEditorActionListener true
                    }
                    false
                }

                threadTypeMessage.setOnKeyListener { _, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) {
                        sendMessage()
                        return@setOnKeyListener true
                    }
                    false
                }
            }

            confirmManageContacts.setOnClickListener {
                hideKeyboard()
                threadAddContacts.beGone()

                val numbers = HashSet<String>()
                participants.forEach { contact ->
                    contact.phoneNumbers.forEach {
                        numbers.add(it.normalizedNumber)
                    }
                }

                val newThreadId = getThreadId(numbers)
                if (threadId != newThreadId) {
                    hideKeyboard()
                    Intent(this@ThreadActivity, ThreadActivity::class.java).apply {
                        putExtra(THREAD_ID, newThreadId)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        startActivity(this)
                    }
                }
            }

            threadTypeMessage.setText(intent.getStringExtra(THREAD_TEXT))
            threadAddAttachmentHolder.setOnClickListener {
                if (attachmentPickerHolder.isVisible()) {
                    isAttachmentPickerVisible = false
                    hideAttachmentPicker()

                } else {
                    isAttachmentPickerVisible = true
                    showAttachmentPicker()

                }
                binding.messageHolder.threadTypeMessage.requestApplyInsets()
            }

            if (intent.extras?.containsKey(THREAD_ATTACHMENT_URI) == true) {
                val uri = intent.getStringExtra(THREAD_ATTACHMENT_URI)!!.toUri()
                addAttachment(uri)
            } else if (intent.extras?.containsKey(THREAD_ATTACHMENT_URIS) == true) {
                (intent.getSerializableExtra(THREAD_ATTACHMENT_URIS) as? ArrayList<Uri>)?.forEach {
                    addAttachment(it)
                }
            }
            scrollToBottomFab.setOnClickListener {
                scrollToBottom()
            }
            scrollToBottomFab.backgroundTintList = ColorStateList.valueOf(getBottomBarColor())
            scrollToBottomFab.applyColorFilter(textColor)
        }

        setupScheduleSendUi()
    }

    private fun askForExactAlarmPermissionIfNeeded(callback: () -> Unit = {}) {
        if (isSPlus()) {
            val alarmManager: AlarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            if (alarmManager.canScheduleExactAlarms()) {
                callback()
            } else {
                PermissionRequiredDialog(
                    activity = this,
                    textId = com.goodwy.commons.R.string.allow_alarm_scheduled_messages,
                    positiveActionCallback = {
                        openRequestExactAlarmSettings(BuildConfig.APPLICATION_ID)
                    },
                )
            }
        } else {
            callback()
        }
    }

    private fun setupParticipants() {
        if (participants.isEmpty()) {
            participants = if (messages.isEmpty()) {
                val intentNumbers = getPhoneNumbersFromIntent()
                val participants = getThreadParticipants(threadId, null)
                fixParticipantNumbers(participants, intentNumbers)
            } else {
                messages.first().participants
            }
            runOnUiThread {
                maybeDisableShortCodeReply()
            }
        }
    }

    private fun isSpecialNumber(): Boolean {
        val addresses = participants.getAddresses()
        return addresses.any { isShortCodeWithLetters(it) }
    }

    private fun maybeDisableShortCodeReply() {
        if (isSpecialNumber() && !isRecycleBin) {
            currentFocus?.clearFocus()
            hideKeyboard()
            binding.messageHolder.threadTypeMessage.text?.clear()
            binding.messageHolder.root.beGone()
            binding.shortCodeHolder.root.beVisible()

            val textColor = getProperTextColor()
            binding.shortCodeHolder.replyDisabledText.setTextColor(textColor)
            binding.shortCodeHolder.replyDisabledInfo.apply {
                applyColorFilter(textColor)
                setOnClickListener {
                    InvalidNumberDialog(
                        activity = this@ThreadActivity,
                        text = getString(R.string.invalid_short_code_desc)
                    )
                }
                tooltipText = getString(com.goodwy.commons.R.string.more_info)
            }
        }
    }

    private fun setupThreadTitle() = binding.apply {
        val textColor = getProperTextColor()
        val title = conversation?.title
        val threadTitle = if (!title.isNullOrEmpty()) title else participants.getThreadTitle()
        val threadSubtitle = participants.getThreadSubtitle()
        when (config.threadTopStyle) {
            THREAD_TOP_COMPACT -> topDetailsCompact.apply {
                senderPhoto.beVisibleIf(config.showContactThumbnails)
                if (threadTitle.isNotEmpty()) {
                    senderName.text = threadTitle
                    senderName.setTextColor(textColor)
                }
                senderNumber.beGoneIf(threadTitle == threadSubtitle || participants.size > 1)
                senderNumber.text = threadSubtitle
                senderNumber.setTextColor(textColor)
                arrayOf(
                    senderPhoto,
                    senderName,
                    senderNumber
                ).forEach {
                    it.setOnClickListener {
                        if (conversation != null) launchConversationDetails(threadId)
                    }
                }
                senderName.setOnLongClickListener { copyToClipboard(senderName.value); true }
                senderNumber.setOnLongClickListener { copyToClipboard(senderNumber.value); true }
            }
            THREAD_TOP_LARGE -> topDetailsLarge.apply {
                topDetailsCompact.root.beGone()
                senderPhotoLarge.beVisibleIf(config.showContactThumbnails)
                if (threadTitle.isNotEmpty()) {
                    senderNameLarge.text = threadTitle
                    senderNameLarge.setTextColor(textColor)
                }
                senderNumberLarge.beGoneIf(threadTitle == threadSubtitle || participants.size > 1)
                senderNumberLarge.text = threadSubtitle
                senderNumberLarge.setTextColor(textColor)
                arrayOf(
                    senderPhotoLarge,
                    senderNameLarge,
                    senderNumberLarge
                ).forEach {
                    it.setOnClickListener {
                        if (conversation != null) launchConversationDetails(threadId)
                    }
                }
                senderNameLarge.setOnLongClickListener { copyToClipboard(senderNameLarge.value); true }
                senderNumberLarge.setOnLongClickListener { copyToClipboard(senderNumberLarge.value); true }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupSIMSelector() {
        val textColor = getProperTextColor()
        val availableSIMs = subscriptionManagerCompat().activeSubscriptionInfoList ?: return
        if (availableSIMs.size > 1) {
            availableSIMCards.clear()
            availableSIMs.forEachIndexed { index, subscriptionInfo ->
                var label = subscriptionInfo.displayName?.toString() ?: ""
                if (subscriptionInfo.number?.isNotEmpty() == true) {
                    label += " (${subscriptionInfo.number})"
                }
                val SIMCard = SIMCard(index + 1, subscriptionInfo.subscriptionId, label)
                availableSIMCards.add(SIMCard)
            }

            val numbers = ArrayList<String>()
            participants.forEach { contact ->
                contact.phoneNumbers.forEach {
                    numbers.add(it.normalizedNumber)
                }
            }

            if (numbers.isEmpty()) {
                return
            }

            currentSIMCardIndex = getProperSimIndex(availableSIMs, numbers)
            binding.messageHolder.threadSelectSimIcon.background.applyColorFilter(
                resources.getColor(com.goodwy.commons.R.color.activated_item_foreground, theme)
            )
            binding.messageHolder.threadSelectSimIcon.applyColorFilter(getProperTextColor())
            binding.messageHolder.threadSelectSimIconHolder.beVisibleIf(!config.showSimSelectionDialog)
            binding.messageHolder.threadSelectSimNumber.beVisible()
            val simLabel =
                if (availableSIMCards.size > currentSIMCardIndex) availableSIMCards[currentSIMCardIndex].label else "SIM Card"
            binding.messageHolder.threadSelectSimIconHolder.contentDescription = simLabel

            if (availableSIMCards.isNotEmpty()) {
                binding.messageHolder.threadSelectSimIconHolder.setOnClickListener {
                    currentSIMCardIndex = (currentSIMCardIndex + 1) % availableSIMCards.size
                    val currentSIMCard = availableSIMCards[currentSIMCardIndex]
                    @SuppressLint("SetTextI18n")
                    binding.messageHolder.threadSelectSimNumber.text = currentSIMCard.id.toString()
                    val simColor = if (!config.colorSimIcons) textColor
                    else {
                        val simId = currentSIMCard.id
                        if (simId in 1..4) config.simIconsColors[simId] else config.simIconsColors[0]
                    }
                    binding.messageHolder.threadSelectSimIcon.applyColorFilter(simColor)
                    val currentSubscriptionId = currentSIMCard.subscriptionId
                    numbers.forEach {
                        config.saveUseSIMIdAtNumber(it, currentSubscriptionId)
                    }
                    it.performHapticFeedback()
                    binding.messageHolder.threadSelectSimIconHolder.contentDescription = currentSIMCard.label
                    toast(currentSIMCard.label)
                }
            }

            binding.messageHolder.threadSelectSimNumber.setTextColor(textColor.getContrastColor())
            try {
                @SuppressLint("SetTextI18n")
                binding.messageHolder.threadSelectSimNumber.text = (availableSIMCards[currentSIMCardIndex].id).toString()
                val simColor =
                    if (!config.colorSimIcons) textColor
                    else {
                        val simId = availableSIMCards[currentSIMCardIndex].id
                        if (simId in 1..4) config.simIconsColors[simId] else config.simIconsColors[0]
                    }
                binding.messageHolder.threadSelectSimIcon.applyColorFilter(simColor)
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun getProperSimIndex(
        availableSIMs: MutableList<SubscriptionInfo>,
        numbers: List<String>,
    ): Int {
        val userPreferredSimId = config.getUseSIMIdAtNumber(numbers.first())
        val userPreferredSimIdx =
            availableSIMs.indexOfFirstOrNull { it.subscriptionId == userPreferredSimId }

        val lastMessage = messages.lastOrNull()
        val senderPreferredSimIdx = if (lastMessage?.isReceivedMessage() == true) {
            availableSIMs.indexOfFirstOrNull { it.subscriptionId == lastMessage.subscriptionId }
        } else {
            null
        }

        val defaultSmsSubscriptionId = SmsManager.getDefaultSmsSubscriptionId()
        val systemPreferredSimIdx = if (defaultSmsSubscriptionId >= 0) {
            availableSIMs.indexOfFirstOrNull { it.subscriptionId == defaultSmsSubscriptionId }
        } else {
            null
        }

        return userPreferredSimIdx ?: senderPreferredSimIdx ?: systemPreferredSimIdx ?: 0
    }

    private fun isBlockNumbers(): Boolean {
        return participants.getAddresses().any { isNumberBlocked(it, getBlockedNumbers()) }
    }

    private fun blockNumber() {
        val numbers = participants.getAddresses()
        val numbersString = TextUtils.join(", ", numbers)
        val isBlockNumbers = isBlockNumbers()
        val baseString =
            if (isBlockNumbers) com.goodwy.strings.R.string.unblock_confirmation
            else com.goodwy.commons.R.string.block_confirmation
        val question = String.format(resources.getString(baseString), numbersString)

        ConfirmationDialog(this, question) {
            ensureBackgroundThread {
                numbers.forEach {
                    if (isBlockNumbers) {
                        deleteBlockedNumber(it)
                        runOnUiThread { refreshMenuItems()}
                    } else {
                        addBlockedNumber(it)
                        runOnUiThread { refreshMenuItems()}
                    }
                }
                refreshConversations()

            }
        }
    }

    private fun askConfirmDelete() {
        val confirmationMessage = R.string.delete_whole_conversation_confirmation
        ConfirmationDialog(this, getString(confirmationMessage)) {
            ensureBackgroundThread {
                if (isRecycleBin) {
                    emptyMessagesRecycleBinForConversation(threadId)
                } else {
                    deleteConversation(threadId)
                }
                runOnUiThread {
                    refreshConversations()
                    finish()
                }
            }
        }
    }

    private fun askConfirmRestoreAll() {
        ConfirmationDialog(this, getString(R.string.restore_confirmation)) {
            ensureBackgroundThread {
                restoreAllMessagesFromRecycleBinForConversation(threadId)
                runOnUiThread {
                    refreshConversations()
                    finish()
                }
            }
        }
    }

    private fun archiveConversation() {
        ensureBackgroundThread {
            updateConversationArchivedStatus(threadId, true)
            runOnUiThread {
                refreshConversations()
                finish()
            }
        }
    }

    private fun unarchiveConversation() {
        ensureBackgroundThread {
            updateConversationArchivedStatus(threadId, false)
            runOnUiThread {
                refreshConversations()
                finish()
            }
        }
    }

    private fun dialNumber() {
        val phoneNumber = participants.first().phoneNumbers.first().normalizedNumber
        dialNumber(phoneNumber)
    }

    private fun managePeople() {
        if (binding.threadAddContacts.isVisible()) {
            hideKeyboard()
            binding.threadAddContacts.beGone()
        } else {
            showSelectedContacts()
            binding.threadAddContacts.beVisible()
            binding.addContactOrNumber.requestFocus()
            showKeyboard(binding.addContactOrNumber)
        }
    }

    private fun showSelectedContacts() {
        val properPrimaryColor = getProperPrimaryColor()

        val views = ArrayList<View>()
        val firstRawId = participants.first().rawId
        participants.forEach { contact ->
            ItemSelectedContactBinding.inflate(layoutInflater).apply {
                val selectedContactBg =
                    ResourcesCompat.getDrawable(resources, R.drawable.item_selected_contact_background, theme)
                (selectedContactBg as LayerDrawable).findDrawableByLayerId(R.id.selected_contact_bg)
                    .applyColorFilter(properPrimaryColor)
                selectedContactHolder.background = selectedContactBg

                selectedContactName.text = contact.name
                selectedContactName.setTextColor(properPrimaryColor.getContrastColor())
                selectedContactRemove.applyColorFilter(properPrimaryColor.getContrastColor())
                selectedContactRemove.beGoneIf(contact.rawId == firstRawId)

                selectedContactRemove.setOnClickListener {
                    if (contact.rawId != firstRawId) {
                        removeSelectedContact(contact.rawId)
                    }
                }
                views.add(root)
            }
        }
        showSelectedContact(views)
    }

    private fun addSelectedContact(contact: SimpleContact) {
        binding.addContactOrNumber.setText("")
        if (participants.map { it.rawId }.contains(contact.rawId)) {
            return
        }

        participants.add(contact)
        showSelectedContacts()

    }

    private fun markAsUnread() {
        ensureBackgroundThread {
            conversationsDB.markUnread(threadId)
            markThreadMessagesUnread(threadId)
            runOnUiThread {
                finish()
                bus?.post(Events.RefreshConversations())
            }
        }
    }

    private fun toggleSendMode() {
        val newMode = sendModeRepository.toggleSendMode(threadId)
        val messageRes = if (newMode == SendMode.SEND) R.string.send_mode_enabled else R.string.draft_mode_enabled
        toast(messageRes)
        refreshMenuItems()
        bus?.post(Events.RefreshConversations())
    }

    private fun addNumberToContact() {
        val phoneNumber =
            participants.firstOrNull()?.phoneNumbers?.firstOrNull()?.normalizedNumber ?: return
        Intent().apply {
            action = Intent.ACTION_INSERT_OR_EDIT
            type = "vnd.android.cursor.item/contact"
            putExtra(KEY_PHONE, phoneNumber)
            launchActivityIntent(this)
        }
    }

    @SuppressLint("MissingPermission")
    private fun renameConversation() {
        RenameConversationDialog(this, conversation!!) { title ->
            ensureBackgroundThread {
                conversation = renameConversation(conversation!!, newTitle = title)
                runOnUiThread {
                    setupThreadTitle()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun getThreadItems(): ArrayList<ThreadItem> {
        val items = ArrayList<ThreadItem>()
        if (isFinishing) {
            return items
        }

        messages.sortBy { it.date }

        val subscriptionIdToSimId = HashMap<Int, String>()
        subscriptionIdToSimId[-1] = "?"
        subscriptionManagerCompat().activeSubscriptionInfoList?.forEachIndexed { index, subscriptionInfo ->
            subscriptionIdToSimId[subscriptionInfo.subscriptionId] = "${index + 1}"
        }

        var prevDateTime = 0
        var prevSIMId = -2
        var hadUnreadItems = false
        val cnt = messages.size
        for (i in 0 until cnt) {
            val message = messages.getOrNull(i) ?: continue

            val isSentFromDifferentKnownSIM =
                prevSIMId != -1 && message.subscriptionId != -1 && prevSIMId != message.subscriptionId
            if (message.date - prevDateTime > MIN_DATE_TIME_DIFF_SECS || isSentFromDifferentKnownSIM) {
                val simCardID = subscriptionIdToSimId[message.subscriptionId] ?: "?"
                items.add(ThreadDateTime(message.date, simCardID))
                prevDateTime = message.date
            }
            items.add(message)

            if (message.type == Telephony.Sms.MESSAGE_TYPE_FAILED) {
                items.add(ThreadError(message.id, message.body))
            }

            if (message.type == Telephony.Sms.MESSAGE_TYPE_OUTBOX) {
                items.add(ThreadSending(message.id))
            }

            if (!message.read) {
                hadUnreadItems = true
                markMessageRead(message.id, message.isMMS)
                conversationsDB.markRead(threadId)
            }

            if (i == cnt - 1 && (message.type == Telephony.Sms.MESSAGE_TYPE_SENT)) {
                items.add(
                    ThreadSent(
                        messageId = message.id,
                        delivered = message.status == Telephony.Sms.STATUS_COMPLETE
                    )
                )
            }
            prevSIMId = message.subscriptionId
        }

        if (hadUnreadItems) {
            bus?.post(Events.RefreshConversations())
        }

        return items
    }

    private fun launchActivityForResult(
        intent: Intent,
        requestCode: Int,
        @StringRes error: Int = com.goodwy.commons.R.string.no_app_found,
    ) {
        hideKeyboard()
        try {
            startActivityForResult(intent, requestCode)
        } catch (_: ActivityNotFoundException) {
            showErrorToast(getString(error))
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }

    private fun getAttachmentsDir(): File {
        return File(cacheDir, "attachments").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    private fun launchCapturePhotoIntent() {
        val imageFile = File.createTempFile("attachment_", ".jpg", getAttachmentsDir())
        capturedImageUri = getMyFileUri(imageFile)
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, capturedImageUri)
        }
        launchActivityForResult(intent, CAPTURE_PHOTO_INTENT)
    }

    private fun launchCaptureVideoIntent() {
        val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
        launchActivityForResult(intent, CAPTURE_VIDEO_INTENT)
    }

    private fun launchCaptureAudioIntent() {
        val intent = Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION)
        launchActivityForResult(intent, CAPTURE_AUDIO_INTENT)
    }

    private fun launchGetContentIntent(mimeTypes: Array<String>, requestCode: Int) {
        Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "**"), PICK_DOCUMENT_INTENT)
        }
        pickContact.setOnClickListener {
            launchPickContactIntent()
        }
        scheduleMessage.setOnClickListener {
            if (isScheduledMessage) {
                launchScheduleSendDialog(scheduledDateTime)
            } else {
                launchScheduleSendDialog()
            }
        }
    }

    private fun showAttachmentPicker() {

        binding.messageHolder.attachmentPickerHolder.showWithAnimation()
        animateAttachmentButton(rotation = -135f)
    }

    private fun maybeSetupRecycleBinView() {
        if (isRecycleBin) {
            binding.messageHolder.root.beGone()
        }
    }

    private fun hideAttachmentPicker() {

        binding.messageHolder.attachmentPickerHolder.beGone()

        animateAttachmentButton(rotation = 0f)
    }

    private fun animateAttachmentButton(rotation: Float) {
        binding.messageHolder.threadAddAttachment.animate()
            .rotation(rotation)
            .setDuration(500L)
            .setInterpolator(OvershootInterpolator())
            .start()
    }

    private fun getBottomBarColor() = if (isDynamicTheme()) {
        getColoredMaterialStatusBarColor()
    } else {
        getColoredMaterialStatusBarColor()
    }

    fun setupMessagingEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(
            binding.messageHolder.threadTypeMessage
        ) { view, insets ->
            val type = WindowInsetsCompat.Type.ime()
            val isKeyboardVisible = insets.isVisible(type)
            if (isKeyboardVisible) {
                val keyboardHeight = insets.getInsets(type).bottom
                val bottomBarHeight = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom

                config.keyboardHeight = if (keyboardHeight > 150) {
                    keyboardHeight - bottomBarHeight
                } else {
                    getDefaultKeyboardHeight()
                }
                hideAttachmentPicker()
            } else if (isAttachmentPickerVisible) {
                showAttachmentPicker()
            }

            insets
        }
    }

    companion object {
        private const val TYPE_EDIT = 14
        private const val TYPE_SEND = 15
        private const val TYPE_DELETE = 16
        private const val MIN_DATE_TIME_DIFF_SECS = 300
        private const val SCROLL_TO_BOTTOM_FAB_LIMIT = 20
        private const val PREFETCH_THRESHOLD = 45
    }

    private fun updateContactImage() {
        val senderPhoto = when (config.threadTopStyle) {
            THREAD_TOP_COMPACT -> binding.topDetailsCompact.senderPhoto
            THREAD_TOP_LARGE -> binding.senderPhotoLarge
            else -> binding.topDetailsCompact.senderPhoto
        }

        val title = conversation?.title
        var threadTitle = if (!title.isNullOrEmpty()) {
            title
        } else {
            participants.getThreadTitle()
        }
        if (threadTitle.isEmpty()) threadTitle = intent.getStringExtra(THREAD_TITLE) ?: ""

        if (conversation != null && (!isDestroyed || !isFinishing)) {
            if ((threadTitle == conversation!!.phoneNumber || conversation!!.isCompany) && conversation!!.photoUri == "") {
                val drawable =
                    if (conversation!!.isCompany) SimpleContactsHelper(this@ThreadActivity).getColoredCompanyIcon(conversation!!.title)
                    else SimpleContactsHelper(this@ThreadActivity).getColoredContactIcon(conversation!!.title)
                senderPhoto.setImageDrawable(drawable)
            } else {
                val placeholder = if (participants.size > 1) {
                    SimpleContactsHelper(this).getColoredGroupIcon(threadTitle)
                } else {
                    null
                }

                SimpleContactsHelper(this).loadContactImage(conversation!!.photoUri, senderPhoto, threadTitle, placeholder)
            }
        } else {
            if (!isDestroyed || !isFinishing) {
                val placeholder = if (participants.size > 1) {
                    SimpleContactsHelper(this).getColoredGroupIcon(threadTitle)
                } else {
                    null
                }

                val number = intent.getStringExtra(THREAD_NUMBER)
                var namePhoto: NamePhoto? = null
                if (number != null) {
                    namePhoto = getNameAndPhotoFromPhoneNumber(number)
                }
                var threadUri = intent.getStringExtra(THREAD_URI) ?: ""
                if (threadUri == "" && namePhoto != null) {
                    threadUri = namePhoto.photoUri ?: ""
                }
                if (threadTitle.isEmpty() && namePhoto != null) threadTitle = namePhoto.name
                SimpleContactsHelper(this).loadContactImage(threadUri, senderPhoto, threadTitle, placeholder)
            }
        }
    }
}
